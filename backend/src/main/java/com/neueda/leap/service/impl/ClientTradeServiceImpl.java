package com.neueda.leap.service.impl;

import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.dto.CreateClientTradeRequestDto;
import com.neueda.leap.dto.UpdateClientTradeRequestDto;
import com.neueda.leap.exception.ClientTradeNotFoundException;
import com.neueda.leap.mapper.ClientTradeMapper;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.ClientTradeService;
import com.neueda.leap.service.InstrumentService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ClientTradeServiceImpl implements ClientTradeService {
    private static final Set<String> VALID_TRADE_TYPES = Set.of("BUY", "SELL");
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "EXECUTED");

    private final ClientTradeMapper clientTradeMapper;
    private final ClientService clientService;
    private final InstrumentService instrumentService;

    public ClientTradeServiceImpl(
            ClientTradeMapper clientTradeMapper,
            ClientService clientService,
            InstrumentService instrumentService
    ) {
        this.clientTradeMapper = clientTradeMapper;
        this.clientService = clientService;
        this.instrumentService = instrumentService;
    }

    @Override
    public List<ClientTrade> listClientTrades(Integer clientId) {
        validateClientId(clientId);
        clientService.getClient(clientId);
        return clientTradeMapper.listClientTrades(clientId);
    }

    @Override
    public ClientTrade getTrade(Integer clientId, Integer tradeId) {
        validateClientId(clientId);
        validateTradeId(tradeId);
        clientService.getClient(clientId);
        ClientTrade trade = clientTradeMapper.getClientTrade(clientId, tradeId);
        if (trade == null) {
            throw new ClientTradeNotFoundException(clientId, tradeId);
        }
        return trade;
    }

    @Override
    public ClientTrade createTrade(Integer clientId, CreateClientTradeRequestDto request) {
        validateClientId(clientId);
        if (request == null) {
            throw new IllegalArgumentException("Trade request is required.");
        }
        validateRequiredInstrumentId(request.instrumentId());
        validateOptionalPositiveId(request.submittedByUserId(), "Submitted-by user id must be a positive integer.");
        validateOptionalPositiveId(request.approvedByUserId(), "Approved-by user id must be a positive integer.");
        validateTradeType(request.tradeType());
        validatePositiveNumber(request.quantity(), "Trade quantity must be greater than zero.");
        validatePositiveNumber(request.price(), "Trade price must be greater than zero.");
        if (request.tradeDate() == null) {
            throw new IllegalArgumentException("Trade date is required.");
        }
        validateStatus(request.status());

        clientService.getClient(clientId);
        instrumentService.getInstrument(request.instrumentId());

        ClientTrade trade = new ClientTrade();
        trade.setClientId(clientId);
        trade.setInstrumentId(request.instrumentId());
        trade.setSubmittedByUserId(request.submittedByUserId());
        trade.setApprovedByUserId(request.approvedByUserId());
        trade.setTradeType(request.tradeType().trim().toUpperCase());
        trade.setQuantity(request.quantity());
        trade.setPrice(request.price());
        trade.setTradeDate(request.tradeDate());
        trade.setStatus(normalizeStatus(request.status(), "PENDING"));
        trade.setReason(trimToNull(request.reason()));

        clientTradeMapper.insertClientTrade(trade);
        return getTrade(clientId, trade.getTradeId());
    }

    @Override
    public ClientTrade updateTrade(Integer clientId, Integer tradeId, UpdateClientTradeRequestDto request) {
        validateClientId(clientId);
        validateTradeId(tradeId);
        if (request == null) {
            throw new IllegalArgumentException("Trade update request is required.");
        }
        String normalizedReason = trimToNull(request.reason());
        String normalizedStatus = request.status() == null ? null : request.status().trim().toUpperCase();
        if (request.submittedByUserId() == null
                && request.approvedByUserId() == null
                && normalizedStatus == null
                && request.executedAt() == null
                && normalizedReason == null) {
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }
        validateOptionalPositiveId(request.submittedByUserId(), "Submitted-by user id must be a positive integer.");
        validateOptionalPositiveId(request.approvedByUserId(), "Approved-by user id must be a positive integer.");
        validateStatus(normalizedStatus);

        clientService.getClient(clientId);

        ClientTrade trade = new ClientTrade();
        trade.setClientId(clientId);
        trade.setTradeId(tradeId);
        trade.setSubmittedByUserId(request.submittedByUserId());
        trade.setApprovedByUserId(request.approvedByUserId());
        trade.setStatus(normalizedStatus);
        trade.setExecutedAt(request.executedAt());
        trade.setReason(normalizedReason);

        int rows = clientTradeMapper.updateClientTrade(trade);
        if (rows == 0) {
            throw new ClientTradeNotFoundException(clientId, tradeId);
        }

        return getTrade(clientId, tradeId);
    }

    private void validateClientId(Integer clientId) {
        if (clientId == null || clientId < 1) {
            throw new IllegalArgumentException("Client id must be a positive integer.");
        }
    }

    private void validateTradeId(Integer tradeId) {
        if (tradeId == null || tradeId < 1) {
            throw new IllegalArgumentException("Trade id must be a positive integer.");
        }
    }

    private void validateRequiredInstrumentId(Integer instrumentId) {
        if (instrumentId == null || instrumentId < 1) {
            throw new IllegalArgumentException("Instrument id must be a positive integer.");
        }
    }

    private void validateOptionalPositiveId(Integer id, String message) {
        if (id != null && id < 1) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateTradeType(String tradeType) {
        if (tradeType == null || !VALID_TRADE_TYPES.contains(tradeType.trim().toUpperCase())) {
            throw new IllegalArgumentException("Trade type must be BUY or SELL.");
        }
    }

    private void validatePositiveNumber(java.math.BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateStatus(String status) {
        if (status != null && !VALID_STATUSES.contains(status.trim().toUpperCase())) {
            throw new IllegalArgumentException("Trade status must be one of PENDING, APPROVED, REJECTED, or EXECUTED.");
        }
    }

    private String normalizeStatus(String status, String defaultValue) {
        return status == null ? defaultValue : status.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}


