package com.neueda.leap.dto;

import java.math.BigDecimal;

public record ClientBalanceResponseDto(
        Integer clientId,
        BigDecimal cashBalance
) {
}

