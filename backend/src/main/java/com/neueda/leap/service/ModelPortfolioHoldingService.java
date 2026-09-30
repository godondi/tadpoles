package com.neueda.leap.service;

import com.neueda.leap.domain.ModelPortfolioHolding;
import com.neueda.leap.dto.CreateModelPortfolioHoldingRequestDto;
import com.neueda.leap.dto.UpdateModelPortfolioHoldingRequestDto;
import java.util.List;

public interface ModelPortfolioHoldingService {
    List<ModelPortfolioHolding> listModelPortfolioHoldings(Integer modelPortfolioId);
    ModelPortfolioHolding getModelPortfolioHolding(Integer modelPortfolioId, Integer instrumentId);
    ModelPortfolioHolding createModelPortfolioHolding(Integer modelPortfolioId, CreateModelPortfolioHoldingRequestDto request);
    ModelPortfolioHolding updateModelPortfolioHolding(
            Integer modelPortfolioId,
            Integer instrumentId,
            UpdateModelPortfolioHoldingRequestDto request
    );
}

