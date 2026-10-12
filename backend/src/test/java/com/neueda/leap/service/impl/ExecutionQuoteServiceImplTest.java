package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.FauxnanceQuote;
import com.neueda.leap.domain.FauxnanceQuoteMeta;
import com.neueda.leap.domain.FauxnanceQuoteResponse;
import com.neueda.leap.domain.Instrument;
import com.neueda.leap.service.FauxnanceQuoteService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionQuoteServiceImplTest {
    @Mock
    private FauxnanceQuoteService fauxnanceQuoteService;

    private ExecutionQuoteServiceImpl executionQuoteService;

    @BeforeEach
    void setUp() {
        executionQuoteService = new ExecutionQuoteServiceImpl(fauxnanceQuoteService);
    }

    @Test
    void getExecutionQuoteSupportsEquities() {
        Instrument instrument = buildInstrument("AAPL", "Equity", "Stock");
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("AAPL"));

        ExecutionQuote result = executionQuoteService.getExecutionQuote(instrument);

        assertEquals("EQUITY", result.assetClass());
        verify(fauxnanceQuoteService).getQuote("AAPL");
    }

    @Test
    void getExecutionQuoteSupportsForeignExchange() {
        Instrument instrument = buildInstrument("USDINR", "FX", "Currency Pair");
        when(fauxnanceQuoteService.getQuote("USDINR")).thenReturn(buildQuoteResponse("USDINR"));

        ExecutionQuote result = executionQuoteService.getExecutionQuote(instrument);

        assertEquals("FX", result.assetClass());
        verify(fauxnanceQuoteService).getQuote("USDINR");
    }

    @Test
    void getExecutionQuoteSupportsCrypto() {
        Instrument instrument = buildInstrument("BTCUSD", "Crypto", "Token");
        when(fauxnanceQuoteService.getQuote("BTCUSD")).thenReturn(buildQuoteResponse("BTCUSD"));

        ExecutionQuote result = executionQuoteService.getExecutionQuote(instrument);

        assertEquals("CRYPTO", result.assetClass());
        verify(fauxnanceQuoteService).getQuote("BTCUSD");
    }

    @Test
    void getExecutionQuoteRejectsUnsupportedAssetClass() {
        Instrument instrument = buildInstrument("BOND1", "Fixed Income", "Bond");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> executionQuoteService.getExecutionQuote(instrument)
        );

        assertEquals("Instrument asset class is not supported for launch pricing.", exception.getMessage());
    }

    @Test
    void getExecutionQuoteRejectsStaleQuote() {
        Instrument instrument = buildInstrument("AAPL", "Equity", "Stock");
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("AAPL", true));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> executionQuoteService.getExecutionQuote(instrument)
        );

        assertEquals("Current market quote is unavailable for execution.", exception.getMessage());
    }

    private Instrument buildInstrument(String ticker, String assetClass, String securityType) {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(11);
        instrument.setTicker(ticker);
        instrument.setAssetClass(assetClass);
        instrument.setSecurityType(securityType);
        instrument.setIsActive(true);
        return instrument;
    }

    private FauxnanceQuoteResponse buildQuoteResponse(String symbol) {
        return buildQuoteResponse(symbol, false);
    }

    private FauxnanceQuoteResponse buildQuoteResponse(String symbol, boolean stale) {
        FauxnanceQuote quote = new FauxnanceQuote();
        quote.setSymbol(symbol);
        quote.setBid(new BigDecimal("99.90"));
        quote.setAsk(new BigDecimal("100.10"));

        FauxnanceQuoteMeta meta = new FauxnanceQuoteMeta();
        meta.setSymbol(symbol);
        meta.setAsOf(OffsetDateTime.parse("2026-10-09T10:30:00Z"));
        meta.setStale(stale);

        FauxnanceQuoteResponse response = new FauxnanceQuoteResponse();
        response.setData(quote);
        response.setMeta(meta);
        return response;
    }
}
