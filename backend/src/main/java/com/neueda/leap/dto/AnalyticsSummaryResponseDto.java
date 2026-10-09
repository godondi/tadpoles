package com.neueda.leap.dto;

import com.neueda.leap.domain.AnalyticsSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AnalyticsSummaryResponseDto(
        String scope,
        LocalDateTime generatedAt,
        LocalDate fromDate,
        LocalDate toDate,
        AnalyticsMetricsResponseDto metrics,
        List<AnalyticsInstrumentActivityResponseDto> mostActiveInstruments,
        List<AnalyticsClientActivityTrendResponseDto> clientActivityTrends
) {
    public static AnalyticsSummaryResponseDto fromEntity(AnalyticsSummary analyticsSummary) {
        return new AnalyticsSummaryResponseDto(
                analyticsSummary.getScope(),
                analyticsSummary.getGeneratedAt(),
                analyticsSummary.getFromDate(),
                analyticsSummary.getToDate(),
                AnalyticsMetricsResponseDto.fromEntity(analyticsSummary.getMetrics()),
                analyticsSummary.getMostActiveInstruments().stream()
                        .map(AnalyticsInstrumentActivityResponseDto::fromEntity)
                        .toList(),
                analyticsSummary.getClientActivityTrends().stream()
                        .map(AnalyticsClientActivityTrendResponseDto::fromEntity)
                        .toList()
        );
    }
}
