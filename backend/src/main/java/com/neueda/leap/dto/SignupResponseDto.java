package com.neueda.leap.dto;

public record SignupResponseDto(
        Integer userId,
        String email,
        String displayName,
        String role,
        Integer clientId,
        Boolean onboardingComplete,
        String token,
        String tokenType,
        long expiresIn
) {
}

