package com.neueda.leap.domain;

import java.math.BigDecimal;

/**
 * Domain model for the `model_portfolio_holdings` table.
 */
public class ModelPortfolioHolding {
    private Integer modelPortfolioId;
    private Integer instrumentId;
    private BigDecimal targetWeightPct;
    public Integer getModelPortfolioId() {
        return modelPortfolioId;
    }
    public void setModelPortfolioId(Integer modelPortfolioId) {
        this.modelPortfolioId = modelPortfolioId;
    }
    public Integer getInstrumentId() {
        return instrumentId;
    }
    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }
    public BigDecimal getTargetWeightPct() {
        return targetWeightPct;
    }
    public void setTargetWeightPct(BigDecimal targetWeightPct) {
        this.targetWeightPct = targetWeightPct;
    }
}
