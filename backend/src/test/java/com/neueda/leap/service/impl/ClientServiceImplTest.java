package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Client;
import com.neueda.leap.exception.ClientNotFoundException;
import com.neueda.leap.mapper.ClientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        Client client = new Client();
        client.setClientId(7);
        client.setClientName("Alice Investor");
        client.setAdvisorId(3);
        client.setModelPortfolioId(5);

        when(clientMapper.getClient(7)).thenReturn(client);

        Client result = clientService.getClient(7);

        assertEquals(7, result.getClientId());
        assertEquals("Alice Investor", result.getClientName());
        assertEquals(3, result.getAdvisorId());
        assertEquals(5, result.getModelPortfolioId());
    }

    @Test
    void getClientThrowsWhenMissing() {
        when(clientMapper.getClient(99)).thenReturn(null);

        assertThrows(ClientNotFoundException.class, () -> clientService.getClient(99));
    }

    @Test
    void getClientThrowsOnInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> clientService.getClient(0));
    }
}


