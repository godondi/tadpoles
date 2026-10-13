package com.neueda.leap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neueda.leap.domain.FauxnanceDailyCandleData;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FauxnanceDailyCandleDataDto(
        String symbol,
        String interval,
        String currency,
        List<FauxnanceDailyCandleDto> candles
) {
    public static FauxnanceDailyCandleDataDto fromEntity(FauxnanceDailyCandleData data) {
        return new FauxnanceDailyCandleDataDto(
                data.getSymbol(),
                data.getInterval(),
                data.getCurrency(),
                data.getCandles() == null ? List.of() : data.getCandles().stream().map(FauxnanceDailyCandleDto::fromEntity).toList()
        );
    }
}

