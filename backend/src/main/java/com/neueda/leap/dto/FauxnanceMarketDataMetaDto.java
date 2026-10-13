package com.neueda.leap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neueda.leap.domain.FauxnanceMarketDataMeta;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FauxnanceMarketDataMetaDto(
        OffsetDateTime asOf,
        String disclaimer,
        String symbol,
        String source,
        Boolean stale,
        Boolean partial,
        LocalDate availableFrom
) {
    public static FauxnanceMarketDataMetaDto fromEntity(FauxnanceMarketDataMeta meta) {
        return new FauxnanceMarketDataMetaDto(
                meta.getAsOf(),
                meta.getDisclaimer(),
                meta.getSymbol(),
                meta.getSource(),
                meta.getStale(),
                meta.getPartial(),
                meta.getAvailableFrom()
        );
    }
}

