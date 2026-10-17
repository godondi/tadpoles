package com.neueda.leap.dto;

import java.time.Instant;
import java.util.List;

/** Informational USD valuation; exact decimal strings never authorize an order. */
public record PortfolioValuationDto(
        String currency,
        String status,
        String totalValue,
        String holdingsValue,
        String cashBalance,
        Instant snapshotAt,
        Instant quoteAsOf,
        List<String> unpricedTickers) {}
