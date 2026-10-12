package com.neueda.leap.service.impl;

import java.math.BigDecimal;

public record ExecutionQuote(
        String symbol,
        String assetClass,
        BigDecimal bid,
        BigDecimal ask,
        boolean stale
) {
}
