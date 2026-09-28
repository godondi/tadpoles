package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderFillResponseDto(
        Integer clientId,
        BigDecimal cashBalance,
        Integer holdingId,
        BigDecimal holdingQuantity,
        Integer tradeId,
        String tradeStatus,
        LocalDateTime executedAt
) {
}

