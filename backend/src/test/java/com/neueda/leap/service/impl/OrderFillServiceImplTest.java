package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.ClientHolding;
import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.domain.FauxnanceQuote;
import com.neueda.leap.domain.FauxnanceQuoteMeta;
import com.neueda.leap.domain.FauxnanceQuoteResponse;
import com.neueda.leap.domain.Instrument;
import com.neueda.leap.dto.FillOrderRequestDto;
import com.neueda.leap.dto.OrderFillResponseDto;
import com.neueda.leap.exception.ClientTradeNotFoundException;
import com.neueda.leap.mapper.ClientHoldingMapper;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.mapper.ClientTradeMapper;
import com.neueda.leap.mapper.InstrumentMapper;
import com.neueda.leap.service.FauxnanceQuoteService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderFillServiceImplTest {
    @Mock
    private ClientMapper clientMapper;
    @Mock
    private ClientTradeMapper clientTradeMapper;
    @Mock
    private ClientHoldingMapper clientHoldingMapper;
    @Mock
    private InstrumentMapper instrumentMapper;
    @Mock
    private FauxnanceQuoteService fauxnanceQuoteService;

    private OrderFillServiceImpl orderFillService;

    @BeforeEach
    void setUp() {
        orderFillService = new OrderFillServiceImpl(
                clientMapper,
                clientTradeMapper,
                clientHoldingMapper,
                instrumentMapper,
                fauxnanceQuoteService
        );
    }

    @Test
    void fillBuyTradeUpdatesBalanceHoldingAndTrade() {
        ClientTrade trade = buildTrade("BUY");
        Client client = buildClient("1000.00");
        ClientHolding holding = buildHolding("7.000000");
        Instrument instrument = buildInstrument(true);
        FillOrderRequestDto request = new FillOrderRequestDto(1, new BigDecimal("80.20"),
                LocalDateTime.of(2026, 9, 24, 11, 0), "Filled at market");

        when(clientTradeMapper.getTradeForUpdate(21)).thenReturn(trade);
        when(clientMapper.getClientForUpdate(7)).thenReturn(client);
        when(instrumentMapper.getInstrument(11)).thenReturn(instrument);
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("80.00", "80.10", false));
        when(clientHoldingMapper.getLatestHoldingForUpdate(7, 11)).thenReturn(holding);
        when(clientHoldingMapper.updateClientHolding(any())).thenReturn(1);
        when(clientMapper.updateClientBalance(eq(7), eq(new BigDecimal("799.75")))).thenReturn(1);
        when(clientTradeMapper.markTradeExecuted(eq(21), eq(1), eq(new BigDecimal("80.10")), any(), eq("Filled at market")))
                .thenReturn(1);

        OrderFillResponseDto result = orderFillService.fillTrade(7, 21, request);

        ArgumentCaptor<ClientHolding> holdingCaptor = ArgumentCaptor.forClass(ClientHolding.class);
        verify(clientHoldingMapper).updateClientHolding(holdingCaptor.capture());
        assertEquals(0, new BigDecimal("9.500000").compareTo(holdingCaptor.getValue().getQuantity()));
        assertEquals(0, new BigDecimal("799.75").compareTo(result.cashBalance()));
        assertEquals("EXECUTED", result.tradeStatus());
    }

    @Test
    void fillSellTradeUpdatesBalanceAndHolding() {
        ClientTrade trade = buildTrade("SELL");
        Client client = buildClient("1000.00");
        ClientHolding holding = buildHolding("7.000000");
        Instrument instrument = buildInstrument(true);
        FillOrderRequestDto request = new FillOrderRequestDto(1, null,
                LocalDateTime.of(2026, 9, 24, 11, 0), "Sold to rebalance");

        when(clientTradeMapper.getTradeForUpdate(21)).thenReturn(trade);
        when(clientMapper.getClientForUpdate(7)).thenReturn(client);
        when(instrumentMapper.getInstrument(11)).thenReturn(instrument);
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("80.30", "80.50", false));
        when(clientHoldingMapper.getLatestHoldingForUpdate(7, 11)).thenReturn(holding);
        when(clientHoldingMapper.updateClientHolding(any())).thenReturn(1);
        when(clientMapper.updateClientBalance(eq(7), eq(new BigDecimal("1200.75")))).thenReturn(1);
        when(clientTradeMapper.markTradeExecuted(eq(21), eq(1), eq(new BigDecimal("80.30")), any(), eq("Sold to rebalance")))
                .thenReturn(1);

        OrderFillResponseDto result = orderFillService.fillTrade(7, 21, request);

        assertEquals(0, new BigDecimal("1200.75").compareTo(result.cashBalance()));
        assertEquals(0, new BigDecimal("4.500000").compareTo(result.holdingQuantity()));
    }

    @Test
    void fillTradeThrowsWhenTradeMissing() {
        when(clientTradeMapper.getTradeForUpdate(21)).thenReturn(null);

        assertThrows(ClientTradeNotFoundException.class,
                () -> orderFillService.fillTrade(7, 21, new FillOrderRequestDto(null, null, null, null)));
    }

    @Test
    void fillTradeThrowsWhenCashIsInsufficient() {
        ClientTrade trade = buildTrade("BUY");
        Client client = buildClient("50.00");
        when(clientTradeMapper.getTradeForUpdate(21)).thenReturn(trade);
        when(clientMapper.getClientForUpdate(7)).thenReturn(client);
        when(instrumentMapper.getInstrument(11)).thenReturn(buildInstrument(true));
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("80.10", "80.30", false));

        assertThrows(IllegalArgumentException.class,
                () -> orderFillService.fillTrade(7, 21, new FillOrderRequestDto(null, null, null, null)));
    }

    @Test
    void fillTradeThrowsWhenHoldingIsInsufficientForSell() {
        ClientTrade trade = buildTrade("SELL");
        Client client = buildClient("1000.00");
        ClientHolding holding = buildHolding("1.000000");
        when(clientTradeMapper.getTradeForUpdate(21)).thenReturn(trade);
        when(clientMapper.getClientForUpdate(7)).thenReturn(client);
        when(instrumentMapper.getInstrument(11)).thenReturn(buildInstrument(true));
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("80.30", "80.50", false));
        when(clientHoldingMapper.getLatestHoldingForUpdate(7, 11)).thenReturn(holding);

        assertThrows(IllegalArgumentException.class,
                () -> orderFillService.fillTrade(7, 21, new FillOrderRequestDto(null, null, null, null)));
    }

    @Test
    void fillTradeRejectsBuyWhenCurrentAskExceedsLimit() {
        ClientTrade trade = buildTrade("BUY");
        when(clientTradeMapper.getTradeForUpdate(21)).thenReturn(trade);
        when(clientMapper.getClientForUpdate(7)).thenReturn(buildClient("1000.00"));
        when(instrumentMapper.getInstrument(11)).thenReturn(buildInstrument(true));
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("80.30", "80.40", false));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderFillService.fillTrade(7, 21, new FillOrderRequestDto(null, null, null, null))
        );

        assertEquals("Current ask price exceeds the order limit.", exception.getMessage());
    }

    @Test
    void fillTradeRejectsSellWhenCurrentBidIsBelowLimit() {
        ClientTrade trade = buildTrade("SELL");
        when(clientTradeMapper.getTradeForUpdate(21)).thenReturn(trade);
        when(clientMapper.getClientForUpdate(7)).thenReturn(buildClient("1000.00"));
        when(instrumentMapper.getInstrument(11)).thenReturn(buildInstrument(true));
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("80.10", "80.30", false));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderFillService.fillTrade(7, 21, new FillOrderRequestDto(null, null, null, null))
        );

        assertEquals("Current bid price is below the order limit.", exception.getMessage());
    }

    @Test
    void fillTradeRejectsWhenCurrentQuoteIsUnavailable() {
        ClientTrade trade = buildTrade("BUY");
        when(clientTradeMapper.getTradeForUpdate(21)).thenReturn(trade);
        when(clientMapper.getClientForUpdate(7)).thenReturn(buildClient("1000.00"));
        when(instrumentMapper.getInstrument(11)).thenReturn(buildInstrument(true));
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("80.00", null, false));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderFillService.fillTrade(7, 21, new FillOrderRequestDto(null, null, null, null))
        );

        assertEquals("Current ask quote is unavailable for execution.", exception.getMessage());
    }

    @Test
    void fillTradeThrowsWhenTradeCannotBeExecuted() {
        ClientTrade trade = buildTrade("BUY");
        trade.setStatus("EXECUTED");
        when(clientTradeMapper.getTradeForUpdate(21)).thenReturn(trade);

        assertThrows(IllegalArgumentException.class,
                () -> orderFillService.fillTrade(7, 21, new FillOrderRequestDto(null, null, null, null)));
    }

    private Client buildClient(String cashBalance) {
        Client client = new Client();
        client.setClientId(7);
        client.setCashBalance(new BigDecimal(cashBalance));
        return client;
    }

    private ClientTrade buildTrade(String tradeType) {
        ClientTrade trade = new ClientTrade();
        trade.setTradeId(21);
        trade.setClientId(7);
        trade.setInstrumentId(11);
        trade.setTradeType(tradeType);
        trade.setQuantity(new BigDecimal("2.500000"));
        trade.setPrice(new BigDecimal("80.20"));
        trade.setTradeDate(LocalDate.of(2026, 9, 24));
        trade.setStatus("APPROVED");
        return trade;
    }

    private ClientHolding buildHolding(String quantity) {
        ClientHolding holding = new ClientHolding();
        holding.setHoldingId(13);
        holding.setClientId(7);
        holding.setInstrumentId(11);
        holding.setQuantity(new BigDecimal(quantity));
        holding.setAsOfDate(LocalDate.of(2026, 9, 23));
        return holding;
    }

    private Instrument buildInstrument(boolean active) {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(11);
        instrument.setTicker("AAPL");
        instrument.setIsActive(active);
        return instrument;
    }

    private FauxnanceQuoteResponse buildQuoteResponse(String bid, String ask, boolean stale) {
        FauxnanceQuote quote = new FauxnanceQuote();
        quote.setSymbol("AAPL");
        quote.setBid(bid == null ? null : new BigDecimal(bid));
        quote.setAsk(ask == null ? null : new BigDecimal(ask));

        FauxnanceQuoteMeta meta = new FauxnanceQuoteMeta();
        meta.setSymbol("AAPL");
        meta.setAsOf(OffsetDateTime.parse("2026-10-08T15:59:00Z"));
        meta.setStale(stale);

        FauxnanceQuoteResponse response = new FauxnanceQuoteResponse();
        response.setData(quote);
        response.setMeta(meta);
        return response;
    }
}
