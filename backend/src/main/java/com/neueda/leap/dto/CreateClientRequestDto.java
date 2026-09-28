package com.neueda.leap.dto;

import java.math.BigDecimal;

public record CreateClientRequestDto(
        String clientName,
        Integer advisorId,
        Integer modelPortfolioId,
        Integer createdByUserId,
        BigDecimal cashBalance
) {
}

