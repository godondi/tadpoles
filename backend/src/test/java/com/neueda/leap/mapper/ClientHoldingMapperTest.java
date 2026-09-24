package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.ClientHolding;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/schema.sql", "classpath:mapper/data.sql"})
class ClientHoldingMapperTest {
    @Autowired
    private ClientHoldingMapper clientHoldingMapper;

    @Test
    void listClientHoldingsReturnsRows() {
        List<ClientHolding> holdings = clientHoldingMapper.listClientHoldings(7);

        assertEquals(1, holdings.size());
        assertEquals(13, holdings.get(0).getHoldingId());
    }

    @Test
    void getClientHoldingReturnsHolding() {
        ClientHolding holding = clientHoldingMapper.getClientHolding(7, 13);

        assertNotNull(holding);
        assertEquals(11, holding.getInstrumentId());
        assertEquals(0, new BigDecimal("9.500000").compareTo(holding.getQuantity()));
    }

    @Test
    void insertClientHoldingCreatesNewRowWithGeneratedId() {
        ClientHolding holding = new ClientHolding();
        holding.setClientId(7);
        holding.setInstrumentId(11);
        holding.setQuantity(new BigDecimal("12.250000"));
        holding.setAsOfDate(LocalDate.of(2026, 9, 25));

        int rows = clientHoldingMapper.insertClientHolding(holding);

        assertEquals(1, rows);
        assertNotNull(holding.getHoldingId());
        ClientHolding stored = clientHoldingMapper.getClientHolding(7, holding.getHoldingId());
        assertEquals(LocalDate.of(2026, 9, 25), stored.getAsOfDate());
    }

    @Test
    void updateClientHoldingUpdatesRequestedFields() {
        ClientHolding update = new ClientHolding();
        update.setClientId(7);
        update.setHoldingId(13);
        update.setQuantity(new BigDecimal("10.250000"));
        update.setAsOfDate(LocalDate.of(2026, 9, 26));

        int rows = clientHoldingMapper.updateClientHolding(update);

        assertEquals(1, rows);
        ClientHolding stored = clientHoldingMapper.getClientHolding(7, 13);
        assertEquals(0, new BigDecimal("10.250000").compareTo(stored.getQuantity()));
        assertEquals(LocalDate.of(2026, 9, 26), stored.getAsOfDate());
    }

    @Test
    void getLatestHoldingForUpdateReturnsMostRecentHolding() {
        ClientHolding holding = clientHoldingMapper.getLatestHoldingForUpdate(7, 11);

        assertNotNull(holding);
        assertEquals(13, holding.getHoldingId());
    }

    @Test
    void deleteClientHoldingRemovesRow() {
        int rows = clientHoldingMapper.deleteClientHolding(7, 13);

        assertEquals(1, rows);
        assertEquals(0, clientHoldingMapper.listClientHoldings(7).size());
    }
}

