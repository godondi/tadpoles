package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.ClientHolding;
import com.neueda.leap.domain.Instrument;
import com.neueda.leap.dto.CreateClientHoldingRequestDto;
import com.neueda.leap.dto.UpdateClientHoldingRequestDto;
import com.neueda.leap.exception.ClientHoldingNotFoundException;
import com.neueda.leap.mapper.ClientHoldingMapper;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.InstrumentService;
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
class ClientHoldingServiceImplTest {
    @Mock
    private ClientHoldingMapper clientHoldingMapper;
    @Mock
    private ClientService clientService;
    @Mock
    private InstrumentService instrumentService;

    private ClientHoldingServiceImpl clientHoldingService;

    @BeforeEach
    void setUp() {
        clientHoldingService = new ClientHoldingServiceImpl(clientHoldingMapper, clientService, instrumentService);
    }

    @Test
    void listClientHoldingsReturnsHoldings() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientHoldingMapper.listClientHoldings(7)).thenReturn(List.of(buildHolding()));

        List<ClientHolding> results = clientHoldingService.listClientHoldings(7);

        assertEquals(1, results.size());
        assertEquals(13, results.get(0).getHoldingId());
    }

    @Test
    void getHoldingReturnsHoldingWhenFound() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientHoldingMapper.getClientHolding(7, 13)).thenReturn(buildHolding());

        ClientHolding result = clientHoldingService.getHolding(7, 13);

        assertEquals(13, result.getHoldingId());
        assertEquals(0, new BigDecimal("9.500000").compareTo(result.getQuantity()));
    }

    @Test
    void createHoldingReturnsInsertedHolding() {
        CreateClientHoldingRequestDto request = new CreateClientHoldingRequestDto(
                11,
                new BigDecimal("9.500000"),
                LocalDate.of(2026, 9, 24)
        );
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(instrumentService.getInstrument(11)).thenReturn(buildInstrument());
        when(clientHoldingMapper.insertClientHolding(any())).thenAnswer(invocation -> {
            ClientHolding holding = invocation.getArgument(0, ClientHolding.class);
            holding.setHoldingId(13);
            return 1;
        });
        when(clientHoldingMapper.getClientHolding(7, 13)).thenReturn(buildHolding());

        ClientHolding result = clientHoldingService.createHolding(7, request);

        ArgumentCaptor<ClientHolding> captor = ArgumentCaptor.forClass(ClientHolding.class);
        verify(clientHoldingMapper).insertClientHolding(captor.capture());
        assertEquals(7, captor.getValue().getClientId());
        assertEquals(11, captor.getValue().getInstrumentId());
        assertEquals(13, result.getHoldingId());
    }

    @Test
    void updateHoldingReturnsUpdatedHolding() {
        UpdateClientHoldingRequestDto request = new UpdateClientHoldingRequestDto(
                new BigDecimal("12.000000"),
                LocalDate.of(2026, 9, 25)
        );
        ClientHolding updated = buildHolding();
        updated.setQuantity(new BigDecimal("12.000000"));
        updated.setAsOfDate(LocalDate.of(2026, 9, 25));

        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientHoldingMapper.updateClientHolding(any())).thenReturn(1);
        when(clientHoldingMapper.getClientHolding(7, 13)).thenReturn(updated);

        ClientHolding result = clientHoldingService.updateHolding(7, 13, request);

        assertEquals(0, new BigDecimal("12.000000").compareTo(result.getQuantity()));
        assertEquals(LocalDate.of(2026, 9, 25), result.getAsOfDate());
    }

    @Test
    void getHoldingThrowsWhenMissing() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientHoldingMapper.getClientHolding(7, 99)).thenReturn(null);

        assertThrows(ClientHoldingNotFoundException.class, () -> clientHoldingService.getHolding(7, 99));
    }

    @Test
    void updateHoldingThrowsWhenMissing() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(clientHoldingMapper.updateClientHolding(any())).thenReturn(0);

        assertThrows(ClientHoldingNotFoundException.class,
                () -> clientHoldingService.updateHolding(7, 99,
                        new UpdateClientHoldingRequestDto(new BigDecimal("1.0"), null)));
    }

    @Test
    void createHoldingThrowsOnMissingAsOfDate() {
        CreateClientHoldingRequestDto request = new CreateClientHoldingRequestDto(11, new BigDecimal("5.0"), null);

        assertThrows(IllegalArgumentException.class, () -> clientHoldingService.createHolding(7, request));
    }

    @Test
    void updateHoldingThrowsWhenNoFieldsProvided() {
        UpdateClientHoldingRequestDto request = new UpdateClientHoldingRequestDto(null, null);

        assertThrows(IllegalArgumentException.class, () -> clientHoldingService.updateHolding(7, 13, request));
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

    private ClientHolding buildHolding() {
        ClientHolding holding = new ClientHolding();
        holding.setHoldingId(13);
        holding.setClientId(7);
        holding.setInstrumentId(11);
        holding.setQuantity(new BigDecimal("9.500000"));
        holding.setAsOfDate(LocalDate.of(2026, 9, 24));
        return holding;
    }
}


