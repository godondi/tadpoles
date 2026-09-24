package com.neueda.leap.service.impl;

import com.neueda.leap.domain.Client;
import com.neueda.leap.dto.CreateClientRequestDto;
import com.neueda.leap.dto.UpdateClientRequestDto;
import com.neueda.leap.exception.ClientNotFoundException;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.service.ClientService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ClientServiceImpl implements ClientService {
    private final ClientMapper clientMapper;

    public ClientServiceImpl(ClientMapper clientMapper) {
        this.clientMapper = clientMapper;
    }

    @Override
    public Client getClient(Integer id) {
        validateId(id);

        Client client = clientMapper.getClient(id);
        if (client == null) {
            throw new ClientNotFoundException(id);
        }

        return client;
    }

    @Override
    public List<Client> listClients() {
        return clientMapper.listClients();
    }

    @Override
    public Client createClient(CreateClientRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Client request is required.");
        }
        if (request.clientName() == null || request.clientName().isBlank()) {
            throw new IllegalArgumentException("Client name is required.");
        }
        if (request.cashBalance() != null && request.cashBalance().signum() < 0) {
            throw new IllegalArgumentException("Client cash balance cannot be negative.");
        }
        validateOptionalPositiveId(request.advisorId(), "Advisor id must be a positive integer.");
        validateOptionalPositiveId(request.modelPortfolioId(), "Model portfolio id must be a positive integer.");
        validateOptionalPositiveId(request.createdByUserId(), "Created-by user id must be a positive integer.");

        Client client = new Client();
        client.setClientName(request.clientName().trim());
        client.setAdvisorId(request.advisorId());
        client.setModelPortfolioId(request.modelPortfolioId());
        client.setCreatedByUserId(request.createdByUserId());
        client.setCashBalance(request.cashBalance() == null ? BigDecimal.ZERO : request.cashBalance());

        clientMapper.insertClient(client);
        return getClient(client.getClientId());
    }

    @Override
    public Client updateClient(Integer id, UpdateClientRequestDto request) {
        validateId(id);
        if (request == null) {
            throw new IllegalArgumentException("Client update request is required.");
        }
        if (request.clientName() != null && request.clientName().isBlank()) {
            throw new IllegalArgumentException("Client name cannot be blank.");
        }
        if (request.cashBalance() != null && request.cashBalance().signum() < 0) {
            throw new IllegalArgumentException("Client cash balance cannot be negative.");
        }
        validateOptionalPositiveId(request.advisorId(), "Advisor id must be a positive integer.");
        validateOptionalPositiveId(request.modelPortfolioId(), "Model portfolio id must be a positive integer.");
        if (request.clientName() == null
                && request.advisorId() == null
                && request.modelPortfolioId() == null
                && request.cashBalance() == null) {
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }

        Client update = new Client();
        update.setClientId(id);
        update.setClientName(request.clientName() == null ? null : request.clientName().trim());
        update.setAdvisorId(request.advisorId());
        update.setModelPortfolioId(request.modelPortfolioId());
        update.setCashBalance(request.cashBalance());

        int rows = clientMapper.updateClient(update);
        if (rows == 0) {
            throw new ClientNotFoundException(id);
        }

        return getClient(id);
    }

    @Override
    public BigDecimal getClientBalance(Integer id) {
        validateId(id);
        BigDecimal cashBalance = clientMapper.getClientBalance(id);
        if (cashBalance == null) {
            throw new ClientNotFoundException(id);
        }
        return cashBalance;
    }

    private void validateId(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Client id must be a positive integer.");
        }
    }

    private void validateOptionalPositiveId(Integer id, String message) {
        if (id != null && id < 1) {
            throw new IllegalArgumentException(message);
        }
    }
}
