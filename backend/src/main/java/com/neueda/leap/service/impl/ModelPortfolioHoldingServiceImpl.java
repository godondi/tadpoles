package com.neueda.leap.service.impl;

import com.neueda.leap.domain.ModelPortfolioHolding;
import com.neueda.leap.dto.CreateModelPortfolioHoldingRequestDto;
import com.neueda.leap.dto.UpdateModelPortfolioHoldingRequestDto;
import com.neueda.leap.exception.ModelPortfolioHoldingNotFoundException;
import com.neueda.leap.mapper.ModelPortfolioHoldingMapper;
import com.neueda.leap.service.InstrumentService;
import com.neueda.leap.service.ModelPortfolioHoldingService;
import com.neueda.leap.service.ModelPortfolioService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelPortfolioHoldingServiceImpl implements ModelPortfolioHoldingService {
    private final ModelPortfolioHoldingMapper modelPortfolioHoldingMapper;
    private final ModelPortfolioService modelPortfolioService;
    private final InstrumentService instrumentService;

    public ModelPortfolioHoldingServiceImpl(
            ModelPortfolioHoldingMapper modelPortfolioHoldingMapper,
            ModelPortfolioService modelPortfolioService,
            InstrumentService instrumentService
    ) {
        this.modelPortfolioHoldingMapper = modelPortfolioHoldingMapper;
        this.modelPortfolioService = modelPortfolioService;
        this.instrumentService = instrumentService;
    }

    @Override
    public List<ModelPortfolioHolding> listModelPortfolioHoldings(Integer modelPortfolioId) {
        validateModelPortfolioId(modelPortfolioId);
        modelPortfolioService.getModelPortfolio(modelPortfolioId);
        return modelPortfolioHoldingMapper.listModelPortfolioHoldings(modelPortfolioId);
    }

    @Override
    public ModelPortfolioHolding getModelPortfolioHolding(Integer modelPortfolioId, Integer instrumentId) {
        validateModelPortfolioId(modelPortfolioId);
        validateInstrumentId(instrumentId);
        modelPortfolioService.getModelPortfolio(modelPortfolioId);
        ModelPortfolioHolding holding = modelPortfolioHoldingMapper.getModelPortfolioHolding(modelPortfolioId, instrumentId);
        if (holding == null) {
            throw new ModelPortfolioHoldingNotFoundException(modelPortfolioId, instrumentId);
        }
        return holding;
    }

    @Override
    public ModelPortfolioHolding createModelPortfolioHolding(
            Integer modelPortfolioId,
            CreateModelPortfolioHoldingRequestDto request
    ) {
        validateModelPortfolioId(modelPortfolioId);
        if (request == null) {
            throw new IllegalArgumentException("Model portfolio holding request is required.");
        }
        validateInstrumentId(request.instrumentId());
        validateTargetWeight(request.targetWeightPct());

        modelPortfolioService.getModelPortfolio(modelPortfolioId);
        instrumentService.getInstrument(request.instrumentId());

        ModelPortfolioHolding holding = new ModelPortfolioHolding();
        holding.setModelPortfolioId(modelPortfolioId);
        holding.setInstrumentId(request.instrumentId());
        holding.setTargetWeightPct(request.targetWeightPct());

        modelPortfolioHoldingMapper.insertModelPortfolioHolding(holding);
        return getModelPortfolioHolding(modelPortfolioId, request.instrumentId());
    }

    @Override
    public ModelPortfolioHolding updateModelPortfolioHolding(
            Integer modelPortfolioId,
            Integer instrumentId,
            UpdateModelPortfolioHoldingRequestDto request
    ) {
        validateModelPortfolioId(modelPortfolioId);
        validateInstrumentId(instrumentId);
        if (request == null) {
            throw new IllegalArgumentException("Model portfolio holding update request is required.");
        }
        if (request.targetWeightPct() == null) {
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }
        validateTargetWeight(request.targetWeightPct());

        modelPortfolioService.getModelPortfolio(modelPortfolioId);

        ModelPortfolioHolding holding = new ModelPortfolioHolding();
        holding.setModelPortfolioId(modelPortfolioId);
        holding.setInstrumentId(instrumentId);
        holding.setTargetWeightPct(request.targetWeightPct());

        int rows = modelPortfolioHoldingMapper.updateModelPortfolioHolding(holding);
        if (rows == 0) {
            throw new ModelPortfolioHoldingNotFoundException(modelPortfolioId, instrumentId);
        }

        return getModelPortfolioHolding(modelPortfolioId, instrumentId);
    }

    private void validateModelPortfolioId(Integer modelPortfolioId) {
        if (modelPortfolioId == null || modelPortfolioId < 1) {
            throw new IllegalArgumentException("Model portfolio id must be a positive integer.");
        }
    }

    private void validateInstrumentId(Integer instrumentId) {
        if (instrumentId == null || instrumentId < 1) {
            throw new IllegalArgumentException("Instrument id must be a positive integer.");
        }
    }

    private void validateTargetWeight(BigDecimal targetWeightPct) {
        if (targetWeightPct == null) {
            throw new IllegalArgumentException("Target weight percentage is required.");
        }
        if (targetWeightPct.compareTo(BigDecimal.ZERO) < 0 || targetWeightPct.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("Target weight percentage must be between 0 and 100.");
        }
    }
}

