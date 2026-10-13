package com.neueda.leap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neueda.leap.domain.FauxnanceQuote;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FauxnanceQuoteDto(
        String symbol,
        BigDecimal price,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal spreadBps,
        String currency,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal previousClose,
        OffsetDateTime asOf,
        String marketState
) {
    public static FauxnanceQuoteDto fromEntity(FauxnanceQuote quote) {
        return new FauxnanceQuoteDto(
                quote.getSymbol(),
                quote.getPrice(),
                quote.getBid(),
                quote.getAsk(),
                quote.getSpreadBps(),
                quote.getCurrency(),
                quote.getChange(),
                quote.getChangePercent(),
                quote.getPreviousClose(),
                quote.getAsOf(),
                quote.getMarketState()
        );
    }
}

