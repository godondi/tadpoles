package com.neueda.leap.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FauxnanceQuoteResponse {
    private FauxnanceQuote data;
    private FauxnanceQuoteMeta meta;

    public FauxnanceQuote getData() {
        return data;
    }

    public void setData(FauxnanceQuote data) {
        this.data = data;
    }

    public FauxnanceQuoteMeta getMeta() {
        return meta;
    }

    public void setMeta(FauxnanceQuoteMeta meta) {
        this.meta = meta;
    }
}

