package com.neueda.leap.domain;

import java.time.LocalDateTime;

/**
 * Domain model for the `clients` table.
 */
public class Client {
    private Integer clientId;
    private String clientName;
    private Integer advisorId;
    private Integer modelPortfolioId;
    private Integer createdByUserId;
    private LocalDateTime createdAt;
    public Integer getClientId() {
        return clientId;
    }
    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }
    public String getClientName() {
        return clientName;
    }
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
    public Integer getAdvisorId() {
        return advisorId;
    }
    public void setAdvisorId(Integer advisorId) {
        this.advisorId = advisorId;
    }
    public Integer getModelPortfolioId() {
        return modelPortfolioId;
    }
    public void setModelPortfolioId(Integer modelPortfolioId) {
        this.modelPortfolioId = modelPortfolioId;
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
