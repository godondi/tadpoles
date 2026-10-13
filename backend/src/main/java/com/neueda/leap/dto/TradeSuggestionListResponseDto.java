package com.neueda.leap.dto;

import com.neueda.leap.domain.TradeSuggestion;
import java.util.List;

public record TradeSuggestionListResponseDto(
        List<TradeSuggestionResponseDto> suggestions
) {
    public static TradeSuggestionListResponseDto fromEntities(List<TradeSuggestion> suggestions) {
        return new TradeSuggestionListResponseDto(
                suggestions.stream().map(TradeSuggestionResponseDto::fromEntity).toList()
        );
    }
}

