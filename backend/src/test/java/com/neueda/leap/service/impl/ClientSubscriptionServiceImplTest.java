package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.ClientSubscription;
import com.neueda.leap.domain.ModelPortfolio;
import com.neueda.leap.dto.CreateClientSubscriptionRequestDto;
import com.neueda.leap.exception.ClientSubscriptionNotFoundException;
import com.neueda.leap.mapper.ClientSubscriptionMapper;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.ModelPortfolioService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientSubscriptionServiceImplTest {
    @Mock
    private ClientSubscriptionMapper clientSubscriptionMapper;
    @Mock
    private ClientService clientService;
    @Mock
    private ModelPortfolioService modelPortfolioService;
    @Mock
    private AppUserService appUserService;

    private ClientSubscriptionServiceImpl clientSubscriptionService;

    @BeforeEach
    void setUp() {
        clientSubscriptionService = new ClientSubscriptionServiceImpl(
                clientSubscriptionMapper,
                clientService,
                modelPortfolioService,
                appUserService
        );
    }

    @Test
    void listClientSubscriptionsReturnsSubscriptions() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientSubscriptionMapper.listClientSubscriptions(7)).thenReturn(List.of(buildSubscription()));

        List<ClientSubscription> results = clientSubscriptionService.listClientSubscriptions(7);

        assertEquals(1, results.size());
        assertEquals(9, results.get(0).getSubscriptionId());
    }

    @Test
    void getClientSubscriptionReturnsSubscriptionWhenFound() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientSubscriptionMapper.getClientSubscription(7, 9)).thenReturn(buildSubscription());

        ClientSubscription result = clientSubscriptionService.getClientSubscription(7, 9);

        assertEquals(9, result.getSubscriptionId());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void createClientSubscriptionDefaultsStatusAndEndedDate() {
        CreateClientSubscriptionRequestDto request = new CreateClientSubscriptionRequestDto(
                5,
                LocalDate.of(2026, 9, 24),
                1
        );
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(modelPortfolioService.getModelPortfolio(5)).thenReturn(buildPortfolio());
        when(appUserService.getUser(1)).thenReturn(buildUser());
        when(clientSubscriptionMapper.insertClientSubscription(any())).thenAnswer(invocation -> {
            ClientSubscription subscription = invocation.getArgument(0, ClientSubscription.class);
            subscription.setSubscriptionId(9);
            return 1;
        });
        when(clientSubscriptionMapper.getClientSubscription(7, 9)).thenReturn(buildSubscription());

        ClientSubscription result = clientSubscriptionService.createClientSubscription(7, request);

        ArgumentCaptor<ClientSubscription> captor = ArgumentCaptor.forClass(ClientSubscription.class);
        verify(clientSubscriptionMapper).insertClientSubscription(captor.capture());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertNull(captor.getValue().getEndedDate());
        assertEquals(9, result.getSubscriptionId());
    }

    @Test
    void getClientSubscriptionThrowsWhenMissing() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientSubscriptionMapper.getClientSubscription(7, 99)).thenReturn(null);

        assertThrows(ClientSubscriptionNotFoundException.class,
                () -> clientSubscriptionService.getClientSubscription(7, 99));
    }

    @Test
    void createClientSubscriptionThrowsOnMissingSubscribedDate() {
        CreateClientSubscriptionRequestDto request = new CreateClientSubscriptionRequestDto(5, null, 1);

        assertThrows(IllegalArgumentException.class, () -> clientSubscriptionService.createClientSubscription(7, request));
    }

    @Test
    void createClientSubscriptionThrowsOnInvalidApproverId() {
        CreateClientSubscriptionRequestDto request = new CreateClientSubscriptionRequestDto(
                5,
                LocalDate.of(2026, 9, 24),
                0
        );

        assertThrows(IllegalArgumentException.class, () -> clientSubscriptionService.createClientSubscription(7, request));
    }

    private Client buildClient() {
        Client client = new Client();
        client.setClientId(7);
        client.setClientName("Alice Investor");
        client.setCashBalance(new BigDecimal("1200.50"));
        return client;
    }

    private ModelPortfolio buildPortfolio() {
        ModelPortfolio portfolio = new ModelPortfolio();
        portfolio.setModelPortfolioId(5);
        portfolio.setModelName("Growth");
        portfolio.setIsActive(true);
        return portfolio;
    }

    private AppUser buildUser() {
        AppUser user = new AppUser();
        user.setUserId(1);
        user.setUsername("admin01");
        return user;
    }

    private ClientSubscription buildSubscription() {
        ClientSubscription subscription = new ClientSubscription();
        subscription.setSubscriptionId(9);
        subscription.setClientId(7);
        subscription.setModelPortfolioId(5);
        subscription.setSubscribedDate(LocalDate.of(2026, 9, 24));
        subscription.setStatus("ACTIVE");
        subscription.setApprovedByUserId(1);
        return subscription;
    }
}
