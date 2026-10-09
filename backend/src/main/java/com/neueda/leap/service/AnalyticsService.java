package com.neueda.leap.service;

import com.neueda.leap.domain.AnalyticsSummary;
import java.time.LocalDate;

public interface AnalyticsService {
    AnalyticsSummary getAnalyticsSummary(LocalDate fromDate, LocalDate toDate);
}
