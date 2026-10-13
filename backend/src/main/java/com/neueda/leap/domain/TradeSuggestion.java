package com.neueda.leap.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain model for the `trade_suggestions` table.
 */
public class TradeSuggestion {
    private Integer suggestionId;
    private Integer advisorId;
    private Integer clientId;
    private Integer instrumentId;
    private String tradeType;
    private BigDecimal quantity;
    private BigDecimal proposedPrice;
    private LocalDateTime suggestedAt;
    private String status;
    private String notes;
    public Integer getSuggestionId() {
        return suggestionId;
    }
    public void setSuggestionId(Integer suggestionId) {
        this.suggestionId = suggestionId;
    }
    public Integer getAdvisorId() {
        return advisorId;
    }
    public void setAdvisorId(Integer advisorId) {
        this.advisorId = advisorId;
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
    public BigDecimal getProposedPrice() {
        return proposedPrice;
    }
    public void setProposedPrice(BigDecimal proposedPrice) {
        this.proposedPrice = proposedPrice;
    }
    public LocalDateTime getSuggestedAt() {
        return suggestedAt;
    }
    public void setSuggestedAt(LocalDateTime suggestedAt) {
        this.suggestedAt = suggestedAt;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
}
