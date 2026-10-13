package com.neueda.leap.service;

import com.neueda.leap.domain.ClientHolding;
import com.neueda.leap.dto.CreateClientHoldingRequestDto;
import com.neueda.leap.dto.UpdateClientHoldingRequestDto;
import java.util.List;

public interface ClientHoldingService {
    List<ClientHolding> listClientHoldings(Integer clientId);
    ClientHolding getHolding(Integer clientId, Integer holdingId);
    ClientHolding createHolding(Integer clientId, CreateClientHoldingRequestDto request);
    ClientHolding updateHolding(Integer clientId, Integer holdingId, UpdateClientHoldingRequestDto request);
}

