package com.neueda.leap.dto;

import com.neueda.leap.domain.InstrumentActivity;
import java.math.BigDecimal;

public record AnalyticsInstrumentActivityResponseDto(
        Integer instrumentId,
        String ticker,
        String instrumentName,
        Long tradeCount,
        BigDecimal executedNotional
) {
    public static AnalyticsInstrumentActivityResponseDto fromEntity(InstrumentActivity instrumentActivity) {
        return new AnalyticsInstrumentActivityResponseDto(
                instrumentActivity.getInstrumentId(),
                instrumentActivity.getTicker(),
                instrumentActivity.getInstrumentName(),
                instrumentActivity.getTradeCount(),
                instrumentActivity.getExecutedNotional()
        );
    }
}
