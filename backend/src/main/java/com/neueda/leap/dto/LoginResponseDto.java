package com.neueda.leap.dto;

public record LoginResponseDto(
        String token,
        String tokenType,
        long expiresIn,
        Integer userId,
        String email,
        String displayName,
        Integer clientId,
        Boolean onboardingComplete
) {
}

