package com.neueda.leap.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Domain model for the `client_holdings` table.
 */
public class ClientHolding {
    private Integer holdingId;
    private Integer clientId;
    private Integer instrumentId;
    private BigDecimal quantity;
    private LocalDate asOfDate;
    public Integer getHoldingId() {
        return holdingId;
    }
    public void setHoldingId(Integer holdingId) {
        this.holdingId = holdingId;
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
    public BigDecimal getQuantity() {
        return quantity;
    }
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    public LocalDate getAsOfDate() {
        return asOfDate;
    }
    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }
}
