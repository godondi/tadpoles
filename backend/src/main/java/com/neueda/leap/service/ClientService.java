package com.neueda.leap.service;

import com.neueda.leap.domain.Client;
import com.neueda.leap.dto.CreateClientRequestDto;
import com.neueda.leap.dto.UpdateClientRequestDto;
import java.math.BigDecimal;
import java.util.List;

public interface ClientService {
    Client getClient(Integer id);
    List<Client> listClients();
    Client createClient(CreateClientRequestDto request);
    Client updateClient(Integer id, UpdateClientRequestDto request);
    BigDecimal getClientBalance(Integer id);
}

