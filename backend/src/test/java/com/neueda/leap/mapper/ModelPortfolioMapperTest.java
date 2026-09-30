package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.ModelPortfolio;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/schema.sql", "classpath:mapper/model_portfolio_data.sql"})
class ModelPortfolioMapperTest {
    @Autowired
    private ModelPortfolioMapper modelPortfolioMapper;

    @Test
    void listModelPortfoliosReturnsRows() {
        List<ModelPortfolio> portfolios = modelPortfolioMapper.listModelPortfolios();

        assertEquals(1, portfolios.size());
        assertEquals(5, portfolios.get(0).getModelPortfolioId());
    }

    @Test
    void getModelPortfolioReturnsPortfolio() {
        ModelPortfolio portfolio = modelPortfolioMapper.getModelPortfolio(5);

        assertNotNull(portfolio);
        assertEquals("Growth", portfolio.getModelName());
        assertEquals(true, portfolio.getIsActive());
    }

    @Test
    void insertModelPortfolioCreatesNewRowWithGeneratedId() {
        ModelPortfolio portfolio = new ModelPortfolio();
        portfolio.setModelName("Balanced");
        portfolio.setDescription("Balanced portfolio");
        portfolio.setIsActive(true);
        portfolio.setCreatedByUserId(1);
        portfolio.setCreatedAt(LocalDateTime.of(2026, 9, 25, 9, 30));

        int rows = modelPortfolioMapper.insertModelPortfolio(portfolio);

        assertEquals(1, rows);
        assertNotNull(portfolio.getModelPortfolioId());
        ModelPortfolio stored = modelPortfolioMapper.getModelPortfolio(portfolio.getModelPortfolioId());
        assertEquals("Balanced", stored.getModelName());
    }

    @Test
    void updateModelPortfolioUpdatesRequestedFields() {
        ModelPortfolio update = new ModelPortfolio();
        update.setModelPortfolioId(5);
        update.setModelName("Growth Plus");
        update.setDescription("Updated growth portfolio");
        update.setIsActive(false);

        int rows = modelPortfolioMapper.updateModelPortfolio(update);

        assertEquals(1, rows);
        ModelPortfolio stored = modelPortfolioMapper.getModelPortfolio(5);
        assertEquals("Growth Plus", stored.getModelName());
        assertFalse(stored.getIsActive());
    }
}


