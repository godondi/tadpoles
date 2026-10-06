package com.neueda.leap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neueda.leap.domain.FauxnanceDailyCandle;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FauxnanceDailyCandleDto(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal adjclose,
        Long volume,
        boolean synthetic
) {
    public static FauxnanceDailyCandleDto fromEntity(FauxnanceDailyCandle candle) {
        return new FauxnanceDailyCandleDto(
                candle.getDate(),
                candle.getOpen(),
                candle.getHigh(),
                candle.getLow(),
                candle.getClose(),
                candle.getAdjclose(),
                candle.getVolume(),
                candle.isSynthetic()
        );
    }
}

