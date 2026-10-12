package com.neueda.leap.domain;

import java.math.BigDecimal;

public class AnalyticsMetrics {
    private Long totalTrades;
    private Long executedTrades;
    private BigDecimal executedQuantity;
    private BigDecimal executedNotional;
    private Long activeClients;
    private Long activeInstruments;

    public Long getTotalTrades() {
        return totalTrades;
    }

    public void setTotalTrades(Long totalTrades) {
        this.totalTrades = totalTrades;
    }

    public Long getExecutedTrades() {
        return executedTrades;
    }

    public void setExecutedTrades(Long executedTrades) {
        this.executedTrades = executedTrades;
    }

    public BigDecimal getExecutedQuantity() {
        return executedQuantity;
    }

    public void setExecutedQuantity(BigDecimal executedQuantity) {
        this.executedQuantity = executedQuantity;
    }

    public BigDecimal getExecutedNotional() {
        return executedNotional;
    }

    public void setExecutedNotional(BigDecimal executedNotional) {
        this.executedNotional = executedNotional;
    }

    public Long getActiveClients() {
        return activeClients;
    }

    public void setActiveClients(Long activeClients) {
        this.activeClients = activeClients;
    }

    public Long getActiveInstruments() {
        return activeInstruments;
    }

    public void setActiveInstruments(Long activeInstruments) {
        this.activeInstruments = activeInstruments;
    }
}
