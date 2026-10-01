package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.ModelPortfolioHolding;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/schema.sql", "classpath:mapper/model_portfolio_data.sql"})
class ModelPortfolioHoldingMapperTest {
    @Autowired
    private ModelPortfolioHoldingMapper modelPortfolioHoldingMapper;

    @Test
    void listModelPortfolioHoldingsReturnsRows() {
        List<ModelPortfolioHolding> holdings = modelPortfolioHoldingMapper.listModelPortfolioHoldings(5);

        assertEquals(1, holdings.size());
        assertEquals(11, holdings.get(0).getInstrumentId());
    }

    @Test
    void getModelPortfolioHoldingReturnsHolding() {
        ModelPortfolioHolding holding = modelPortfolioHoldingMapper.getModelPortfolioHolding(5, 11);

        assertNotNull(holding);
        assertEquals(0, new BigDecimal("60.00").compareTo(holding.getTargetWeightPct()));
    }

    @Test
    void insertModelPortfolioHoldingCreatesNewRow() {
        ModelPortfolioHolding holding = new ModelPortfolioHolding();
        holding.setModelPortfolioId(5);
        holding.setInstrumentId(12);
        holding.setTargetWeightPct(new BigDecimal("40.00"));

        int rows = modelPortfolioHoldingMapper.insertModelPortfolioHolding(holding);

        assertEquals(1, rows);
        ModelPortfolioHolding stored = modelPortfolioHoldingMapper.getModelPortfolioHolding(5, 12);
        assertEquals(0, new BigDecimal("40.00").compareTo(stored.getTargetWeightPct()));
    }

    @Test
    void updateModelPortfolioHoldingUpdatesRequestedFields() {
        ModelPortfolioHolding update = new ModelPortfolioHolding();
        update.setModelPortfolioId(5);
        update.setInstrumentId(11);
        update.setTargetWeightPct(new BigDecimal("55.50"));

        int rows = modelPortfolioHoldingMapper.updateModelPortfolioHolding(update);

        assertEquals(1, rows);
        ModelPortfolioHolding stored = modelPortfolioHoldingMapper.getModelPortfolioHolding(5, 11);
        assertEquals(0, new BigDecimal("55.50").compareTo(stored.getTargetWeightPct()));
    }
}

