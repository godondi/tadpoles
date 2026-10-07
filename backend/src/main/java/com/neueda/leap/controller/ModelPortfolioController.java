package com.neueda.leap.controller;

import com.neueda.leap.domain.ModelPortfolio;
import com.neueda.leap.dto.CreateModelPortfolioRequestDto;
import com.neueda.leap.dto.ModelPortfolioListResponseDto;
import com.neueda.leap.dto.ModelPortfolioResponseDto;
import com.neueda.leap.dto.UpdateModelPortfolioRequestDto;
import com.neueda.leap.service.ModelPortfolioService;
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
@RequestMapping("/api/model-portfolios")
public class ModelPortfolioController {
    private final ModelPortfolioService modelPortfolioService;

    public ModelPortfolioController(ModelPortfolioService modelPortfolioService) {
        this.modelPortfolioService = modelPortfolioService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'ANALYST', 'ADVISOR')")
    public ModelPortfolioListResponseDto listModelPortfolios() {
        return ModelPortfolioListResponseDto.fromEntities(modelPortfolioService.listModelPortfolios());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ModelPortfolioResponseDto createModelPortfolio(@RequestBody CreateModelPortfolioRequestDto request) {
        ModelPortfolio modelPortfolio = modelPortfolioService.createModelPortfolio(request);
        return ModelPortfolioResponseDto.fromEntity(modelPortfolio);
    }

    @GetMapping("/{modelPortfolioId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'ANALYST', 'ADVISOR', 'CLIENT')")
    public ModelPortfolioResponseDto getModelPortfolio(@PathVariable Integer modelPortfolioId) {
        ModelPortfolio modelPortfolio = modelPortfolioService.getModelPortfolio(modelPortfolioId);
        return ModelPortfolioResponseDto.fromEntity(modelPortfolio);
    }

    @PatchMapping("/{modelPortfolioId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ModelPortfolioResponseDto updateModelPortfolio(
            @PathVariable Integer modelPortfolioId,
            @RequestBody UpdateModelPortfolioRequestDto request
    ) {
        ModelPortfolio modelPortfolio = modelPortfolioService.updateModelPortfolio(modelPortfolioId, request);
        return ModelPortfolioResponseDto.fromEntity(modelPortfolio);
    }
}
