package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateClientHoldingRequestDto(
        Integer instrumentId,
        BigDecimal quantity,
        LocalDate asOfDate
) {
}

