package com.neueda.leap.service.impl;

import com.neueda.leap.domain.ClientSubscription;
import com.neueda.leap.dto.CreateClientSubscriptionRequestDto;
import com.neueda.leap.exception.ClientSubscriptionNotFoundException;
import com.neueda.leap.mapper.ClientSubscriptionMapper;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.ClientSubscriptionService;
import com.neueda.leap.service.ModelPortfolioService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ClientSubscriptionServiceImpl implements ClientSubscriptionService {
    private final ClientSubscriptionMapper clientSubscriptionMapper;
    private final ClientService clientService;
    private final ModelPortfolioService modelPortfolioService;
    private final AppUserService appUserService;

    public ClientSubscriptionServiceImpl(
            ClientSubscriptionMapper clientSubscriptionMapper,
            ClientService clientService,
            ModelPortfolioService modelPortfolioService,
            AppUserService appUserService
    ) {
        this.clientSubscriptionMapper = clientSubscriptionMapper;
        this.clientService = clientService;
        this.modelPortfolioService = modelPortfolioService;
        this.appUserService = appUserService;
    }

    @Override
    public List<ClientSubscription> listClientSubscriptions(Integer clientId) {
        validateClientId(clientId);
        clientService.getClient(clientId);
        return clientSubscriptionMapper.listClientSubscriptions(clientId);
    }

    @Override
    public ClientSubscription getClientSubscription(Integer clientId, Integer subscriptionId) {
        validateClientId(clientId);
        validateSubscriptionId(subscriptionId);
        clientService.getClient(clientId);
        ClientSubscription subscription = clientSubscriptionMapper.getClientSubscription(clientId, subscriptionId);
        if (subscription == null) {
            throw new ClientSubscriptionNotFoundException(clientId, subscriptionId);
        }
        return subscription;
    }

    @Override
    public ClientSubscription createClientSubscription(Integer clientId, CreateClientSubscriptionRequestDto request) {
        validateClientId(clientId);
        if (request == null) {
            throw new IllegalArgumentException("Subscription request is required.");
        }
        validateRequiredPortfolioId(request.modelPortfolioId());
        if (request.subscribedDate() == null) {
            throw new IllegalArgumentException("Subscription date is required.");
        }
        validateOptionalPositiveId(request.approvedByUserId(), "Approved-by user id must be a positive integer.");

        clientService.getClient(clientId);
        modelPortfolioService.getModelPortfolio(request.modelPortfolioId());
        if (request.approvedByUserId() != null) {
            appUserService.getUser(request.approvedByUserId());
        }

        ClientSubscription subscription = new ClientSubscription();
        subscription.setClientId(clientId);
        subscription.setModelPortfolioId(request.modelPortfolioId());
        subscription.setSubscribedDate(request.subscribedDate());
        subscription.setEndedDate(null);
        subscription.setStatus("ACTIVE");
        subscription.setApprovedByUserId(request.approvedByUserId());

        clientSubscriptionMapper.insertClientSubscription(subscription);
        return getClientSubscription(clientId, subscription.getSubscriptionId());
    }

    private void validateClientId(Integer clientId) {
        if (clientId == null || clientId < 1) {
            throw new IllegalArgumentException("Client id must be a positive integer.");
        }
    }

    private void validateSubscriptionId(Integer subscriptionId) {
        if (subscriptionId == null || subscriptionId < 1) {
            throw new IllegalArgumentException("Subscription id must be a positive integer.");
        }
    }

    private void validateRequiredPortfolioId(Integer modelPortfolioId) {
        if (modelPortfolioId == null || modelPortfolioId < 1) {
            throw new IllegalArgumentException("Model portfolio id must be a positive integer.");
        }
    }

    private void validateOptionalPositiveId(Integer id, String message) {
        if (id != null && id < 1) {
            throw new IllegalArgumentException(message);
        }
    }
}
