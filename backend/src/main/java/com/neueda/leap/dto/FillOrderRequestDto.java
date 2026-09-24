package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FillOrderRequestDto(
        Integer approvedByUserId,
        BigDecimal price,
        LocalDateTime executedAt,
        String reason
) {
}

