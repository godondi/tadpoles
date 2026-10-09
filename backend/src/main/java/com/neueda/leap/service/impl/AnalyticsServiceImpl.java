package com.neueda.leap.service.impl;

import com.neueda.leap.domain.AnalyticsSummary;
import com.neueda.leap.mapper.AnalyticsMapper;
import com.neueda.leap.service.AnalyticsService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {
    private final AnalyticsMapper analyticsMapper;

    public AnalyticsServiceImpl(AnalyticsMapper analyticsMapper) {
        this.analyticsMapper = analyticsMapper;
    }

    @Override
    public AnalyticsSummary getAnalyticsSummary(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);

        AnalyticsSummary analyticsSummary = new AnalyticsSummary();
        analyticsSummary.setScope("INTERNAL");
        analyticsSummary.setGeneratedAt(LocalDateTime.now());
        analyticsSummary.setFromDate(fromDate);
        analyticsSummary.setToDate(toDate);
        analyticsSummary.setMetrics(analyticsMapper.getAnalyticsMetrics(fromDate, toDate));
        analyticsSummary.setMostActiveInstruments(analyticsMapper.listMostActiveInstruments(fromDate, toDate));
        analyticsSummary.setClientActivityTrends(analyticsMapper.listClientActivityTrends(fromDate, toDate));
        return analyticsSummary;
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("From date must be on or before to date.");
        }
    }
}
