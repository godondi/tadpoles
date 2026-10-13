package com.neueda.leap.service;

import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.dto.CreateClientTradeRequestDto;
import com.neueda.leap.dto.UpdateClientTradeRequestDto;
import java.util.List;

public interface ClientTradeService {
    List<ClientTrade> listClientTrades(Integer clientId);
    ClientTrade getTrade(Integer clientId, Integer tradeId);
    ClientTrade createTrade(Integer clientId, CreateClientTradeRequestDto request);
    ClientTrade updateTrade(Integer clientId, Integer tradeId, UpdateClientTradeRequestDto request);
}

