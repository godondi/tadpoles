package com.neueda.leap.domain;

import java.time.LocalDateTime;

/**
 * Domain model for the `model_portfolios` table.
 */
public class ModelPortfolio {
    private Integer modelPortfolioId;
    private String modelName;
    private String description;
    private Boolean isActive;
    private Integer createdByUserId;
    private LocalDateTime createdAt;
    public Integer getModelPortfolioId() {
        return modelPortfolioId;
    }
    public void setModelPortfolioId(Integer modelPortfolioId) {
        this.modelPortfolioId = modelPortfolioId;
    }
    public String getModelName() {
        return modelName;
    }
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public Boolean getIsActive() {
        return isActive;
    }
    public void setIsActive(Boolean active) {
        isActive = active;
    }
    public Integer getCreatedByUserId() {
        return createdByUserId;
    }
    public void setCreatedByUserId(Integer createdByUserId) {
        this.createdByUserId = createdByUserId;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
