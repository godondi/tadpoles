package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.domain.Instrument;
import com.neueda.leap.dto.CreateClientTradeRequestDto;
import com.neueda.leap.dto.UpdateClientTradeRequestDto;
import com.neueda.leap.exception.ClientTradeNotFoundException;
import com.neueda.leap.mapper.ClientTradeMapper;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.InstrumentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientTradeServiceImplTest {
    @Mock
    private ClientTradeMapper clientTradeMapper;
    @Mock
    private ClientService clientService;
    @Mock
    private InstrumentService instrumentService;

    private ClientTradeServiceImpl clientTradeService;

    @BeforeEach
    void setUp() {
        clientTradeService = new ClientTradeServiceImpl(clientTradeMapper, clientService, instrumentService);
    }

    @Test
    void listClientTradesReturnsTrades() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientTradeMapper.listClientTrades(7)).thenReturn(List.of(buildTrade()));

        List<ClientTrade> results = clientTradeService.listClientTrades(7);

        assertEquals(1, results.size());
        assertEquals(21, results.get(0).getTradeId());
    }

    @Test
    void getTradeReturnsTradeWhenFound() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientTradeMapper.getClientTrade(7, 21)).thenReturn(buildTrade());

        ClientTrade result = clientTradeService.getTrade(7, 21);

        assertEquals(21, result.getTradeId());
        assertEquals("APPROVED", result.getStatus());
    }

    @Test
    void createTradeDefaultsStatusToPending() {
        CreateClientTradeRequestDto request = new CreateClientTradeRequestDto(
                11,
                14,
                null,
                "BUY",
                new BigDecimal("2.500000"),
                new BigDecimal("110.25"),
                LocalDate.of(2026, 9, 24),
                null,
                "Add position"
        );
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(instrumentService.getInstrument(11)).thenReturn(buildInstrument());
        when(clientTradeMapper.insertClientTrade(any())).thenAnswer(invocation -> {
            ClientTrade trade = invocation.getArgument(0, ClientTrade.class);
            trade.setTradeId(21);
            return 1;
        });
        when(clientTradeMapper.getClientTrade(7, 21)).thenReturn(buildTrade());

        ClientTrade result = clientTradeService.createTrade(7, request);

        ArgumentCaptor<ClientTrade> captor = ArgumentCaptor.forClass(ClientTrade.class);
        verify(clientTradeMapper).insertClientTrade(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals(21, result.getTradeId());
    }

    @Test
    void updateTradeReturnsUpdatedTrade() {
        UpdateClientTradeRequestDto request = new UpdateClientTradeRequestDto(
                null,
                1,
                "APPROVED",
                null,
                "Approved by desk"
        );
        ClientTrade updated = buildTrade();
        updated.setApprovedByUserId(1);
        updated.setStatus("APPROVED");
        updated.setReason("Approved by desk");

        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientTradeMapper.updateClientTrade(any())).thenReturn(1);
        when(clientTradeMapper.getClientTrade(7, 21)).thenReturn(updated);

        ClientTrade result = clientTradeService.updateTrade(7, 21, request);

        assertEquals("APPROVED", result.getStatus());
        assertEquals(1, result.getApprovedByUserId());
    }

    @Test
    void getTradeThrowsWhenMissing() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientTradeMapper.getClientTrade(7, 99)).thenReturn(null);

        assertThrows(ClientTradeNotFoundException.class, () -> clientTradeService.getTrade(7, 99));
    }

    @Test
    void updateTradeThrowsWhenMissing() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientTradeMapper.updateClientTrade(any())).thenReturn(0);

        assertThrows(ClientTradeNotFoundException.class,
                () -> clientTradeService.updateTrade(7, 99,
                        new UpdateClientTradeRequestDto(null, null, "APPROVED", null, null)));
    }

    @Test
    void createTradeThrowsOnInvalidTradeType() {
        CreateClientTradeRequestDto request = new CreateClientTradeRequestDto(
                11,
                14,
                null,
                "HOLD",
                new BigDecimal("2.5"),
                new BigDecimal("110.25"),
                LocalDate.of(2026, 9, 24),
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> clientTradeService.createTrade(7, request));
    }

    @Test
    void updateTradeThrowsWhenNoFieldsProvided() {
        UpdateClientTradeRequestDto request = new UpdateClientTradeRequestDto(null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> clientTradeService.updateTrade(7, 21, request));
    }

    private Client buildClient() {
        Client client = new Client();
        client.setClientId(7);
        client.setClientName("Alice Investor");
        return client;
    }

    private Instrument buildInstrument() {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(11);
        instrument.setTicker("AAPL");
        instrument.setIsActive(true);
        return instrument;
    }

    private ClientTrade buildTrade() {
        ClientTrade trade = new ClientTrade();
        trade.setTradeId(21);
        trade.setClientId(7);
        trade.setInstrumentId(11);
        trade.setSubmittedByUserId(14);
        trade.setApprovedByUserId(1);
        trade.setTradeType("BUY");
        trade.setQuantity(new BigDecimal("2.500000"));
        trade.setPrice(new BigDecimal("110.25"));
        trade.setTradeDate(LocalDate.of(2026, 9, 24));
        trade.setStatus("APPROVED");
        trade.setExecutedAt(LocalDateTime.of(2026, 9, 24, 10, 15));
        trade.setReason("Add position");
        return trade;
    }
}


