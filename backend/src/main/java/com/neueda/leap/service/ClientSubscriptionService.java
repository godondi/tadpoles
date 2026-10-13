package com.neueda.leap.service;

import com.neueda.leap.domain.ClientSubscription;
import com.neueda.leap.dto.CreateClientSubscriptionRequestDto;
import java.util.List;

public interface ClientSubscriptionService {
    List<ClientSubscription> listClientSubscriptions(Integer clientId);
    ClientSubscription getClientSubscription(Integer clientId, Integer subscriptionId);
    ClientSubscription createClientSubscription(Integer clientId, CreateClientSubscriptionRequestDto request);
}
