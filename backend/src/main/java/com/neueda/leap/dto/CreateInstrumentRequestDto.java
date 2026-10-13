package com.neueda.leap.dto;

public record CreateInstrumentRequestDto(
        String instrumentName,
        String ticker,
        String currency,
        String assetClass,
        String securityType,
        Boolean isActive
) {
}

