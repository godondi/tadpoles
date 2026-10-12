package com.neueda.leap.dto;

import com.neueda.leap.domain.ClientActivityTrend;
import java.time.LocalDate;

public record AnalyticsClientActivityTrendResponseDto(
        LocalDate periodStart,
        Long tradeCount,
        Long executedTradeCount,
        Long activeClientCount
) {
    public static AnalyticsClientActivityTrendResponseDto fromEntity(ClientActivityTrend trend) {
        return new AnalyticsClientActivityTrendResponseDto(
                trend.getPeriodStart(),
                trend.getTradeCount(),
                trend.getExecutedTradeCount(),
                trend.getActiveClientCount()
        );
    }
}
