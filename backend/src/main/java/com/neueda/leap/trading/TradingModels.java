package com.neueda.leap.trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Exact values stay decimal strings at the HTTP boundary. */
public final class TradingModels {
    private TradingModels() {}

    public record Command(int instrumentId, String side, String quantity, String orderType) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Policy(
            int instrumentId,
            String ticker,
            boolean active,
            String version,
            String venue,
            String quantityUnit,
            String priceUnit,
            String currency,
            BigDecimal quantityStep,
            int currencyScale,
            BigDecimal maxQuantity,
            BigDecimal maxNotional,
            int quoteMaxAgeSeconds,
            int futureSkewSeconds,
            int retrySeconds,
            int maxAttempts,
            String session,
            boolean enabled,
            String rawPriceUnit,
            BigDecimal priceMultiplier,
            String providerSymbol,
            boolean buyAllowed,
            boolean sellAllowed) {}

    public record Quote(
            UUID quoteId,
            int instrumentId,
            String symbol,
            String currency,
            String rawPriceUnit,
            String priceUnit,
            BigDecimal rawBid,
            BigDecimal rawAsk,
            BigDecimal bid,
            BigDecimal ask,
            Instant providerTime,
            Instant receivedAt,
            String source,
            String disclaimer,
            String spreadOrigin,
            String marketState,
            Boolean stale,
            Instant metadataTime) {}

    public record Evaluation(
            boolean eligible, String code, String reason, BigDecimal price, BigDecimal gross) {}

    public record Preview(
            boolean eligible,
            String code,
            String reason,
            String executionMode,
            String policyVersion,
            String currency,
            String quantityUnit,
            String venue,
            String price,
            String gross,
            String fee,
            String availableCash,
            String availableQuantity,
            String projectedCash,
            String projectedQuantity,
            Instant quoteTime,
            Instant expiresAt,
            String source,
            String message) {}
}
