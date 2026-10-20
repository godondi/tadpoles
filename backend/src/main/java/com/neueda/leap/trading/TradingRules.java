package com.neueda.leap.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.trading.TradingModels.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;

public final class TradingRules {
    private TradingRules() {}

    public static Command command(JsonNode json) {
        if (json == null || !json.isObject() || json.size() != 4)
            throw new IllegalArgumentException(
                    "Only instrumentId, side, quantity and orderType are accepted");
        var fields = json.fieldNames();
        while (fields.hasNext())
            if (!Set.of("instrumentId", "side", "quantity", "orderType").contains(fields.next()))
                throw new IllegalArgumentException("Unsupported order field");
        if (!json.path("instrumentId").isIntegralNumber()
                || !json.path("instrumentId").canConvertToInt()
                || !json.path("quantity").isTextual()
                || !json.path("side").isTextual()
                || !json.path("orderType").isTextual())
            throw new IllegalArgumentException(
                    "Quantity must be a decimal string and instrumentId an integer");
        Command c =
                new Command(
                        json.get("instrumentId").asInt(),
                        json.get("side").asText(),
                        json.get("quantity").asText(),
                        json.get("orderType").asText());
        validate(c);
        return c;
    }

    public static BigDecimal validate(Command c) {
        if (c == null
                || c.instrumentId() < 1
                || !Set.of("BUY", "SELL").contains(c.side())
                || !"MARKET".equals(c.orderType())
                || c.quantity() == null
                || !c.quantity().matches("[0-9]{1,16}(\\.[0-9]{1,12})?"))
            throw new IllegalArgumentException(
                    "Use a positive instrument ID, BUY or SELL, MARKET and an exact positive"
                            + " decimal quantity (up to 12 decimals)");
        BigDecimal q = new BigDecimal(c.quantity());
        if (q.signum() <= 0)
            throw new IllegalArgumentException("Quantity must be greater than zero");
        return q;
    }

    public static Evaluation evaluate(Command c, Policy p, Quote q, Instant now) {
        BigDecimal quantity = validate(c);
        if (p == null || !p.active() || !p.enabled())
            return reject("INSTRUMENT_UNAVAILABLE", "This instrument is not enabled for trading");
        if (!"PROVIDER_OPEN".equals(p.session()))
            return reject("SESSION_CLOSED", "The configured trading session is closed");
        if (!"USD".equals(p.currency())
                || !"USD".equals(p.priceUnit())
                || !"USD".equals(p.rawPriceUnit())
                || BigDecimal.ONE.compareTo(p.priceMultiplier()) != 0)
            return reject(
                    "CURRENCY_UNSUPPORTED",
                    "Only validated USD instruments are supported in this release");
        if (("BUY".equals(c.side()) && !p.buyAllowed())
                || ("SELL".equals(c.side()) && !p.sellAllowed()))
            return reject("SIDE_HALTED", "This trading direction is currently halted");
        if (quantity.remainder(p.quantityStep()).signum() != 0)
            return reject(
                    "QUANTITY_STEP",
                    "Enter a multiple of " + decimal(p.quantityStep()) + " " + p.quantityUnit());
        if (quantity.compareTo(p.maxQuantity()) > 0)
            return reject("QUANTITY_LIMIT", "Quantity exceeds this policy's limit");
        if (q == null
                || q.providerTime() == null
                || q.receivedAt() == null
                || q.bid() == null
                || q.ask() == null
                || q.source() == null
                || q.rawPriceUnit() == null
                || q.rawBid() == null
                || q.rawAsk() == null)
            return reject("QUOTE_MISSING", "A complete execution quote is unavailable");
        if (Boolean.TRUE.equals(q.stale()))
            return reject(
                    "QUOTE_STALE",
                    "Fauxnance marked this quote as stale. Wait for the provider to update, then"
                            + " review your order again");
        if (q.stale() == null)
            return reject(
                    "QUOTE_METADATA",
                    "The provider did not report quote freshness. Try again after a provider"
                            + " update");
        if (q.metadataTime() == null)
            return reject(
                    "QUOTE_METADATA",
                    "The provider omitted its metadata timestamp. A complete quote is required");
        if (!"modelled".equals(q.spreadOrigin()))
            return reject(
                    "QUOTE_METADATA",
                    "The provider did not report a supported bid/ask spread source");
        if (q.source().isBlank())
            return reject("QUOTE_METADATA", "The provider did not identify the quote source");
        if (!TradingSession.isOpen(q.marketState(), p.venue(), now))
            return reject(
                    "SESSION_CLOSED",
                    "unknown".equals(q.marketState())
                            ? "Provider market status is unknown and no supported scheduled core"
                                    + " session is open"
                            : "The provider does not report an open market");
        if (q.metadataTime().isBefore(now.minusSeconds(p.quoteMaxAgeSeconds()))
                || q.metadataTime().isAfter(now.plusSeconds(p.futureSkewSeconds())))
            return reject("QUOTE_METADATA_TIME", "Provider metadata is stale or future-dated");
        if (q.instrumentId() != c.instrumentId()
                || !java.util.Objects.equals(p.providerSymbol(), q.symbol())
                || !p.currency().equals(q.currency())
                || !p.priceUnit().equals(q.priceUnit())
                || !p.rawPriceUnit().equals(q.rawPriceUnit()))
            return reject("QUOTE_MAPPING", "Quote instrument, units or currency do not match");
        if (q.bid().signum() <= 0
                || q.ask().signum() <= 0
                || q.bid().compareTo(q.ask()) > 0
                || q.rawBid().multiply(p.priceMultiplier()).compareTo(q.bid()) != 0
                || q.rawAsk().multiply(p.priceMultiplier()).compareTo(q.ask()) != 0)
            return reject("QUOTE_INVALID", "Quote prices or normalized units are invalid");
        if (q.providerTime().isAfter(now.plusSeconds(p.futureSkewSeconds()))
                || q.receivedAt().isAfter(now.plusSeconds(p.futureSkewSeconds())))
            return reject("QUOTE_FUTURE", "Quote timestamp is in the future");
        if (q.providerTime().isBefore(now.minusSeconds(p.quoteMaxAgeSeconds()))
                || q.receivedAt().isBefore(now.minusSeconds(p.quoteMaxAgeSeconds())))
            return reject("QUOTE_STALE", "Quote has expired; request a fresh estimate");
        BigDecimal price = "BUY".equals(c.side()) ? q.ask() : q.bid();
        BigDecimal normalized = price.stripTrailingZeros();
        if (normalized.scale() > 12 || normalized.precision() - normalized.scale() > 16)
            return reject("QUOTE_PRECISION", "Quote price exceeds supported exact precision");
        BigDecimal gross =
                quantity.multiply(price).setScale(p.currencyScale(), RoundingMode.HALF_UP);
        if (gross.signum() <= 0
                || gross.compareTo(p.maxNotional()) > 0
                || gross.precision() - gross.scale() > 16)
            return reject(
                    "NOTIONAL_LIMIT", "Consideration is outside the configured posting limits");
        return new Evaluation(
                true,
                "ELIGIBLE",
                "unknown".equals(q.marketState())
                        ? "Eligible during the scheduled core session; provider market status is"
                                + " unknown"
                        : "Eligible under the trading policy",
                price,
                gross);
    }

    public static Evaluation reject(String code, String reason) {
        return new Evaluation(false, code, reason, null, null);
    }

    public static String decimal(BigDecimal n) {
        return n == null ? null : n.stripTrailingZeros().toPlainString();
    }
}
