package com.neueda.leap.service;

import com.neueda.leap.domain.TradeSuggestion;
import com.neueda.leap.dto.CreateTradeSuggestionRequestDto;
import com.neueda.leap.dto.UpdateTradeSuggestionRequestDto;
import java.util.List;

public interface TradeSuggestionService {
    List<TradeSuggestion> listClientTradeSuggestions(Integer clientId);
    TradeSuggestion getTradeSuggestion(Integer suggestionId);
    TradeSuggestion createTradeSuggestion(Integer advisorId, Integer clientId, CreateTradeSuggestionRequestDto request);
    TradeSuggestion updateTradeSuggestion(Integer suggestionId, UpdateTradeSuggestionRequestDto request);
}

