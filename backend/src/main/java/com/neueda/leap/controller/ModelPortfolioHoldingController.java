package com.neueda.leap.controller;

import com.neueda.leap.domain.ModelPortfolioHolding;
import com.neueda.leap.dto.CreateModelPortfolioHoldingRequestDto;
import com.neueda.leap.dto.ModelPortfolioHoldingListResponseDto;
import com.neueda.leap.dto.ModelPortfolioHoldingResponseDto;
import com.neueda.leap.dto.UpdateModelPortfolioHoldingRequestDto;
import com.neueda.leap.service.ModelPortfolioHoldingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-portfolios/{modelPortfolioId}/holdings")
public class ModelPortfolioHoldingController {
    private final ModelPortfolioHoldingService modelPortfolioHoldingService;

    public ModelPortfolioHoldingController(ModelPortfolioHoldingService modelPortfolioHoldingService) {
        this.modelPortfolioHoldingService = modelPortfolioHoldingService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'ANALYST', 'ADVISOR', 'CLIENT')")
    public ModelPortfolioHoldingListResponseDto listModelPortfolioHoldings(
            @PathVariable Integer modelPortfolioId
    ) {
        return ModelPortfolioHoldingListResponseDto.fromEntities(
                modelPortfolioHoldingService.listModelPortfolioHoldings(modelPortfolioId)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ModelPortfolioHoldingResponseDto createModelPortfolioHolding(
            @PathVariable Integer modelPortfolioId,
            @RequestBody CreateModelPortfolioHoldingRequestDto request
    ) {
        ModelPortfolioHolding holding = modelPortfolioHoldingService.createModelPortfolioHolding(modelPortfolioId, request);
        return ModelPortfolioHoldingResponseDto.fromEntity(holding);
    }

    @GetMapping("/{instrumentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'ANALYST', 'ADVISOR', 'CLIENT')")
    public ModelPortfolioHoldingResponseDto getModelPortfolioHolding(
            @PathVariable Integer modelPortfolioId,
            @PathVariable Integer instrumentId
    ) {
        ModelPortfolioHolding holding = modelPortfolioHoldingService.getModelPortfolioHolding(modelPortfolioId, instrumentId);
        return ModelPortfolioHoldingResponseDto.fromEntity(holding);
    }

    @PatchMapping("/{instrumentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ModelPortfolioHoldingResponseDto updateModelPortfolioHolding(
            @PathVariable Integer modelPortfolioId,
            @PathVariable Integer instrumentId,
            @RequestBody UpdateModelPortfolioHoldingRequestDto request
    ) {
        ModelPortfolioHolding holding = modelPortfolioHoldingService.updateModelPortfolioHolding(
                modelPortfolioId,
                instrumentId,
                request
        );
        return ModelPortfolioHoldingResponseDto.fromEntity(holding);
    }
}
