package com.neueda.leap.service;

import com.neueda.leap.domain.ModelPortfolio;
import com.neueda.leap.dto.CreateModelPortfolioRequestDto;
import com.neueda.leap.dto.UpdateModelPortfolioRequestDto;
import java.util.List;

public interface ModelPortfolioService {
    ModelPortfolio getModelPortfolio(Integer id);
    List<ModelPortfolio> listModelPortfolios();
    ModelPortfolio createModelPortfolio(CreateModelPortfolioRequestDto request);
    ModelPortfolio updateModelPortfolio(Integer id, UpdateModelPortfolioRequestDto request);
}

