package com.neueda.leap.dto;

import com.neueda.leap.domain.ClientTrade;
import java.util.List;

public record ClientTradeListResponseDto(
        List<ClientTradeResponseDto> trades
) {
    public static ClientTradeListResponseDto fromEntities(List<ClientTrade> trades) {
        return new ClientTradeListResponseDto(
                trades.stream().map(ClientTradeResponseDto::fromEntity).toList()
        );
    }
}

