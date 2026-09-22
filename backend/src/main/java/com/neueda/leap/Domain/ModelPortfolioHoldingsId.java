package com.neueda.leap.Domain;

import java.io.Serializable;
import java.util.Objects;

public class ModelPortfolioHoldingsId implements Serializable {
    private Integer modelPortfolioId;
    private Integer instrumentId;

    public ModelPortfolioHoldingsId() {
    }

    public ModelPortfolioHoldingsId(Integer modelPortfolioId, Integer instrumentId) {
        this.modelPortfolioId = modelPortfolioId;
        this.instrumentId = instrumentId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModelPortfolioHoldingsId that)) {
            return false;
        }
        return Objects.equals(modelPortfolioId, that.modelPortfolioId)
            && Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelPortfolioId, instrumentId);
    }
}
