package com.neueda.leap.dto;

import java.math.BigDecimal;

public record UpdateModelPortfolioHoldingRequestDto(
        BigDecimal targetWeightPct
) {
}

