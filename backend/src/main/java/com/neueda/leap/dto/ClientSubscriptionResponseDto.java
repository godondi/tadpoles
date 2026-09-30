package com.neueda.leap.dto;

import com.neueda.leap.domain.ClientSubscription;
import java.time.LocalDate;

public record ClientSubscriptionResponseDto(
        Integer subscriptionId,
        Integer clientId,
        Integer modelPortfolioId,
        LocalDate subscribedDate,
        LocalDate endedDate,
        String status
) {
    public static ClientSubscriptionResponseDto fromEntity(ClientSubscription subscription) {
        return new ClientSubscriptionResponseDto(
                subscription.getSubscriptionId(),
                subscription.getClientId(),
                subscription.getModelPortfolioId(),
                subscription.getSubscribedDate(),
                subscription.getEndedDate(),
                subscription.getStatus()
        );
    }
}
