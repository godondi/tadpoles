package com.neueda.leap.dto;

import com.neueda.leap.domain.Client;

public record ClientResponseDto(
        Integer clientId,
        String clientName,
        Integer advisorId,
        Integer modelPortfolioId
) {
    public static ClientResponseDto fromEntity(Client client) {
        return new ClientResponseDto(
                client.getClientId(),
                client.getClientName(),
                client.getAdvisorId(),
                client.getModelPortfolioId()
        );
    }
}



