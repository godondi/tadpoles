package com.neueda.leap.dto;

import com.neueda.leap.domain.ClientHolding;
import java.util.List;

public record ClientHoldingListResponseDto(
        List<ClientHoldingResponseDto> holdings
) {
    public static ClientHoldingListResponseDto fromEntities(List<ClientHolding> holdings) {
        return new ClientHoldingListResponseDto(
                holdings.stream().map(ClientHoldingResponseDto::fromEntity).toList()
        );
    }
}

