package com.neueda.leap.dto;

import com.neueda.leap.domain.ClientTrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClientTradeResponseDto(
        Integer tradeId,
        Integer clientId,
        Integer instrumentId,
        Integer submittedByUserId,
        Integer approvedByUserId,
        String tradeType,
        BigDecimal quantity,
        BigDecimal price,
        LocalDate tradeDate,
        String status,
        LocalDateTime executedAt,
        String reason
) {
    public static ClientTradeResponseDto fromEntity(ClientTrade trade) {
        return new ClientTradeResponseDto(
                trade.getTradeId(),
                trade.getClientId(),
                trade.getInstrumentId(),
                trade.getSubmittedByUserId(),
                trade.getApprovedByUserId(),
                trade.getTradeType(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getTradeDate(),
                trade.getStatus(),
                trade.getExecutedAt(),
                trade.getReason()
        );
    }
}

