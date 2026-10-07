package com.neueda.leap.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FauxnanceQuoteMeta {
    private OffsetDateTime asOf;
    private String disclaimer;
    private String symbol;
    private String source;
    private String spreadSource;
    private Boolean stale;

    public OffsetDateTime getAsOf() {
        return asOf;
    }

    public void setAsOf(OffsetDateTime asOf) {
        this.asOf = asOf;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSpreadSource() {
        return spreadSource;
    }

    public void setSpreadSource(String spreadSource) {
        this.spreadSource = spreadSource;
    }

    public Boolean getStale() {
        return stale;
    }

    public void setStale(Boolean stale) {
        this.stale = stale;
    }
}

