package com.neueda.leap.service.impl;

import com.neueda.leap.domain.ModelPortfolio;
import com.neueda.leap.dto.CreateModelPortfolioRequestDto;
import com.neueda.leap.dto.UpdateModelPortfolioRequestDto;
import com.neueda.leap.exception.ModelPortfolioNotFoundException;
import com.neueda.leap.mapper.ModelPortfolioMapper;
import com.neueda.leap.service.ModelPortfolioService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelPortfolioServiceImpl implements ModelPortfolioService {
    private final ModelPortfolioMapper modelPortfolioMapper;

    public ModelPortfolioServiceImpl(ModelPortfolioMapper modelPortfolioMapper) {
        this.modelPortfolioMapper = modelPortfolioMapper;
    }

    @Override
    public ModelPortfolio getModelPortfolio(Integer id) {
        validateId(id);

        ModelPortfolio modelPortfolio = modelPortfolioMapper.getModelPortfolio(id);
        if (modelPortfolio == null) {
            throw new ModelPortfolioNotFoundException(id);
        }

        return modelPortfolio;
    }

    @Override
    public List<ModelPortfolio> listModelPortfolios() {
        return modelPortfolioMapper.listModelPortfolios();
    }

    @Override
    public ModelPortfolio createModelPortfolio(CreateModelPortfolioRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Model portfolio request is required.");
        }
        if (request.modelName() == null || request.modelName().isBlank()) {
            throw new IllegalArgumentException("Model portfolio name is required.");
        }
        validateOptionalPositiveId(request.createdByUserId(), "Created-by user id must be a positive integer.");

        ModelPortfolio modelPortfolio = new ModelPortfolio();
        modelPortfolio.setModelName(request.modelName().trim());
        modelPortfolio.setDescription(request.description() == null ? null : request.description().trim());
        modelPortfolio.setIsActive(true);
        modelPortfolio.setCreatedByUserId(request.createdByUserId());

        modelPortfolioMapper.insertModelPortfolio(modelPortfolio);
        return getModelPortfolio(modelPortfolio.getModelPortfolioId());
    }

    @Override
    public ModelPortfolio updateModelPortfolio(Integer id, UpdateModelPortfolioRequestDto request) {
        validateId(id);
        if (request == null) {
            throw new IllegalArgumentException("Model portfolio update request is required.");
        }
        if (request.modelName() != null && request.modelName().isBlank()) {
            throw new IllegalArgumentException("Model portfolio name cannot be blank.");
        }
        if (request.modelName() == null && request.description() == null && request.isActive() == null) {
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }

        ModelPortfolio update = new ModelPortfolio();
        update.setModelPortfolioId(id);
        update.setModelName(request.modelName() == null ? null : request.modelName().trim());
        update.setDescription(request.description() == null ? null : request.description().trim());
        update.setIsActive(request.isActive());

        int rows = modelPortfolioMapper.updateModelPortfolio(update);
        if (rows == 0) {
            throw new ModelPortfolioNotFoundException(id);
        }

        return getModelPortfolio(id);
    }

    private void validateId(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Model portfolio id must be a positive integer.");
        }
    }

    private void validateOptionalPositiveId(Integer id, String message) {
        if (id != null && id < 1) {
            throw new IllegalArgumentException(message);
        }
    }
}
