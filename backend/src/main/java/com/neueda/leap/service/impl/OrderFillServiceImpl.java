package com.neueda.leap.service.impl;

import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.ClientHolding;
import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.domain.Instrument;
import com.neueda.leap.dto.FillOrderRequestDto;
import com.neueda.leap.dto.OrderFillResponseDto;
import com.neueda.leap.exception.ClientTradeNotFoundException;
import com.neueda.leap.exception.InstrumentNotFoundException;
import com.neueda.leap.mapper.ClientHoldingMapper;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.mapper.ClientTradeMapper;
import com.neueda.leap.mapper.InstrumentMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderFillServiceImpl implements com.neueda.leap.service.OrderFillService {
    private final ClientMapper clientMapper;
    private final ClientTradeMapper clientTradeMapper;
    private final ClientHoldingMapper clientHoldingMapper;
    private final InstrumentMapper instrumentMapper;

    public OrderFillServiceImpl(
            ClientMapper clientMapper,
            ClientTradeMapper clientTradeMapper,
            ClientHoldingMapper clientHoldingMapper,
            InstrumentMapper instrumentMapper
    ) {
        this.clientMapper = clientMapper;
        this.clientTradeMapper = clientTradeMapper;
        this.clientHoldingMapper = clientHoldingMapper;
        this.instrumentMapper = instrumentMapper;
    }

    @Override
    @Transactional
    public OrderFillResponseDto fillTrade(Integer clientId, Integer tradeId, FillOrderRequestDto request) {
        validatePositiveId(clientId, "Client id must be a positive integer.");
        validatePositiveId(tradeId, "Trade id must be a positive integer.");
        if (request != null && request.approvedByUserId() != null && request.approvedByUserId() < 1) {
            throw new IllegalArgumentException("Approved-by user id must be a positive integer.");
        }
        if (request != null && request.price() != null && request.price().signum() <= 0) {
            throw new IllegalArgumentException("Execution price must be greater than zero.");
        }

        ClientTrade trade = clientTradeMapper.getTradeForUpdate(tradeId);
        if (trade == null || !clientId.equals(trade.getClientId())) {
            throw new ClientTradeNotFoundException(clientId, tradeId);
        }
        if (!"PENDING".equals(trade.getStatus()) && !"APPROVED".equals(trade.getStatus())) {
            throw new IllegalArgumentException("Only PENDING or APPROVED trades can be filled.");
        }

        Client client = clientMapper.getClientForUpdate(clientId);
        if (client == null) {
            throw new IllegalArgumentException("Client with id " + clientId + " was not found.");
        }

        Instrument instrument = instrumentMapper.getInstrument(trade.getInstrumentId());
        if (instrument == null) {
            throw new InstrumentNotFoundException(trade.getInstrumentId());
        }
        if (Boolean.FALSE.equals(instrument.getIsActive())) {
            throw new IllegalArgumentException("Inactive instruments cannot be filled.");
        }

        BigDecimal executionPrice = request != null && request.price() != null ? request.price() : trade.getPrice();
        LocalDateTime executedAt = request != null && request.executedAt() != null ? request.executedAt() : LocalDateTime.now();
        String reason = request != null && request.reason() != null ? request.reason().trim() : trade.getReason();
        BigDecimal cashDelta = trade.getQuantity().multiply(executionPrice).setScale(2, RoundingMode.HALF_UP);

        ClientHolding holding = clientHoldingMapper.getLatestHoldingForUpdate(clientId, trade.getInstrumentId());
        BigDecimal currentQuantity = holding == null ? BigDecimal.ZERO : holding.getQuantity();
        BigDecimal newQuantity;
        BigDecimal newCashBalance;

        if ("BUY".equals(trade.getTradeType())) {
            if (client.getCashBalance().compareTo(cashDelta) < 0) {
                throw new IllegalArgumentException("Client does not have enough cash to fill this trade.");
            }
            newQuantity = currentQuantity.add(trade.getQuantity());
            newCashBalance = client.getCashBalance().subtract(cashDelta);
        } else {
            if (currentQuantity.compareTo(trade.getQuantity()) < 0) {
                throw new IllegalArgumentException("Client does not have enough holdings to fill this trade.");
            }
            newQuantity = currentQuantity.subtract(trade.getQuantity());
            newCashBalance = client.getCashBalance().add(cashDelta);
        }

        LocalDate asOfDate = executedAt.toLocalDate();
        if (holding == null) {
            holding = new ClientHolding();
            holding.setClientId(clientId);
            holding.setInstrumentId(trade.getInstrumentId());
            holding.setQuantity(newQuantity);
            holding.setAsOfDate(asOfDate);
            clientHoldingMapper.insertClientHolding(holding);
        } else if (newQuantity.signum() == 0) {
            clientHoldingMapper.deleteClientHolding(clientId, holding.getHoldingId());
        } else {
            holding.setQuantity(newQuantity);
            holding.setAsOfDate(asOfDate);
            clientHoldingMapper.updateClientHolding(holding);
        }

        clientMapper.updateClientBalance(clientId, newCashBalance);
        int updatedTrades = clientTradeMapper.markTradeExecuted(
                tradeId,
                request == null ? trade.getApprovedByUserId() : request.approvedByUserId(),
                executionPrice,
                executedAt,
                reason
        );
        if (updatedTrades == 0) {
            throw new ClientTradeNotFoundException(clientId, tradeId);
        }

        return new OrderFillResponseDto(
                clientId,
                newCashBalance,
                holding.getHoldingId(),
                newQuantity,
                tradeId,
                "EXECUTED",
                executedAt
        );
    }

    private void validatePositiveId(Integer id, String message) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException(message);
        }
    }
}


