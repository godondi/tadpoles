package com.neueda.leap.trading;

import static com.neueda.leap.trading.TradingData.*;
import static com.neueda.leap.trading.TradingRules.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.mapper.TradingMapper;
import com.neueda.leap.trading.TradingModels.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TradingTransactions {
    private final TradingMapper db;
    private final ObjectMapper json;
    private final Clock clock;
    private final BookingProbe probe;

    public TradingTransactions(
            TradingMapper db, ObjectMapper json, Clock clock, ObjectProvider<BookingProbe> probe) {
        this.db = db;
        this.json = json;
        this.clock = clock;
        this.probe = probe.getIfAvailable(() -> write -> {});
    }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot retain trading evidence", e);
        }
    }

    public Policy policy(Map<String, Object> order) {
        try {
            return json.readValue(string(order, "policy_snapshot"), Policy.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid retained policy", e);
        }
    }

    public Evaluation resources(int client, Command command, Policy policy, Evaluation e) {
        if (!e.eligible()) return e;
        if (!db.onboarded(client) || !db.accountEnabled(client))
            return reject("ACCOUNT_UNAVAILABLE", "Complete onboarding before trading");
        if (!db.hasBank(client))
            return reject("BANK_REQUIRED", "Add a saved bank account before buying or selling");
        Map<String, Object> cash = db.cash(client, policy.currency());
        if (cash == null || !Boolean.TRUE.equals(cash.get("available_for_trading")))
            return reject("CURRENCY_UNAVAILABLE", "No account exists for " + policy.currency());
        if ("BUY".equals(command.side())
                && amount(cash, "posted").subtract(amount(cash, "reserved")).compareTo(e.gross())
                        < 0)
            return reject(
                    "INSUFFICIENT_CASH", "Not enough available " + policy.currency() + " cash");
        var position = db.position(client, command.instrumentId());
        if (position != null && !Boolean.TRUE.equals(position.get("available_for_trading")))
            return reject(
                    "POSITION_REVIEW", "This historical position requires review before trading");
        if ("SELL".equals(command.side())
                && amount(position, "posted")
                                .subtract(amount(position, "reserved"))
                                .compareTo(validate(command))
                        < 0)
            return reject("INSUFFICIENT_UNITS", "Not enough available units to sell");
        return e;
    }

    @Transactional
    public Map<String, Object> admit(
            int client,
            int actor,
            String key,
            String hash,
            Command command,
            Policy fetchedPolicy,
            Quote quote) {
        db.lockClient(client);
        var prior = db.byKey(client, key);
        if (prior != null) {
            checkHash(prior, hash);
            return prior;
        }
        Instant now = clock.instant();
        Policy current = db.policy(command.instrumentId());
        Evaluation e = evaluate(command, current, quote, now);
        // A policy change during the unlocked quote fetch requires a fresh submission attempt with
        // this same key.
        if (current != null && !Objects.equals(current, fetchedPolicy))
            e = reject("POLICY_CHANGED", "Trading policy changed; review a new estimate");
        e = resources(client, command, current, e);
        String status = e.eligible() ? "ACCEPTED" : "REJECTED";
        Map<String, Object> args =
                map(
                        "client",
                        client,
                        "instrument",
                        command.instrumentId(),
                        "actor",
                        actor,
                        "side",
                        command.side(),
                        "quantity",
                        validate(command),
                        "now",
                        now,
                        "status",
                        status,
                        "accepted",
                        e.eligible() ? now : null,
                        "key",
                        key,
                        "hash",
                        hash,
                        "currency",
                        current == null ? null : current.currency(),
                        "policy",
                        encode(current),
                        "stage",
                        e.eligible() ? null : "ADMISSION",
                        "code",
                        e.eligible() ? null : e.code(),
                        "reason",
                        e.reason());
        int id = db.insertOrder(args);
        evidence(id, "ADMISSION", current, quote, e, now);
        if (e.eligible()) {
            BigDecimal amount = "BUY".equals(command.side()) ? e.gross() : validate(command);
            if ("BUY".equals(command.side()))
                db.reserveCash(client, current.currency(), amount, now);
            else db.reserveUnits(client, command.instrumentId(), amount, now);
            db.reservation(id, "BUY".equals(command.side()) ? "CASH" : "UNITS", amount, now);
            db.job(id, now, now.plusSeconds(current.retrySeconds()));
        }
        event(db.order(client, id), null, status, e.reason(), "user:" + actor, quote, null, now);
        return db.order(client, id);
    }

    public void checkHash(Map<String, Object> prior, String hash) {
        if (!Objects.equals(string(prior, "request_hash"), hash))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key already belongs to a different instruction");
    }

    public record Claim(
            int orderId, int clientId, UUID token, int attempt, Policy policy, Command command) {}

    @Transactional
    public Claim claim(int id) {
        var initial = db.workerOrder(id);
        if (initial == null) return null;
        int client = integer(initial, "client_id");
        db.lockClient(client);
        var job = db.lockJob(id);
        Instant now = clock.instant();
        if (Boolean.TRUE.equals(job.get("completed"))
                || instant(job, "next_attempt").isAfter(now)
                || (instant(job, "lease_until") != null
                        && instant(job, "lease_until").isAfter(now))) return null;
        var order = db.order(client, id);
        Policy p = policy(order);
        UUID token = UUID.randomUUID();
        db.claim(id, token, now.plusSeconds(30));
        String previous = string(order, "status");
        db.state(id, "EXECUTING", null, null, "Processing order", now);
        event(
                db.order(client, id),
                previous,
                "EXECUTING",
                "Processing order",
                "execution-worker",
                null,
                null,
                now);
        int attempt = integer(job, "attempts") + 1;
        db.attempt(UUID.randomUUID(), id, attempt, "CLAIMED", "Claim " + token, null, now);
        return new Claim(
                id,
                client,
                token,
                attempt,
                p,
                new Command(
                        integer(order, "instrument_id"),
                        string(order, "trade_type"),
                        decimal(amount(order, "quantity")),
                        "MARKET"));
    }

    @Transactional
    public void book(Claim claim, Quote quote, String transportFailure) {
        db.lockClient(claim.clientId());
        var job = db.lockJob(claim.orderId());
        var order = db.order(claim.clientId(), claim.orderId());
        if (Boolean.TRUE.equals(job.get("completed")))
            return; // Already committed: no second posting.
        Instant now = clock.instant();
        if (!claim.token().equals(job.get("claim_token"))
                || instant(job, "lease_until") == null
                || !instant(job, "lease_until").isAfter(now)) return;
        Policy p = claim.policy();
        if (transportFailure != null
                && now.isBefore(instant(job, "deadline"))
                && claim.attempt() < p.maxAttempts()) {
            db.attempt(
                    UUID.randomUUID(),
                    claim.orderId(),
                    claim.attempt(),
                    "RETRY",
                    "Quote transport unavailable",
                    null,
                    now);
            db.retry(claim.orderId(), now.plusSeconds(Math.min(20, claim.attempt() * 2L)));
            return;
        }
        Evaluation e = evaluate(claim.command(), p, quote, now);
        Policy current = db.policy(p.instrumentId());
        if (!now.isBefore(instant(job, "deadline")))
            e = reject("DEADLINE_EXPIRED", "Execution deadline expired");
        else if (transportFailure != null)
            e = reject("QUOTE_UNAVAILABLE", "Execution quote unavailable after bounded retries");
        else if (current == null
                || !current.enabled()
                || !current.active()
                || !"PROVIDER_OPEN".equals(current.session()))
            e = reject("TRADING_HALTED", "Instrument trading is currently halted");
        else if (!db.onboarded(claim.clientId()) || !db.accountEnabled(claim.clientId()))
            e = reject("ACCOUNT_UNAVAILABLE", "Account is no longer eligible");
        if (e.eligible() && !db.hasBank(claim.clientId()))
            e = reject("BANK_REQUIRED", "A saved bank account is required");
        if (e.eligible()
                && ((!current.buyAllowed() && "BUY".equals(claim.command().side()))
                        || (!current.sellAllowed() && "SELL".equals(claim.command().side()))))
            e = reject("SIDE_HALTED", "This trading direction is halted");
        var reservation = db.reservationFor(claim.orderId());
        if (reservation == null || !"ACTIVE".equals(string(reservation, "state")))
            throw new IllegalStateException("Missing active reservation");
        BigDecimal reserved = amount(reservation, "amount");
        boolean buy = "BUY".equals(claim.command().side());
        BigDecimal quantity = validate(claim.command());
        var cash = db.cash(claim.clientId(), p.currency());
        var position = db.position(claim.clientId(), p.instrumentId());
        if (e.eligible()
                && (cash == null
                        || !Boolean.TRUE.equals(cash.get("available_for_trading"))
                        || position != null
                                && !Boolean.TRUE.equals(position.get("available_for_trading"))))
            e = reject("ACCOUNT_REVIEW", "Historical financial records require review");
        if (e.eligible()
                && buy
                && amount(cash, "posted")
                                .subtract(amount(cash, "reserved"))
                                .add(reserved)
                                .compareTo(e.gross())
                        < 0)
            e =
                    reject(
                            "INSUFFICIENT_EXECUTION_CASH",
                            "Execution price increased beyond your unreserved cash; reservation"
                                    + " released");
        if (e.eligible()
                && !buy
                && (reserved.compareTo(quantity) != 0
                        || amount(position, "posted").compareTo(quantity) < 0))
            e = reject("INSUFFICIENT_EXECUTION_UNITS", "Reserved units are unavailable");
        evidence(claim.orderId(), "EXECUTION", p, quote, e, now);
        probe.after("evidence");
        if (!e.eligible()) {
            if (buy)
                db.cashMovement(claim.clientId(), p.currency(), BigDecimal.ZERO, reserved, now);
            else
                db.positionMovement(
                        claim.clientId(), p.instrumentId(), BigDecimal.ZERO, reserved, now);
            probe.after("release");
            if (db.endReservation(claim.orderId(), "RELEASED", now) != 1)
                throw new IllegalStateException("Reservation already ended");
            db.state(claim.orderId(), "REJECTED", "EXECUTION", e.code(), e.reason(), now);
            event(
                    db.order(claim.clientId(), claim.orderId()),
                    "EXECUTING",
                    "REJECTED",
                    e.reason(),
                    "execution-worker",
                    quote,
                    null,
                    now);
        } else {
            BigDecimal cashDelta = buy ? e.gross().negate() : e.gross();
            BigDecimal unitDelta = buy ? quantity : quantity.negate();
            UUID fill = UUID.randomUUID(), group = UUID.randomUUID();
            db.ensurePosition(claim.clientId(), p.instrumentId());
            db.positionMovement(
                    claim.clientId(),
                    p.instrumentId(),
                    unitDelta,
                    buy ? BigDecimal.ZERO : reserved,
                    now);
            probe.after("position");
            db.cashMovement(
                    claim.clientId(),
                    p.currency(),
                    cashDelta,
                    buy ? reserved : BigDecimal.ZERO,
                    now);
            probe.after("cash");
            var f =
                    map(
                            "fill",
                            fill,
                            "id",
                            claim.orderId(),
                            "reference",
                            "INT-" + claim.orderId(),
                            "quote",
                            quote.quoteId(),
                            "quantity",
                            quantity,
                            "price",
                            e.price(),
                            "gross",
                            e.gross(),
                            "currency",
                            p.currency(),
                            "group",
                            group,
                            "executed",
                            now,
                            "now",
                            now,
                            "client",
                            claim.clientId(),
                            "instrument",
                            p.instrumentId(),
                            "cashDelta",
                            cashDelta,
                            "unitDelta",
                            unitDelta,
                            "posting",
                            UUID.randomUUID());
            db.fill(f);
            probe.after("fill");
            db.cashJournal(f);
            probe.after("cashJournal");
            f.put("posting", UUID.randomUUID());
            db.positionJournal(f);
            probe.after("positionJournal");
            if (db.endReservation(claim.orderId(), "CONSUMED", now) != 1)
                throw new IllegalStateException("Reservation already ended");
            db.state(claim.orderId(), "FILLED", null, null, "Internal fill booked", now);
            event(
                    db.order(claim.clientId(), claim.orderId()),
                    "EXECUTING",
                    "FILLED",
                    "Internal fill booked",
                    "execution-worker",
                    quote,
                    fill,
                    now);
        }
        db.attempt(
                UUID.randomUUID(),
                claim.orderId(),
                claim.attempt(),
                e.eligible() ? "FILLED" : "REJECTED",
                e.reason(),
                quote == null ? null : quote.quoteId(),
                now);
        db.complete(claim.orderId());
        probe.after("job");
    }

    private void evidence(
            int id, String stage, Policy policy, Quote quote, Evaluation e, Instant now) {
        if (quote != null) db.quote(quote.quoteId(), id, stage, encode(quote), now);
        db.decision(
                UUID.randomUUID(),
                id,
                stage,
                policy == null ? "UNCONFIGURED" : policy.version(),
                e.eligible(),
                e.code(),
                e.reason(),
                quote == null ? null : quote.quoteId(),
                now);
    }

    private void event(
            Map<String, Object> order,
            String previous,
            String state,
            String reason,
            String actor,
            Quote quote,
            UUID fill,
            Instant now) {
        int client = integer(order, "client_id"), id = integer(order, "trade_id");
        var event =
                map(
                        "event",
                        UUID.randomUUID(),
                        "id",
                        id,
                        "client",
                        client,
                        "cursor",
                        db.nextCursor(client),
                        "version",
                        number(order, "version"),
                        "previous",
                        previous,
                        "state",
                        state,
                        "reason",
                        reason,
                        "actor",
                        actor,
                        "quote",
                        quote == null ? null : quote.quoteId(),
                        "fill",
                        fill,
                        "now",
                        now);
        db.event(event);
        probe.after("event");
        event.put(
                "payload",
                encode(
                        map(
                                "orderId",
                                id,
                                "version",
                                number(order, "version"),
                                "status",
                                state,
                                "executionMode",
                                "INTERNAL")));
        db.outbox(event);
        probe.after("outbox");
    }
}
