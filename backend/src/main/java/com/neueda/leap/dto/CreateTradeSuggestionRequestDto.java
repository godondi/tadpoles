package com.neueda.leap.dto;

import java.math.BigDecimal;

public record CreateTradeSuggestionRequestDto(
        Integer instrumentId,
        String tradeType,
        BigDecimal quantity,
        BigDecimal proposedPrice,
        String notes
) {
}

