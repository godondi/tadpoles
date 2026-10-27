package com.neueda.leap.dto;

import java.time.Instant;
import java.util.List;

/**
 * Informational USD valuation; exact decimal strings never authorize an order. The all-time return
 * uses Modified Dietz with dated external cash flows.
 */
public record PortfolioValuationDto(
        String currency,
        String status,
        String totalValue,
        String holdingsValue,
        String cashBalance,
        Instant snapshotAt,
        Instant quoteAsOf,
        String allTimeReturnPercent,
        List<String> unpricedTickers) {}
