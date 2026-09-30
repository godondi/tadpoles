package com.neueda.leap.exception;

public class ModelPortfolioHoldingNotFoundException extends RuntimeException {
    public ModelPortfolioHoldingNotFoundException(Integer modelPortfolioId, Integer instrumentId) {
        super("Model portfolio holding for portfolio " + modelPortfolioId
                + " and instrument " + instrumentId + " was not found.");
    }
}

