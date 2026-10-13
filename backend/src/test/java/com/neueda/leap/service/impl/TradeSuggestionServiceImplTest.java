package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Advisor;
import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.Instrument;
import com.neueda.leap.domain.TradeSuggestion;
import com.neueda.leap.dto.CreateTradeSuggestionRequestDto;
import com.neueda.leap.dto.UpdateTradeSuggestionRequestDto;
import com.neueda.leap.exception.TradeSuggestionNotFoundException;
import com.neueda.leap.mapper.TradeSuggestionMapper;
import com.neueda.leap.service.AdvisorService;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.InstrumentService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeSuggestionServiceImplTest {
    @Mock
    private TradeSuggestionMapper tradeSuggestionMapper;
    @Mock
    private AdvisorService advisorService;
    @Mock
    private ClientService clientService;
    @Mock
    private InstrumentService instrumentService;

    private TradeSuggestionServiceImpl tradeSuggestionService;

    @BeforeEach
    void setUp() {
        tradeSuggestionService = new TradeSuggestionServiceImpl(
                tradeSuggestionMapper,
                advisorService,
                clientService,
                instrumentService
        );
    }

    @Test
    void listClientTradeSuggestionsReturnsSuggestions() {
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(tradeSuggestionMapper.listClientTradeSuggestions(7)).thenReturn(List.of(buildSuggestion()));

        List<TradeSuggestion> results = tradeSuggestionService.listClientTradeSuggestions(7);

        assertEquals(1, results.size());
        assertEquals(15, results.get(0).getSuggestionId());
    }

    @Test
    void getTradeSuggestionReturnsSuggestionWhenFound() {
        when(tradeSuggestionMapper.getTradeSuggestion(15)).thenReturn(buildSuggestion());

        TradeSuggestion result = tradeSuggestionService.getTradeSuggestion(15);

        assertEquals("BUY", result.getTradeType());
        assertEquals("SUGGESTED", result.getStatus());
    }

    @Test
    void createTradeSuggestionNormalizesInputAndReturnsCreatedSuggestion() {
        CreateTradeSuggestionRequestDto request = new CreateTradeSuggestionRequestDto(
                11,
                " buy ",
                new BigDecimal("5.500000"),
                new BigDecimal("189.25"),
                "  Increase technology exposure  "
        );

        when(advisorService.getAdvisor(3)).thenReturn(buildAdvisor());
        when(clientService.getClient(7)).thenReturn(buildClient());
        when(instrumentService.getInstrument(11)).thenReturn(buildInstrument());
        when(tradeSuggestionMapper.insertTradeSuggestion(any())).thenAnswer(invocation -> {
            TradeSuggestion suggestion = invocation.getArgument(0, TradeSuggestion.class);
            suggestion.setSuggestionId(15);
            return 1;
        });
        when(tradeSuggestionMapper.getTradeSuggestion(15)).thenReturn(buildSuggestion());

        TradeSuggestion result = tradeSuggestionService.createTradeSuggestion(3, 7, request);

        ArgumentCaptor<TradeSuggestion> captor = ArgumentCaptor.forClass(TradeSuggestion.class);
        verify(tradeSuggestionMapper).insertTradeSuggestion(captor.capture());
        assertEquals("BUY", captor.getValue().getTradeType());
        assertEquals("Increase technology exposure", captor.getValue().getNotes());
        assertEquals("SUGGESTED", captor.getValue().getStatus());
        assertEquals(15, result.getSuggestionId());
    }

    @Test
    void createTradeSuggestionThrowsWhenClientBelongsToDifferentAdvisor() {
        Client client = buildClient();
        client.setAdvisorId(9);
        when(advisorService.getAdvisor(3)).thenReturn(buildAdvisor());
        when(clientService.getClient(7)).thenReturn(client);

        CreateTradeSuggestionRequestDto request = new CreateTradeSuggestionRequestDto(
                11,
                "BUY",
                new BigDecimal("5.5"),
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> tradeSuggestionService.createTradeSuggestion(3, 7, request));
    }

    @Test
    void updateTradeSuggestionReturnsUpdatedSuggestion() {
        UpdateTradeSuggestionRequestDto request = new UpdateTradeSuggestionRequestDto("accepted", "  Client approved  ");
        TradeSuggestion updated = buildSuggestion();
        updated.setStatus("ACCEPTED");
        updated.setNotes("Client approved");

        when(tradeSuggestionMapper.updateTradeSuggestion(any())).thenReturn(1);
        when(tradeSuggestionMapper.getTradeSuggestion(15)).thenReturn(updated);

        TradeSuggestion result = tradeSuggestionService.updateTradeSuggestion(15, request);

        assertEquals("ACCEPTED", result.getStatus());
        assertEquals("Client approved", result.getNotes());
    }

    @Test
    void getTradeSuggestionThrowsWhenMissing() {
        when(tradeSuggestionMapper.getTradeSuggestion(99)).thenReturn(null);

        assertThrows(TradeSuggestionNotFoundException.class, () -> tradeSuggestionService.getTradeSuggestion(99));
    }

    @Test
    void updateTradeSuggestionThrowsWhenNoFieldsProvided() {
        UpdateTradeSuggestionRequestDto request = new UpdateTradeSuggestionRequestDto(null, " ");

        assertThrows(IllegalArgumentException.class, () -> tradeSuggestionService.updateTradeSuggestion(15, request));
    }

    private Advisor buildAdvisor() {
        Advisor advisor = new Advisor();
        advisor.setAdvisorId(3);
        advisor.setAdvisorName("Advisor One");
        advisor.setUserId(4);
        return advisor;
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

    private Instrument buildInstrument() {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(11);
        instrument.setInstrumentName("Apple Inc");
        instrument.setTicker("AAPL");
        instrument.setCurrency("USD");
        return instrument;
    }

    private TradeSuggestion buildSuggestion() {
        TradeSuggestion suggestion = new TradeSuggestion();
        suggestion.setSuggestionId(15);
        suggestion.setAdvisorId(3);
        suggestion.setClientId(7);
        suggestion.setInstrumentId(11);
        suggestion.setTradeType("BUY");
        suggestion.setQuantity(new BigDecimal("5.500000"));
        suggestion.setProposedPrice(new BigDecimal("189.25"));
        suggestion.setSuggestedAt(LocalDateTime.of(2026, 9, 24, 11, 0));
        suggestion.setStatus("SUGGESTED");
        suggestion.setNotes("Increase technology exposure");
        return suggestion;
    }
}

