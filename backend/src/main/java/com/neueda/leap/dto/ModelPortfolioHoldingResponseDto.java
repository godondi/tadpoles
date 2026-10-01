package com.neueda.leap.dto;

import com.neueda.leap.domain.ModelPortfolioHolding;
import java.math.BigDecimal;

public record ModelPortfolioHoldingResponseDto(
        Integer modelPortfolioId,
        Integer instrumentId,
        BigDecimal targetWeightPct
) {
    public static ModelPortfolioHoldingResponseDto fromEntity(ModelPortfolioHolding holding) {
        return new ModelPortfolioHoldingResponseDto(
                holding.getModelPortfolioId(),
                holding.getInstrumentId(),
                holding.getTargetWeightPct()
        );
    }
}
