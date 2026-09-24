package com.neueda.leap.service.impl;

import com.neueda.leap.domain.Client;
import com.neueda.leap.exception.ClientNotFoundException;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.service.ClientService;
import org.springframework.stereotype.Service;

@Service
public class ClientServiceImpl implements ClientService {
    private final ClientMapper clientMapper;

    public ClientServiceImpl(ClientMapper clientMapper) {
        this.clientMapper = clientMapper;
    }

    @Override
    public Client getClient(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Client id must be a positive integer.");
        }

        Client client = clientMapper.getClient(id);
        if (client == null) {
            throw new ClientNotFoundException(id);
        }

        return client;
    }
}


