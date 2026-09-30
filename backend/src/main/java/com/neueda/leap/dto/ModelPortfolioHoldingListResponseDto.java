package com.neueda.leap.dto;

import com.neueda.leap.domain.ModelPortfolioHolding;
import java.util.List;

public record ModelPortfolioHoldingListResponseDto(
        List<ModelPortfolioHoldingResponseDto> holdings
) {
    public static ModelPortfolioHoldingListResponseDto fromEntities(List<ModelPortfolioHolding> holdings) {
        return new ModelPortfolioHoldingListResponseDto(
                holdings.stream().map(ModelPortfolioHoldingResponseDto::fromEntity).toList()
        );
    }
}

