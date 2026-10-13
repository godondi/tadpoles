package com.neueda.leap.dto;

import com.neueda.leap.domain.TradeSuggestion;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeSuggestionResponseDto(
        Integer suggestionId,
        Integer advisorId,
        Integer clientId,
        Integer instrumentId,
        String tradeType,
        BigDecimal quantity,
        BigDecimal proposedPrice,
        LocalDateTime suggestedAt,
        String status,
        String notes
) {
    public static TradeSuggestionResponseDto fromEntity(TradeSuggestion suggestion) {
        return new TradeSuggestionResponseDto(
                suggestion.getSuggestionId(),
                suggestion.getAdvisorId(),
                suggestion.getClientId(),
                suggestion.getInstrumentId(),
                suggestion.getTradeType(),
                suggestion.getQuantity(),
                suggestion.getProposedPrice(),
                suggestion.getSuggestedAt(),
                suggestion.getStatus(),
                suggestion.getNotes()
        );
    }
}

