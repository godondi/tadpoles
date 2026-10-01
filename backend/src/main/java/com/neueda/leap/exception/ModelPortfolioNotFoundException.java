package com.neueda.leap.exception;

public class ModelPortfolioNotFoundException extends RuntimeException {
    public ModelPortfolioNotFoundException(Integer modelPortfolioId) {
        super("Model portfolio with id " + modelPortfolioId + " was not found.");
    }
}
