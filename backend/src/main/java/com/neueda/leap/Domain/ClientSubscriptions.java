package com.neueda.leap.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "client_subscriptions")
public class ClientSubscriptions {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Integer subscriptionId;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    @Column(name = "model_portfolio_id", nullable = false)
    private Integer modelPortfolioId;

    @Column(name = "subscribed_date", nullable = false)
    private LocalDate subscribedDate;

    @Column(name = "ended_date")
    private LocalDate endedDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "approved_by_user_id")
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
