package com.neueda.leap.domain;

import java.math.BigDecimal;

public class InstrumentActivity {
    private Integer instrumentId;
    private String ticker;
    private String instrumentName;
    private Long tradeCount;
    private BigDecimal executedNotional;

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public Long getTradeCount() {
        return tradeCount;
    }

    public void setTradeCount(Long tradeCount) {
        this.tradeCount = tradeCount;
    }

    public BigDecimal getExecutedNotional() {
        return executedNotional;
    }

    public void setExecutedNotional(BigDecimal executedNotional) {
        this.executedNotional = executedNotional;
    }
}
