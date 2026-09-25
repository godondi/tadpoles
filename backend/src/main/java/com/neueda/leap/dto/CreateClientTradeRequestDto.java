package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateClientTradeRequestDto(
        Integer instrumentId,
        Integer submittedByUserId,
        Integer approvedByUserId,
        String tradeType,
        BigDecimal quantity,
        BigDecimal price,
        LocalDate tradeDate,
        String status,
        String reason
) {
}

