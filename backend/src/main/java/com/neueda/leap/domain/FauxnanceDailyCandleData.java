package com.neueda.leap.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FauxnanceDailyCandleData {
    private String symbol;
    private String interval;
    private String currency;
    private List<FauxnanceDailyCandle> candles;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<FauxnanceDailyCandle> getCandles() {
        return candles;
    }

    public void setCandles(List<FauxnanceDailyCandle> candles) {
        this.candles = candles;
    }
}

