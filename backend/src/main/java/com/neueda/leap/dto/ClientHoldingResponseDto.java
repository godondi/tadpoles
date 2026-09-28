package com.neueda.leap.dto;

import com.neueda.leap.domain.ClientHolding;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ClientHoldingResponseDto(
        Integer holdingId,
        Integer clientId,
        Integer instrumentId,
        BigDecimal quantity,
        LocalDate asOfDate
) {
    public static ClientHoldingResponseDto fromEntity(ClientHolding holding) {
        return new ClientHoldingResponseDto(
                holding.getHoldingId(),
                holding.getClientId(),
                holding.getInstrumentId(),
                holding.getQuantity(),
                holding.getAsOfDate()
        );
    }
}

