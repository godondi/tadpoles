package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.neueda.leap.dto.FillOrderRequestDto;
import com.neueda.leap.dto.OrderFillResponseDto;
import com.neueda.leap.mapper.ClientHoldingMapper;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.mapper.ClientTradeMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:orderfilldb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
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

    @Test
    void fillTradeCommitsAllUpdatesTogether() {
        OrderFillResponseDto response = orderFillService.fillTrade(
                7,
                21,
                new FillOrderRequestDto(1, new BigDecimal("80.20"), LocalDateTime.of(2026, 9, 24, 11, 0), "Executed")
        );

        assertEquals(0, new BigDecimal("799.50").compareTo(response.cashBalance()));
        assertEquals("EXECUTED", clientTradeMapper.getClientTrade(7, 21).getStatus());
        assertEquals(0, new BigDecimal("9.500000")
                .compareTo(clientHoldingMapper.getLatestHoldingForUpdate(7, 11).getQuantity()));
        assertEquals(0, new BigDecimal("799.50").compareTo(clientMapper.getClientBalance(7)));
    }

    @Test
    void fillTradeRollsBackWhenTradeRecordUpdateFails() {
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
}

