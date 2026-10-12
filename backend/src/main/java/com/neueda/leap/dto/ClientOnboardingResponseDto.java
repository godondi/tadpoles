package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClientOnboardingResponseDto(
        Integer userId,
        Integer clientId,
        String email,
        String displayName,
        String clientName,
        String phone,
        LocalDate dateOfBirth,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        String employmentStatus,
        BigDecimal netWorth,
        String riskTolerance,
        String investmentObjective,
        String preferredContactMethod,
        Boolean paperlessStatements,
        Boolean marketingOptIn,
        Boolean onboardingComplete
) {
}

