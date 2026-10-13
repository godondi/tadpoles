package com.neueda.leap.dto;

import java.math.BigDecimal;

public record CreateModelPortfolioHoldingRequestDto(
        Integer instrumentId,
        BigDecimal targetWeightPct
) {
}
