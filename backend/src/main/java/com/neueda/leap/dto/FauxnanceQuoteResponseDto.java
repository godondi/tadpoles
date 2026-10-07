package com.neueda.leap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neueda.leap.domain.FauxnanceQuoteResponse;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FauxnanceQuoteResponseDto(
        FauxnanceQuoteDto data,
        FauxnanceQuoteMetaDto meta
) {
    public static FauxnanceQuoteResponseDto fromEntity(FauxnanceQuoteResponse response) {
        return new FauxnanceQuoteResponseDto(
                FauxnanceQuoteDto.fromEntity(response.getData()),
                FauxnanceQuoteMetaDto.fromEntity(response.getMeta())
        );
    }
}

