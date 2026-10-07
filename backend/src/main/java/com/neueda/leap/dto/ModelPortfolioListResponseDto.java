package com.neueda.leap.dto;

import com.neueda.leap.domain.ModelPortfolio;
import java.util.List;

public record ModelPortfolioListResponseDto(
        List<ModelPortfolioResponseDto> modelPortfolios
) {
    public static ModelPortfolioListResponseDto fromEntities(List<ModelPortfolio> modelPortfolios) {
        return new ModelPortfolioListResponseDto(
                modelPortfolios.stream().map(ModelPortfolioResponseDto::fromEntity).toList()
        );
    }
}
