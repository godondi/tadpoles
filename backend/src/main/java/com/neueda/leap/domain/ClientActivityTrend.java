package com.neueda.leap.domain;

import java.time.LocalDate;

public class ClientActivityTrend {
    private LocalDate periodStart;
    private Long tradeCount;
    private Long executedTradeCount;
    private Long activeClientCount;

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public Long getTradeCount() {
        return tradeCount;
    }

    public void setTradeCount(Long tradeCount) {
        this.tradeCount = tradeCount;
    }

    public Long getExecutedTradeCount() {
        return executedTradeCount;
    }

    public void setExecutedTradeCount(Long executedTradeCount) {
        this.executedTradeCount = executedTradeCount;
    }

    public Long getActiveClientCount() {
        return activeClientCount;
    }

    public void setActiveClientCount(Long activeClientCount) {
        this.activeClientCount = activeClientCount;
    }
}
