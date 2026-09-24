package com.neueda.leap.dto;

import com.neueda.leap.domain.Client;
import java.util.List;

public record ClientListResponseDto(
        List<ClientResponseDto> clients
) {
    public static ClientListResponseDto fromEntities(List<Client> clients) {
        return new ClientListResponseDto(
                clients.stream().map(ClientResponseDto::fromEntity).toList()
        );
    }
}

