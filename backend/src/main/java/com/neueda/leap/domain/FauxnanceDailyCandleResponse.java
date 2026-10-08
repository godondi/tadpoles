package com.neueda.leap.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FauxnanceDailyCandleResponse {
    private FauxnanceDailyCandleData data;
    private FauxnanceMarketDataMeta meta;

    public FauxnanceDailyCandleData getData() {
        return data;
    }

    public void setData(FauxnanceDailyCandleData data) {
        this.data = data;
    }

    public FauxnanceMarketDataMeta getMeta() {
        return meta;
    }

    public void setMeta(FauxnanceMarketDataMeta meta) {
        this.meta = meta;
    }
}

