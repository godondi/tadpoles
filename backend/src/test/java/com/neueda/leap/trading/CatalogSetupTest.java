package com.neueda.leap.trading;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.mapper.InstrumentPolicyMapper;
import com.neueda.leap.mapper.TradingSetupMapper;
import com.neueda.leap.service.FauxnanceQuoteService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class CatalogSetupTest {
    @Test
    void checksEveryActiveUsdInstrumentProvidedByTheCatalog() {
        var setup = mock(TradingSetupMapper.class);
        var policies = mock(InstrumentPolicyMapper.class);
        var provider = mock(FauxnanceQuoteService.class);
        var transactionManager = mock(PlatformTransactionManager.class);
        when(setup.usdCatalog())
                .thenReturn(
                        List.of(
                                Map.of(
                                        "instrumentId", 11,
                                        "ticker", "AAPL",
                                        "name", "Apple Inc.",
                                        "venue", "NASDAQ",
                                        "assetClass", "Equity",
                                        "securityType", "COMMON_STOCK"),
                                Map.of(
                                        "instrumentId", 12,
                                        "ticker", "MSFT",
                                        "name", "Microsoft Corporation",
                                        "venue", "NASDAQ",
                                        "assetClass", "Equity",
                                        "securityType", "COMMON_STOCK")));
        when(setup.version(anyInt())).thenReturn(null);
        when(provider.getQuote(anyString())).thenThrow(new IllegalStateException("provider down"));

        new CatalogSetup(
                        setup,
                        policies,
                        provider,
                        Clock.systemUTC(),
                        new ObjectMapper(),
                        transactionManager)
                .initialize();

        verify(provider).getQuote("AAPL");
        verify(provider).getQuote("MSFT");
        verify(setup, times(2)).providerCheck(any());
        verify(policies, never()).policy(any());
    }
}
