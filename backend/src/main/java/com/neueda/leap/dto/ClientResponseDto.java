package com.neueda.leap.dto;

import com.neueda.leap.domain.Client;
import java.math.BigDecimal;

public record ClientResponseDto(
        Integer clientId,
        String clientName,
        Integer advisorId,
        Integer modelPortfolioId,
        BigDecimal cashBalance
) {
    public static ClientResponseDto fromEntity(Client client) {
        return new ClientResponseDto(
                client.getClientId(),
                client.getClientName(),
                client.getAdvisorId(),
                client.getModelPortfolioId(),
                client.getCashBalance()
        );
    }
}


