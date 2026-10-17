package com.neueda.leap.service.impl;

import static com.neueda.leap.trading.TradingData.*;
import static com.neueda.leap.trading.TradingRules.decimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.BankingDtos.*;
import com.neueda.leap.mapper.*;
import com.neueda.leap.service.BankingService;
import com.neueda.leap.trading.BookingProbe;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankingServiceImpl implements BankingService {
    private final BankingMapper db;
    private final TradingMapper accounts;
    private final ObjectMapper json;
    private final Clock clock;
    private final String encryptionKey;
    private final BookingProbe probe;

    public BankingServiceImpl(
            BankingMapper db,
            TradingMapper accounts,
            ObjectMapper json,
            Clock clock,
            @Value("${banking.encryption-key:${BANKING_ENCRYPTION_KEY:}}") String encryptionKey,
            ObjectProvider<BookingProbe> probe) {
        this.db = db;
        this.accounts = accounts;
        this.json = json;
        this.clock = clock;
        this.encryptionKey = encryptionKey;
        this.probe = probe.getIfAvailable(() -> point -> {});
    }

    public List<BankReference> banks(int client) {
        return db.banks(client).stream().map(this::safeBank).toList();
    }

    @Transactional
    public BankReference save(int client, int actor, UUID id, BankRequest request) {
        if (request == null) throw new IllegalArgumentException("Bank reference is required");
        String name = request.nickname() == null ? "" : request.nickname().trim();
        if (!name.matches("[A-Za-z0-9 .'-]{2,50}") || !name.matches(".*[A-Za-z0-9].*"))
            throw new IllegalArgumentException(
                    "Nickname: 2–50 letters, digits, spaces, periods, apostrophes or hyphens;"
                            + " include a letter or digit");
        accounts.lockClient(client);
        var existing = id == null ? null : owned(client, id);
        if (existing != null
                && (request.version() == null || request.version() != number(existing, "version")))
            conflict("Bank reference changed; refresh before saving");
        UUID bankId = id == null ? UUID.randomUUID() : id;
        if (db.duplicate(client, name, bankId))
            conflict("Choose a different nickname for this account");
        boolean replace =
                existing == null
                        || request.routingNumber() != null
                        || request.accountNumber() != null;
        String encrypted, last;
        if (replace) {
            if (request.routingNumber() == null || !request.routingNumber().matches("[0-9]{9}"))
                throw new IllegalArgumentException("Routing number must contain exactly 9 digits");
            if (request.accountNumber() == null || !request.accountNumber().matches("[0-9]{4,17}"))
                throw new IllegalArgumentException(
                        "Account number must contain 4–17 digits; enter the full number when"
                                + " changing it");
            if (existing != null && db.pendingOrders(client))
                conflict("Wait for pending orders to finish before replacing bank details");
            encrypted =
                    encrypt(
                            client,
                            bankId,
                            request.routingNumber() + ":" + request.accountNumber());
            last = request.accountNumber().substring(request.accountNumber().length() - 4);
        } else {
            encrypted = string(existing, "encrypted_details");
            last = string(existing, "last_four");
        }
        boolean isDefault =
                !db.hasBank(client)
                        || Boolean.TRUE.equals(request.defaultAccount())
                        || existing != null && Boolean.TRUE.equals(existing.get("is_default"));
        Instant now = clock.instant();
        if (isDefault && (existing == null || !Boolean.TRUE.equals(existing.get("is_default"))))
            db.clearDefault(client, now);
        var row =
                map(
                        "id",
                        bankId,
                        "client",
                        client,
                        "name",
                        name,
                        "encrypted",
                        encrypted,
                        "last",
                        last,
                        "isDefault",
                        isDefault,
                        "now",
                        now);
        if (existing == null) db.add(row);
        else db.edit(row);
        var safe = safeBank(db.bank(client, bankId));
        db.audit(actor, bankId.toString(), existing == null ? "CREATE" : "UPDATE", encode(safe));
        return safe;
    }

    @Transactional
    public void remove(int client, int actor, UUID id, long version) {
        accounts.lockClient(client);
        var bank = owned(client, id);
        if (number(bank, "version") != version)
            conflict("Bank reference changed; refresh before removing it");
        if (db.pendingOrders(client))
            conflict("Wait for pending orders to finish before removing a bank account");
        db.deactivate(client, id, clock.instant());
        if (Boolean.TRUE.equals(bank.get("is_default"))) db.chooseDefault(client, clock.instant());
        db.audit(actor, id.toString(), "DEACTIVATE", encode(safeBank(bank)));
    }

    @Transactional
    public Movement move(int client, int actor, String key, MovementRequest request) {
        validKey(key);
        if (request == null
                || request.bankId() == null
                || !Set.of("DEPOSIT", "WITHDRAW")
                        .contains(Objects.toString(request.direction(), ""))
                || request.amount() == null
                || !request.amount().matches("[0-9]{1,5}(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException(
                    "Select a bank account and enter a positive USD amount with at most two"
                            + " decimals");
        BigDecimal amount = new BigDecimal(request.amount());
        if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("10000")) > 0)
            throw new IllegalArgumentException(
                    "Amount must be greater than zero and no more than USD 10,000");
        String hash = hash(request.bankId() + "|" + request.direction() + "|" + decimal(amount));
        accounts.lockClient(client);
        var prior = db.byKey(client, key);
        if (prior != null) {
            if (!hash.equals(string(prior, "request_hash")))
                conflict("Request reference belongs to a different movement");
            return safeMovement(prior);
        }
        var bank = owned(client, request.bankId());
        if (!accounts.accountEnabled(client) || !accounts.onboarded(client))
            conflict("Complete your profile before moving funds");
        var cash = accounts.cash(client, "USD");
        if (cash == null || !Boolean.TRUE.equals(cash.get("available_for_trading")))
            conflict("USD account is unavailable; contact support");
        Instant now = clock.instant();
        boolean deposit = "DEPOSIT".equals(request.direction());
        String reason = null;
        if (deposit) {
            Instant start =
                    now.atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant();
            if (db.dailyDeposits(client, start, start.plusSeconds(86400))
                            .add(amount)
                            .compareTo(new BigDecimal("10000"))
                    > 0) reason = "Internal test-funding limit is USD 10,000 per UTC day";
        } else if (amount(cash, "posted").subtract(amount(cash, "reserved")).compareTo(amount) < 0)
            reason =
                    "Not enough settled, available USD cash; pending orders reserve part of your"
                            + " balance";
        UUID id = UUID.randomUUID();
        var row =
                map(
                        "id",
                        id,
                        "client",
                        client,
                        "bank",
                        request.bankId(),
                        "key",
                        key,
                        "hash",
                        hash,
                        "direction",
                        request.direction(),
                        "amount",
                        amount,
                        "status",
                        reason == null ? "SETTLED" : "REJECTED",
                        "reason",
                        reason,
                        "snapshot",
                        bank.get("nickname") + " · •••• " + bank.get("last_four"),
                        "group",
                        UUID.randomUUID(),
                        "actor",
                        "user:" + actor,
                        "now",
                        now,
                        "posting",
                        UUID.randomUUID(),
                        "event",
                        UUID.randomUUID(),
                        "delta",
                        deposit ? amount : amount.negate());
        db.movement(row);
        probe.after("movement");
        if (reason == null) {
            // Synchronous internal settlement: hold and consume in the same transaction.
            if (!deposit) accounts.reserveCash(client, "USD", amount, now);
            probe.after("movementReservation");
            accounts.cashMovement(
                    client,
                    "USD",
                    deposit ? amount : amount.negate(),
                    deposit ? BigDecimal.ZERO : amount,
                    now);
            probe.after("movementCash");
            db.journal(row);
            probe.after("movementJournal");
        }
        row.put(
                "payload",
                encode(
                        Map.of(
                                "movementId",
                                id,
                                "status",
                                row.get("status"),
                                "currency",
                                "USD",
                                "amount",
                                decimal(amount),
                                "direction",
                                request.direction())));
        db.outbox(row);
        probe.after("movementOutbox");
        return safeMovement(db.byKey(client, key));
    }

    public Movement byRequest(int client, String key) {
        validKey(key);
        var row = db.byKey(client, key);
        if (row == null) notFound();
        return safeMovement(row);
    }

    public List<Movement> history(int client, int page, int pageSize) {
        if (page < 0 || page > 100000 || pageSize < 1 || pageSize > 100)
            throw new IllegalArgumentException("Invalid pagination");
        return db.history(client, pageSize, page * pageSize).stream()
                .map(this::safeMovement)
                .toList();
    }

    private Map<String, Object> owned(int client, UUID id) {
        var b = db.bank(client, id);
        if (b == null || !Boolean.TRUE.equals(b.get("active"))) notFound();
        return b;
    }

    private BankReference safeBank(Map<String, Object> b) {
        return new BankReference(
                (UUID) b.get("bank_id"),
                string(b, "nickname"),
                "•••• " + b.get("last_four"),
                Boolean.TRUE.equals(b.get("is_default")),
                number(b, "version"));
    }

    private Movement safeMovement(Map<String, Object> m) {
        return new Movement(
                (UUID) m.get("movement_id"),
                (UUID) m.get("bank_id"),
                string(m, "direction"),
                decimal(amount(m, "amount")),
                "USD",
                string(m, "status"),
                string(m, "reason"),
                string(m, "bank_snapshot"),
                instant(m, "recorded_at"));
    }

    private String encrypt(int client, UUID bank, String cleartext) {
        try {
            byte[] key = Base64.getDecoder().decode(encryptionKey);
            if (key.length != 32) throw new IllegalArgumentException();
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD((client + ":" + bank).getBytes(StandardCharsets.UTF_8));
            return "v1:"
                    + Base64.getEncoder().encodeToString(nonce)
                    + ":"
                    + Base64.getEncoder()
                            .encodeToString(
                                    cipher.doFinal(cleartext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Bank reference storage is not configured; contact the operator");
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot record banking evidence");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void validKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,128}"))
            throw new IllegalArgumentException(
                    "A stable 16–128 character request reference is required");
    }

    private void conflict(String message) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private void notFound() {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Bank reference or movement not found");
    }
}
