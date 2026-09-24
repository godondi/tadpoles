package com.neueda.leap.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Domain model for the `client_trades` table.
 */
public class ClientTrade {
    private Integer tradeId;
    private Integer clientId;
    private Integer instrumentId;
    private Integer submittedByUserId;
    private Integer approvedByUserId;
    private String tradeType;
    private BigDecimal quantity;
    private BigDecimal price;
    private LocalDate tradeDate;
    private String status;
    private LocalDateTime executedAt;
    private String reason;
    public Integer getTradeId() {
        return tradeId;
    }
    public void setTradeId(Integer tradeId) {
        this.tradeId = tradeId;
    }
    public Integer getClientId() {
        return clientId;
    }
    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }
    public Integer getInstrumentId() {
        return instrumentId;
    }
    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }
    public Integer getSubmittedByUserId() {
        return submittedByUserId;
    }
    public void setSubmittedByUserId(Integer submittedByUserId) {
        this.submittedByUserId = submittedByUserId;
    }
    public Integer getApprovedByUserId() {
        return approvedByUserId;
    }
    public void setApprovedByUserId(Integer approvedByUserId) {
        this.approvedByUserId = approvedByUserId;
    }
    public String getTradeType() {
        return tradeType;
    }
    public void setTradeType(String tradeType) {
        this.tradeType = tradeType;
    }
    public BigDecimal getQuantity() {
        return quantity;
    }
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    public BigDecimal getPrice() {
        return price;
    }
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    public LocalDate getTradeDate() {
        return tradeDate;
    }
    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public LocalDateTime getExecutedAt() {
        return executedAt;
    }
    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
    public String getReason() {
        return reason;
    }
    public void setReason(String reason) {
        this.reason = reason;
    }
}
