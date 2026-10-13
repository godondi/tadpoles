package com.neueda.leap.dto;

import java.time.LocalDateTime;

public record UpdateClientTradeRequestDto(
        Integer submittedByUserId,
        Integer approvedByUserId,
        String status,
        LocalDateTime executedAt,
        String reason
) {
}

