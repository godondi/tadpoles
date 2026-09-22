package com.neueda.leap.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "clients")
public class Clients {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_id")
    private Integer clientId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "advisor_id")
    private Integer advisorId;

    @Column(name = "model_portfolio_id")
    private Integer modelPortfolioId;

    @Column(name = "created_by_user_id")
    private Integer createdByUserId;

    @Column(name = "created_at", nullable = false)
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
