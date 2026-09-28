package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.Instrument;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/schema.sql", "classpath:mapper/data.sql"})
class InstrumentMapperTest {
    @Autowired
    private InstrumentMapper instrumentMapper;

    @Test
    void getInstrumentReturnsInstrument() {
        Instrument instrument = instrumentMapper.getInstrument(11);

        assertNotNull(instrument);
        assertEquals("AAPL", instrument.getTicker());
    }

    @Test
    void listInstrumentsReturnsRows() {
        List<Instrument> instruments = instrumentMapper.listInstruments();
        assertEquals(1, instruments.size());
    }

    @Test
    void insertInstrumentCreatesNewRowWithGeneratedId() {
        Instrument instrument = new Instrument();
        instrument.setInstrumentName("Microsoft");
        instrument.setTicker("MSFT");
        instrument.setCurrency("USD");
        instrument.setAssetClass("Equity");
        instrument.setSecurityType("Stock");
        instrument.setIsActive(true);

        int rows = instrumentMapper.insertInstrument(instrument);

        assertEquals(1, rows);
        assertNotNull(instrument.getInstrumentId());
        Instrument stored = instrumentMapper.getInstrument(instrument.getInstrumentId());
        assertEquals("MSFT", stored.getTicker());
    }

    @Test
    void updateInstrumentUpdatesRequestedFields() {
        Instrument update = new Instrument();
        update.setInstrumentId(11);
        update.setCurrency("EUR");
        update.setIsActive(false);

        int rows = instrumentMapper.updateInstrument(update);

        assertEquals(1, rows);
        Instrument stored = instrumentMapper.getInstrument(11);
        assertEquals("EUR", stored.getCurrency());
        assertEquals(false, stored.getIsActive());
    }
}

