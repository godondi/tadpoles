package com.neueda.leap.dto;

import com.neueda.leap.domain.AnalyticsMetrics;
import java.math.BigDecimal;

public record AnalyticsMetricsResponseDto(
        Long totalTrades,
        Long executedTrades,
        BigDecimal executedQuantity,
        BigDecimal executedNotional,
        Long activeClients,
        Long activeInstruments
) {
    public static AnalyticsMetricsResponseDto fromEntity(AnalyticsMetrics metrics) {
        return new AnalyticsMetricsResponseDto(
                metrics.getTotalTrades(),
                metrics.getExecutedTrades(),
                metrics.getExecutedQuantity(),
                metrics.getExecutedNotional(),
                metrics.getActiveClients(),
                metrics.getActiveInstruments()
        );
    }
}
