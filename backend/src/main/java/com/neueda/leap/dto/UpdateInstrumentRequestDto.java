package com.neueda.leap.dto;

public record UpdateInstrumentRequestDto(
        String instrumentName,
        String ticker,
        String currency,
        String assetClass,
        String securityType,
        Boolean isActive
) {
}

