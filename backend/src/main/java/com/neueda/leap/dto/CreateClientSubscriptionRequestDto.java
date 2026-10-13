package com.neueda.leap.dto;

import java.time.LocalDate;

public record CreateClientSubscriptionRequestDto(
        Integer modelPortfolioId,
        LocalDate subscribedDate,
        Integer approvedByUserId
) {
}
