package com.neueda.leap.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "model_portfolio_holdings")
@IdClass(ModelPortfolioHoldingsId.class)
public class ModelPortfolioHoldings {
    @Id
    @Column(name = "model_portfolio_id", nullable = false)
    private Integer modelPortfolioId;

    @Id
    @Column(name = "instrument_id", nullable = false)
    private Integer instrumentId;

    @Column(name = "target_weight_pct", nullable = false, precision = 5, scale = 2)
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
