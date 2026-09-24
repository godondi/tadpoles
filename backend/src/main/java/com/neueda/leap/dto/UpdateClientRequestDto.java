package com.neueda.leap.dto;

import java.math.BigDecimal;

public record UpdateClientRequestDto(
        String clientName,
        Integer advisorId,
        Integer modelPortfolioId,
        BigDecimal cashBalance
) {
}

