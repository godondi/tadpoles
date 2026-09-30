package com.neueda.leap.dto;

import com.neueda.leap.domain.ModelPortfolio;

public record ModelPortfolioResponseDto(
        Integer modelPortfolioId,
        String modelName,
        String description,
        Boolean isActive
) {
    public static ModelPortfolioResponseDto fromEntity(ModelPortfolio modelPortfolio) {
        return new ModelPortfolioResponseDto(
                modelPortfolio.getModelPortfolioId(),
                modelPortfolio.getModelName(),
                modelPortfolio.getDescription(),
                modelPortfolio.getIsActive()
        );
    }
}

