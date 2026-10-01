package com.neueda.leap.service.impl;

import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.TradeSuggestion;
import com.neueda.leap.dto.CreateTradeSuggestionRequestDto;
import com.neueda.leap.dto.UpdateTradeSuggestionRequestDto;
import com.neueda.leap.exception.TradeSuggestionNotFoundException;
import com.neueda.leap.mapper.TradeSuggestionMapper;
import com.neueda.leap.service.AdvisorService;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.InstrumentService;
import com.neueda.leap.service.TradeSuggestionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TradeSuggestionServiceImpl implements TradeSuggestionService {
    private static final Set<String> VALID_TRADE_TYPES = Set.of("BUY", "SELL");
    private static final Set<String> VALID_STATUSES = Set.of("SUGGESTED", "VIEWED", "ACCEPTED", "REJECTED", "EXPIRED");
    private static final Set<String> VALID_DECISION_STATUSES = Set.of("VIEWED", "ACCEPTED", "REJECTED", "EXPIRED");

    private final TradeSuggestionMapper tradeSuggestionMapper;
    private final AdvisorService advisorService;
    private final ClientService clientService;
    private final InstrumentService instrumentService;

    public TradeSuggestionServiceImpl(
            TradeSuggestionMapper tradeSuggestionMapper,
            AdvisorService advisorService,
            ClientService clientService,
            InstrumentService instrumentService
    ) {
        this.tradeSuggestionMapper = tradeSuggestionMapper;
        this.advisorService = advisorService;
        this.clientService = clientService;
        this.instrumentService = instrumentService;
    }

    @Override
    public List<TradeSuggestion> listClientTradeSuggestions(Integer clientId) {
        validateClientId(clientId);
        clientService.getClient(clientId);
        return tradeSuggestionMapper.listClientTradeSuggestions(clientId);
    }

    @Override
    public TradeSuggestion getTradeSuggestion(Integer suggestionId) {
        validateSuggestionId(suggestionId);
        TradeSuggestion suggestion = tradeSuggestionMapper.getTradeSuggestion(suggestionId);
        if (suggestion == null) {
            throw new TradeSuggestionNotFoundException(suggestionId);
        }
        return suggestion;
    }

    @Override
    public TradeSuggestion createTradeSuggestion(
            Integer advisorId,
            Integer clientId,
            CreateTradeSuggestionRequestDto request
    ) {
        validateAdvisorId(advisorId);
        validateClientId(clientId);
        if (request == null) {
            throw new IllegalArgumentException("Trade suggestion request is required.");
        }
        validateInstrumentId(request.instrumentId());
        validateTradeType(request.tradeType());
        validatePositiveNumber(request.quantity(), "Trade suggestion quantity must be greater than zero.");
        if (request.proposedPrice() != null) {
            validatePositiveNumber(request.proposedPrice(), "Proposed price must be greater than zero.");
        }

        advisorService.getAdvisor(advisorId);
        Client client = clientService.getClient(clientId);
        ensureClientBelongsToAdvisor(advisorId, client);
        instrumentService.getInstrument(request.instrumentId());

        TradeSuggestion suggestion = new TradeSuggestion();
        suggestion.setAdvisorId(advisorId);
        suggestion.setClientId(clientId);
        suggestion.setInstrumentId(request.instrumentId());
        suggestion.setTradeType(request.tradeType().trim().toUpperCase());
        suggestion.setQuantity(request.quantity());
        suggestion.setProposedPrice(request.proposedPrice());
        suggestion.setSuggestedAt(LocalDateTime.now());
        suggestion.setStatus("SUGGESTED");
        suggestion.setNotes(trimToNull(request.notes()));

        tradeSuggestionMapper.insertTradeSuggestion(suggestion);
        return getTradeSuggestion(suggestion.getSuggestionId());
    }

    @Override
    public TradeSuggestion updateTradeSuggestion(Integer suggestionId, UpdateTradeSuggestionRequestDto request) {
        validateSuggestionId(suggestionId);
        if (request == null) {
            throw new IllegalArgumentException("Trade suggestion update request is required.");
        }

        String normalizedStatus = request.status() == null ? null : request.status().trim().toUpperCase();
        String normalizedNotes = trimToNull(request.notes());
        if (normalizedStatus == null && normalizedNotes == null) {
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }
        if (normalizedStatus != null && !VALID_DECISION_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Trade suggestion status must be one of VIEWED, ACCEPTED, REJECTED, or EXPIRED."
            );
        }

        TradeSuggestion suggestion = new TradeSuggestion();
        suggestion.setSuggestionId(suggestionId);
        suggestion.setStatus(normalizedStatus);
        suggestion.setNotes(normalizedNotes);

        int rows = tradeSuggestionMapper.updateTradeSuggestion(suggestion);
        if (rows == 0) {
            throw new TradeSuggestionNotFoundException(suggestionId);
        }

        TradeSuggestion updated = getTradeSuggestion(suggestionId);
        validateExistingStatus(updated.getStatus());
        return updated;
    }

    private void ensureClientBelongsToAdvisor(Integer advisorId, Client client) {
        if (client.getAdvisorId() == null || !client.getAdvisorId().equals(advisorId)) {
            throw new IllegalArgumentException(
                    "Client " + client.getClientId() + " is not assigned to advisor " + advisorId + "."
            );
        }
    }

    private void validateAdvisorId(Integer advisorId) {
        if (advisorId == null || advisorId < 1) {
            throw new IllegalArgumentException("Advisor id must be a positive integer.");
        }
    }

    private void validateClientId(Integer clientId) {
        if (clientId == null || clientId < 1) {
            throw new IllegalArgumentException("Client id must be a positive integer.");
        }
    }

    private void validateInstrumentId(Integer instrumentId) {
        if (instrumentId == null || instrumentId < 1) {
            throw new IllegalArgumentException("Instrument id must be a positive integer.");
        }
    }

    private void validateSuggestionId(Integer suggestionId) {
        if (suggestionId == null || suggestionId < 1) {
            throw new IllegalArgumentException("Trade suggestion id must be a positive integer.");
        }
    }

    private void validateTradeType(String tradeType) {
        if (tradeType == null || !VALID_TRADE_TYPES.contains(tradeType.trim().toUpperCase())) {
            throw new IllegalArgumentException("Trade type must be BUY or SELL.");
        }
    }

    private void validatePositiveNumber(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateExistingStatus(String status) {
        if (status != null && !VALID_STATUSES.contains(status.trim().toUpperCase())) {
            throw new IllegalArgumentException(
                    "Trade suggestion status must be one of SUGGESTED, VIEWED, ACCEPTED, REJECTED, or EXPIRED."
            );
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

