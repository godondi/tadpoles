package com.neueda.leap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neueda.leap.domain.FauxnanceDailyCandleResponse;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FauxnanceDailyCandleResponseDto(
        FauxnanceDailyCandleDataDto data,
        FauxnanceMarketDataMetaDto meta
) {
    public static FauxnanceDailyCandleResponseDto fromEntity(FauxnanceDailyCandleResponse response) {
        return new FauxnanceDailyCandleResponseDto(
                FauxnanceDailyCandleDataDto.fromEntity(response.getData()),
                FauxnanceMarketDataMetaDto.fromEntity(response.getMeta())
        );
    }
}

