package com.neueda.leap.dto;

import com.neueda.leap.domain.Instrument;

public record InstrumentResponseDto(
        Integer instrumentId,
        String instrumentName,
        String ticker,
        String currency,
        String assetClass,
        String securityType,
        String exchange,
        Boolean isActive
) {
    public static InstrumentResponseDto fromEntity(Instrument instrument) {
        return new InstrumentResponseDto(
                instrument.getInstrumentId(),
                instrument.getInstrumentName(),
                instrument.getTicker(),
                instrument.getCurrency(),
                instrument.getAssetClass(),
                instrument.getSecurityType(),
                instrument.getExchange(),
                instrument.getIsActive()
        );
    }
}
