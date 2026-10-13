package com.neueda.leap.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain model for the `clients` table.
 */
public class Client {
    private Integer clientId;
    private Integer userId;
    private String clientName;
    private Integer advisorId;
    private Integer modelPortfolioId;
    private Integer createdByUserId;
    private BigDecimal cashBalance;
    private LocalDateTime createdAt;
    public Integer getClientId() {
        return clientId;
    }
    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }
    public Integer getUserId() {
        return userId;
    }
    public void setUserId(Integer userId) {
        this.userId = userId;
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
    public BigDecimal getCashBalance() {
        return cashBalance;
    }
    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
