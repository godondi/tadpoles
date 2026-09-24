package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Client;
import com.neueda.leap.dto.CreateClientRequestDto;
import com.neueda.leap.dto.UpdateClientRequestDto;
import com.neueda.leap.exception.ClientNotFoundException;
import com.neueda.leap.mapper.ClientMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {
    @Mock
    private ClientMapper clientMapper;

    private ClientServiceImpl clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientServiceImpl(clientMapper);
    }

    @Test
    void getClientReturnsClientWhenFound() {
        Client client = buildClient();
        when(clientMapper.getClient(7)).thenReturn(client);

        Client result = clientService.getClient(7);

        assertEquals(7, result.getClientId());
        assertEquals("Alice Investor", result.getClientName());
    }

    @Test
    void listClientsReturnsClients() {
        when(clientMapper.listClients()).thenReturn(List.of(buildClient()));

        List<Client> results = clientService.listClients();

        assertEquals(1, results.size());
        assertEquals(7, results.get(0).getClientId());
    }

    @Test
    void createClientUsesDefaultBalanceWhenMissing() {
        CreateClientRequestDto request = new CreateClientRequestDto(
                "Alice Investor",
                3,
                5,
                1,
                null
        );

        when(clientMapper.insertClient(any())).thenAnswer(invocation -> {
            Client toInsert = invocation.getArgument(0, Client.class);
            toInsert.setClientId(7);
            return 1;
        });
        when(clientMapper.getClient(7)).thenReturn(buildClient());

        Client result = clientService.createClient(request);

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientMapper).insertClient(captor.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getCashBalance()));
        assertEquals(7, result.getClientId());
    }

    @Test
    void updateClientReturnsUpdatedClient() {
        UpdateClientRequestDto request = new UpdateClientRequestDto(
                "Alice Updated",
                null,
                null,
                new BigDecimal("2000.00")
        );
        Client updated = buildClient();
        updated.setClientName("Alice Updated");
        updated.setCashBalance(new BigDecimal("2000.00"));

        when(clientMapper.updateClient(any())).thenReturn(1);
        when(clientMapper.getClient(7)).thenReturn(updated);

        Client result = clientService.updateClient(7, request);

        assertEquals("Alice Updated", result.getClientName());
        assertEquals(0, new BigDecimal("2000.00").compareTo(result.getCashBalance()));
    }

    @Test
    void getClientBalanceReturnsBalance() {
        when(clientMapper.getClientBalance(7)).thenReturn(new BigDecimal("1200.50"));

        BigDecimal balance = clientService.getClientBalance(7);

        assertEquals(0, new BigDecimal("1200.50").compareTo(balance));
    }

    @Test
    void getClientThrowsWhenMissing() {
        when(clientMapper.getClient(99)).thenReturn(null);
        assertThrows(ClientNotFoundException.class, () -> clientService.getClient(99));
    }

    @Test
    void updateClientThrowsWhenMissing() {
        when(clientMapper.updateClient(any())).thenReturn(0);
        assertThrows(ClientNotFoundException.class,
                () -> clientService.updateClient(99, new UpdateClientRequestDto("A", null, null, null)));
    }

    @Test
    void getClientThrowsOnInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> clientService.getClient(0));
    }

    @Test
    void createClientThrowsOnBlankName() {
        CreateClientRequestDto request = new CreateClientRequestDto(" ", null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> clientService.createClient(request));
    }

    @Test
    void updateClientThrowsWhenNoFieldsProvided() {
        UpdateClientRequestDto request = new UpdateClientRequestDto(null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> clientService.updateClient(7, request));
    }

    private Client buildClient() {
        Client client = new Client();
        client.setClientId(7);
        client.setClientName("Alice Investor");
        client.setAdvisorId(3);
        client.setModelPortfolioId(5);
        client.setCashBalance(new BigDecimal("1200.50"));
        return client;
    }
}

