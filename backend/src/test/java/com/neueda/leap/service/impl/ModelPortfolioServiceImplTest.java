package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.ModelPortfolio;
import com.neueda.leap.dto.CreateModelPortfolioRequestDto;
import com.neueda.leap.dto.UpdateModelPortfolioRequestDto;
import com.neueda.leap.exception.ModelPortfolioNotFoundException;
import com.neueda.leap.mapper.ModelPortfolioMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelPortfolioServiceImplTest {
    @Mock
    private ModelPortfolioMapper modelPortfolioMapper;

    private ModelPortfolioServiceImpl modelPortfolioService;

    @BeforeEach
    void setUp() {
        modelPortfolioService = new ModelPortfolioServiceImpl(modelPortfolioMapper);
    }

    @Test
    void getModelPortfolioReturnsPortfolioWhenFound() {
        when(modelPortfolioMapper.getModelPortfolio(5)).thenReturn(buildPortfolio());

        ModelPortfolio result = modelPortfolioService.getModelPortfolio(5);

        assertEquals(5, result.getModelPortfolioId());
        assertEquals("Growth", result.getModelName());
    }

    @Test
    void listModelPortfoliosReturnsPortfolios() {
        when(modelPortfolioMapper.listModelPortfolios()).thenReturn(List.of(buildPortfolio()));

        List<ModelPortfolio> results = modelPortfolioService.listModelPortfolios();

        assertEquals(1, results.size());
        assertEquals(5, results.get(0).getModelPortfolioId());
    }

    @Test
    void createModelPortfolioDefaultsIsActiveToTrue() {
        CreateModelPortfolioRequestDto request = new CreateModelPortfolioRequestDto(
                "Growth",
                "Growth portfolio",
                1
        );
        when(modelPortfolioMapper.insertModelPortfolio(any())).thenAnswer(invocation -> {
            ModelPortfolio portfolio = invocation.getArgument(0, ModelPortfolio.class);
            portfolio.setModelPortfolioId(5);
            return 1;
        });
        when(modelPortfolioMapper.getModelPortfolio(5)).thenReturn(buildPortfolio());

        ModelPortfolio result = modelPortfolioService.createModelPortfolio(request);

        ArgumentCaptor<ModelPortfolio> captor = ArgumentCaptor.forClass(ModelPortfolio.class);
        verify(modelPortfolioMapper).insertModelPortfolio(captor.capture());
        assertEquals(true, captor.getValue().getIsActive());
        assertEquals("Growth", captor.getValue().getModelName());
        assertEquals(5, result.getModelPortfolioId());
    }

    @Test
    void updateModelPortfolioReturnsUpdatedPortfolio() {
        UpdateModelPortfolioRequestDto request = new UpdateModelPortfolioRequestDto(
                "Growth Plus",
                "Updated growth portfolio",
                false
        );
        ModelPortfolio updated = buildPortfolio();
        updated.setModelName("Growth Plus");
        updated.setDescription("Updated growth portfolio");
        updated.setIsActive(false);

        when(modelPortfolioMapper.updateModelPortfolio(any())).thenReturn(1);
        when(modelPortfolioMapper.getModelPortfolio(5)).thenReturn(updated);

        ModelPortfolio result = modelPortfolioService.updateModelPortfolio(5, request);

        assertEquals("Growth Plus", result.getModelName());
        assertEquals(false, result.getIsActive());
    }

    @Test
    void getModelPortfolioThrowsWhenMissing() {
        when(modelPortfolioMapper.getModelPortfolio(99)).thenReturn(null);

        assertThrows(ModelPortfolioNotFoundException.class, () -> modelPortfolioService.getModelPortfolio(99));
    }

    @Test
    void updateModelPortfolioThrowsWhenMissing() {
        when(modelPortfolioMapper.updateModelPortfolio(any())).thenReturn(0);

        assertThrows(ModelPortfolioNotFoundException.class,
                () -> modelPortfolioService.updateModelPortfolio(99,
                        new UpdateModelPortfolioRequestDto("Growth", null, true)));
    }

    @Test
    void createModelPortfolioThrowsOnBlankName() {
        CreateModelPortfolioRequestDto request = new CreateModelPortfolioRequestDto(" ", null, null);

        assertThrows(IllegalArgumentException.class, () -> modelPortfolioService.createModelPortfolio(request));
    }

    @Test
    void updateModelPortfolioThrowsWhenNoFieldsProvided() {
        UpdateModelPortfolioRequestDto request = new UpdateModelPortfolioRequestDto(null, null, null);

        assertThrows(IllegalArgumentException.class, () -> modelPortfolioService.updateModelPortfolio(5, request));
    }

    private ModelPortfolio buildPortfolio() {
        ModelPortfolio portfolio = new ModelPortfolio();
        portfolio.setModelPortfolioId(5);
        portfolio.setModelName("Growth");
        portfolio.setDescription("Growth portfolio");
        portfolio.setIsActive(true);
        portfolio.setCreatedByUserId(1);
        portfolio.setCreatedAt(LocalDateTime.of(2026, 9, 24, 10, 15));
        return portfolio;
    }
}

