package com.neueda.leap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neueda.leap.domain.FauxnanceQuoteMeta;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FauxnanceQuoteMetaDto(
        OffsetDateTime asOf,
        String disclaimer,
        String symbol,
        String source,
        String spreadSource,
        Boolean stale
) {
    public static FauxnanceQuoteMetaDto fromEntity(FauxnanceQuoteMeta meta) {
        return new FauxnanceQuoteMetaDto(
                meta.getAsOf(),
                meta.getDisclaimer(),
                meta.getSymbol(),
                meta.getSource(),
                meta.getSpreadSource(),
                meta.getStale()
        );
    }
}

