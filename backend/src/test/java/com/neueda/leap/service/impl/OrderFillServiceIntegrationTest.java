package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.FauxnanceQuote;
import com.neueda.leap.domain.FauxnanceQuoteMeta;
import com.neueda.leap.domain.FauxnanceQuoteResponse;
import com.neueda.leap.dto.FillOrderRequestDto;
import com.neueda.leap.dto.OrderFillResponseDto;
import com.neueda.leap.mapper.ClientHoldingMapper;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.mapper.ClientTradeMapper;
import com.neueda.leap.service.FauxnanceQuoteService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:orderfilldb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "server.port=0",
        "jwt.secret=test-jwt-secret-test-jwt-secret-123456",
        "fauxnance.base-url=http://localhost",
        "fauxnance.api-key=test-api-key",
        "fauxnance.default-interval=1d",
        "fauxnance.connect-timeout-seconds=10",
        "fauxnance.read-timeout-seconds=20",
        "spring.sql.init.mode=never"
})
@Sql(scripts = "/orderfill/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/orderfill/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderFillServiceIntegrationTest {
    @Autowired
    private OrderFillServiceImpl orderFillService;
    @Autowired
    private ClientMapper clientMapper;
    @Autowired
    private ClientHoldingMapper clientHoldingMapper;
    @Autowired
    private ClientTradeMapper clientTradeMapper;
    @MockBean
    private FauxnanceQuoteService fauxnanceQuoteService;

    @Test
    void fillTradeCommitsAllUpdatesTogether() {
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("80.00", "80.10"));

        OrderFillResponseDto response = orderFillService.fillTrade(
                7,
                21,
                new FillOrderRequestDto(1, new BigDecimal("80.20"), LocalDateTime.of(2026, 9, 24, 11, 0), "Executed")
        );

        assertEquals(0, new BigDecimal("799.75").compareTo(response.cashBalance()));
        assertEquals("EXECUTED", clientTradeMapper.getClientTrade(7, 21).getStatus());
        assertEquals(0, new BigDecimal("9.500000")
                .compareTo(clientHoldingMapper.getLatestHoldingForUpdate(7, 11).getQuantity()));
        assertEquals(0, new BigDecimal("799.75").compareTo(clientMapper.getClientBalance(7)));
    }

    @Test
    void fillTradeRollsBackWhenTradeRecordUpdateFails() {
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(buildQuoteResponse("80.00", "80.10"));

        assertThrows(Exception.class, () -> orderFillService.fillTrade(
                7,
                21,
                new FillOrderRequestDto(999, new BigDecimal("80.20"), LocalDateTime.of(2026, 9, 24, 11, 0), "Executed")
        ));

        assertEquals(0, new BigDecimal("1000.00").compareTo(clientMapper.getClientBalance(7)));
        assertEquals("APPROVED", clientTradeMapper.getClientTrade(7, 21).getStatus());
        assertEquals(0, new BigDecimal("7.000000")
                .compareTo(clientHoldingMapper.getLatestHoldingForUpdate(7, 11).getQuantity()));
    }

    private FauxnanceQuoteResponse buildQuoteResponse(String bid, String ask) {
        FauxnanceQuote quote = new FauxnanceQuote();
        quote.setSymbol("AAPL");
        quote.setBid(new BigDecimal(bid));
        quote.setAsk(new BigDecimal(ask));

        FauxnanceQuoteMeta meta = new FauxnanceQuoteMeta();
        meta.setSymbol("AAPL");
        meta.setAsOf(OffsetDateTime.parse("2026-10-08T15:59:00Z"));
        meta.setStale(false);

        FauxnanceQuoteResponse response = new FauxnanceQuoteResponse();
        response.setData(quote);
        response.setMeta(meta);
        return response;
    }
}
