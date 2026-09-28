package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateClientHoldingRequestDto(
        BigDecimal quantity,
        LocalDate asOfDate
) {
}

