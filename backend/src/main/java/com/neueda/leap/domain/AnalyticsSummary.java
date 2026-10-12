package com.neueda.leap.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AnalyticsSummary {
    private String scope;
    private LocalDateTime generatedAt;
    private LocalDate fromDate;
    private LocalDate toDate;
    private AnalyticsMetrics metrics;
    private List<InstrumentActivity> mostActiveInstruments;
    private List<ClientActivityTrend> clientActivityTrends;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public AnalyticsMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(AnalyticsMetrics metrics) {
        this.metrics = metrics;
    }

    public List<InstrumentActivity> getMostActiveInstruments() {
        return mostActiveInstruments;
    }

    public void setMostActiveInstruments(List<InstrumentActivity> mostActiveInstruments) {
        this.mostActiveInstruments = mostActiveInstruments;
    }

    public List<ClientActivityTrend> getClientActivityTrends() {
        return clientActivityTrends;
    }

    public void setClientActivityTrends(List<ClientActivityTrend> clientActivityTrends) {
        this.clientActivityTrends = clientActivityTrends;
    }
}
