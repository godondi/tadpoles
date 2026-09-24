package com.neueda.leap.service.impl;

import com.neueda.leap.domain.ClientHolding;
import com.neueda.leap.dto.CreateClientHoldingRequestDto;
import com.neueda.leap.dto.UpdateClientHoldingRequestDto;
import com.neueda.leap.exception.ClientHoldingNotFoundException;
import com.neueda.leap.mapper.ClientHoldingMapper;
import com.neueda.leap.service.ClientHoldingService;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.InstrumentService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ClientHoldingServiceImpl implements ClientHoldingService {
    private final ClientHoldingMapper clientHoldingMapper;
    private final ClientService clientService;
    private final InstrumentService instrumentService;

    public ClientHoldingServiceImpl(
            ClientHoldingMapper clientHoldingMapper,
            ClientService clientService,
            InstrumentService instrumentService
    ) {
        this.clientHoldingMapper = clientHoldingMapper;
        this.clientService = clientService;
        this.instrumentService = instrumentService;
    }

    @Override
    public List<ClientHolding> listClientHoldings(Integer clientId) {
        validateClientId(clientId);
        clientService.getClient(clientId);
        return clientHoldingMapper.listClientHoldings(clientId);
    }

    @Override
    public ClientHolding getHolding(Integer clientId, Integer holdingId) {
        validateClientId(clientId);
        validateHoldingId(holdingId);
        clientService.getClient(clientId);
        ClientHolding holding = clientHoldingMapper.getClientHolding(clientId, holdingId);
        if (holding == null) {
            throw new ClientHoldingNotFoundException(clientId, holdingId);
        }
        return holding;
    }

    @Override
    public ClientHolding createHolding(Integer clientId, CreateClientHoldingRequestDto request) {
        validateClientId(clientId);
        if (request == null) {
            throw new IllegalArgumentException("Holding request is required.");
        }
        validateRequiredInstrumentId(request.instrumentId());
        validatePositiveQuantity(request.quantity(), "Holding quantity must be greater than zero.");
        if (request.asOfDate() == null) {
            throw new IllegalArgumentException("Holding as-of date is required.");
        }

        clientService.getClient(clientId);
        instrumentService.getInstrument(request.instrumentId());

        ClientHolding holding = new ClientHolding();
        holding.setClientId(clientId);
        holding.setInstrumentId(request.instrumentId());
        holding.setQuantity(request.quantity());
        holding.setAsOfDate(request.asOfDate());

        clientHoldingMapper.insertClientHolding(holding);
        return getHolding(clientId, holding.getHoldingId());
    }

    @Override
    public ClientHolding updateHolding(Integer clientId, Integer holdingId, UpdateClientHoldingRequestDto request) {
        validateClientId(clientId);
        validateHoldingId(holdingId);
        if (request == null) {
            throw new IllegalArgumentException("Holding update request is required.");
        }
        if (request.quantity() == null && request.asOfDate() == null) {
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }
        if (request.quantity() != null && request.quantity().signum() <= 0) {
            throw new IllegalArgumentException("Holding quantity must be greater than zero.");
        }

        clientService.getClient(clientId);

        ClientHolding holding = new ClientHolding();
        holding.setClientId(clientId);
        holding.setHoldingId(holdingId);
        holding.setQuantity(request.quantity());
        holding.setAsOfDate(request.asOfDate());

        int rows = clientHoldingMapper.updateClientHolding(holding);
        if (rows == 0) {
            throw new ClientHoldingNotFoundException(clientId, holdingId);
        }

        return getHolding(clientId, holdingId);
    }

    private void validateClientId(Integer clientId) {
        if (clientId == null || clientId < 1) {
            throw new IllegalArgumentException("Client id must be a positive integer.");
        }
    }

    private void validateHoldingId(Integer holdingId) {
        if (holdingId == null || holdingId < 1) {
            throw new IllegalArgumentException("Holding id must be a positive integer.");
        }
    }

    private void validateRequiredInstrumentId(Integer instrumentId) {
        if (instrumentId == null || instrumentId < 1) {
            throw new IllegalArgumentException("Instrument id must be a positive integer.");
        }
    }

    private void validatePositiveQuantity(java.math.BigDecimal quantity, String message) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}

