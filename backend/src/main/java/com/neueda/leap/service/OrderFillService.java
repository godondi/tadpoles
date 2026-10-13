package com.neueda.leap.service;

import com.neueda.leap.dto.FillOrderRequestDto;
import com.neueda.leap.dto.OrderFillResponseDto;

public interface OrderFillService {
    OrderFillResponseDto fillTrade(Integer clientId, Integer tradeId, FillOrderRequestDto request);
}

