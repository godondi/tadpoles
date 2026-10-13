package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Instrument;
import com.neueda.leap.domain.ModelPortfolio;
import com.neueda.leap.domain.ModelPortfolioHolding;
import com.neueda.leap.dto.CreateModelPortfolioHoldingRequestDto;
import com.neueda.leap.dto.UpdateModelPortfolioHoldingRequestDto;
import com.neueda.leap.exception.ModelPortfolioHoldingNotFoundException;
import com.neueda.leap.mapper.ModelPortfolioHoldingMapper;
import com.neueda.leap.service.InstrumentService;
import com.neueda.leap.service.ModelPortfolioService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelPortfolioHoldingServiceImplTest {
    @Mock
    private ModelPortfolioHoldingMapper modelPortfolioHoldingMapper;
    @Mock
    private ModelPortfolioService modelPortfolioService;
    @Mock
    private InstrumentService instrumentService;

    private ModelPortfolioHoldingServiceImpl modelPortfolioHoldingService;

    @BeforeEach
    void setUp() {
        modelPortfolioHoldingService = new ModelPortfolioHoldingServiceImpl(
                modelPortfolioHoldingMapper,
                modelPortfolioService,
                instrumentService
        );
    }

    @Test
    void listModelPortfolioHoldingsReturnsHoldings() {
        when(modelPortfolioService.getModelPortfolio(5)).thenReturn(buildPortfolio());
        when(modelPortfolioHoldingMapper.listModelPortfolioHoldings(5)).thenReturn(List.of(buildHolding()));

        List<ModelPortfolioHolding> results = modelPortfolioHoldingService.listModelPortfolioHoldings(5);

        assertEquals(1, results.size());
        assertEquals(11, results.get(0).getInstrumentId());
    }

    @Test
    void getModelPortfolioHoldingReturnsHoldingWhenFound() {
        when(modelPortfolioService.getModelPortfolio(5)).thenReturn(buildPortfolio());
        when(modelPortfolioHoldingMapper.getModelPortfolioHolding(5, 11)).thenReturn(buildHolding());

        ModelPortfolioHolding result = modelPortfolioHoldingService.getModelPortfolioHolding(5, 11);

        assertEquals(5, result.getModelPortfolioId());
        assertEquals(0, new BigDecimal("60.00").compareTo(result.getTargetWeightPct()));
    }

    @Test
    void createModelPortfolioHoldingReturnsInsertedHolding() {
        CreateModelPortfolioHoldingRequestDto request = new CreateModelPortfolioHoldingRequestDto(
                11,
                new BigDecimal("60.00")
        );
        when(modelPortfolioService.getModelPortfolio(5)).thenReturn(buildPortfolio());
        when(instrumentService.getInstrument(11)).thenReturn(buildInstrument());
        when(modelPortfolioHoldingMapper.insertModelPortfolioHolding(any())).thenReturn(1);
        when(modelPortfolioHoldingMapper.getModelPortfolioHolding(5, 11)).thenReturn(buildHolding());

        ModelPortfolioHolding result = modelPortfolioHoldingService.createModelPortfolioHolding(5, request);

        ArgumentCaptor<ModelPortfolioHolding> captor = ArgumentCaptor.forClass(ModelPortfolioHolding.class);
        verify(modelPortfolioHoldingMapper).insertModelPortfolioHolding(captor.capture());
        assertEquals(5, captor.getValue().getModelPortfolioId());
        assertEquals(11, captor.getValue().getInstrumentId());
        assertEquals(11, result.getInstrumentId());
    }

    @Test
    void updateModelPortfolioHoldingReturnsUpdatedHolding() {
        UpdateModelPortfolioHoldingRequestDto request = new UpdateModelPortfolioHoldingRequestDto(
                new BigDecimal("55.50")
        );
        ModelPortfolioHolding updated = buildHolding();
        updated.setTargetWeightPct(new BigDecimal("55.50"));

        when(modelPortfolioService.getModelPortfolio(5)).thenReturn(buildPortfolio());
        when(modelPortfolioHoldingMapper.updateModelPortfolioHolding(any())).thenReturn(1);
        when(modelPortfolioHoldingMapper.getModelPortfolioHolding(5, 11)).thenReturn(updated);

        ModelPortfolioHolding result = modelPortfolioHoldingService.updateModelPortfolioHolding(5, 11, request);

        assertEquals(0, new BigDecimal("55.50").compareTo(result.getTargetWeightPct()));
    }

    @Test
    void getModelPortfolioHoldingThrowsWhenMissing() {
        when(modelPortfolioService.getModelPortfolio(5)).thenReturn(buildPortfolio());
        when(modelPortfolioHoldingMapper.getModelPortfolioHolding(5, 99)).thenReturn(null);

        assertThrows(ModelPortfolioHoldingNotFoundException.class,
                () -> modelPortfolioHoldingService.getModelPortfolioHolding(5, 99));
    }

    @Test
    void updateModelPortfolioHoldingThrowsWhenMissing() {
        when(modelPortfolioService.getModelPortfolio(5)).thenReturn(buildPortfolio());
        when(modelPortfolioHoldingMapper.updateModelPortfolioHolding(any())).thenReturn(0);

        assertThrows(ModelPortfolioHoldingNotFoundException.class,
                () -> modelPortfolioHoldingService.updateModelPortfolioHolding(5, 99,
                        new UpdateModelPortfolioHoldingRequestDto(new BigDecimal("25.00"))));
    }

    @Test
    void createModelPortfolioHoldingThrowsOnOutOfRangeWeight() {
        CreateModelPortfolioHoldingRequestDto request = new CreateModelPortfolioHoldingRequestDto(
                11,
                new BigDecimal("120.00")
        );

        assertThrows(IllegalArgumentException.class,
                () -> modelPortfolioHoldingService.createModelPortfolioHolding(5, request));
    }

    @Test
    void updateModelPortfolioHoldingThrowsWhenNoFieldsProvided() {
        UpdateModelPortfolioHoldingRequestDto request = new UpdateModelPortfolioHoldingRequestDto(null);

        assertThrows(IllegalArgumentException.class,
                () -> modelPortfolioHoldingService.updateModelPortfolioHolding(5, 11, request));
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

    private Instrument buildInstrument() {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(11);
        instrument.setTicker("AAPL");
        instrument.setIsActive(true);
        return instrument;
    }

    private ModelPortfolioHolding buildHolding() {
        ModelPortfolioHolding holding = new ModelPortfolioHolding();
        holding.setModelPortfolioId(5);
        holding.setInstrumentId(11);
        holding.setTargetWeightPct(new BigDecimal("60.00"));
        return holding;
    }
}
