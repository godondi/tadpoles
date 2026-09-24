package com.neueda.leap.domain;

import java.time.LocalDate;

/**
 * Domain model for the `client_subscriptions` table.
 */
public class ClientSubscription {
    private Integer subscriptionId;
    private Integer clientId;
    private Integer modelPortfolioId;
    private LocalDate subscribedDate;
    private LocalDate endedDate;
    private String status;
    private Integer approvedByUserId;
    public Integer getSubscriptionId() {
        return subscriptionId;
    }
    public void setSubscriptionId(Integer subscriptionId) {
        this.subscriptionId = subscriptionId;
    }
    public Integer getClientId() {
        return clientId;
    }
    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }
    public Integer getModelPortfolioId() {
        return modelPortfolioId;
    }
    public void setModelPortfolioId(Integer modelPortfolioId) {
        this.modelPortfolioId = modelPortfolioId;
    }
    public LocalDate getSubscribedDate() {
        return subscribedDate;
    }
    public void setSubscribedDate(LocalDate subscribedDate) {
        this.subscribedDate = subscribedDate;
    }
    public LocalDate getEndedDate() {
        return endedDate;
    }
    public void setEndedDate(LocalDate endedDate) {
        this.endedDate = endedDate;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public Integer getApprovedByUserId() {
        return approvedByUserId;
    }
    public void setApprovedByUserId(Integer approvedByUserId) {
        this.approvedByUserId = approvedByUserId;
    }
}
