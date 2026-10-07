package com.neueda.leap.dto;

import com.neueda.leap.domain.ClientSubscription;
import java.util.List;

public record ClientSubscriptionListResponseDto(
        List<ClientSubscriptionResponseDto> subscriptions
) {
    public static ClientSubscriptionListResponseDto fromEntities(List<ClientSubscription> subscriptions) {
        return new ClientSubscriptionListResponseDto(
                subscriptions.stream().map(ClientSubscriptionResponseDto::fromEntity).toList()
        );
    }
}
