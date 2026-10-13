package com.neueda.leap.dto;

public record AuthTokensResponseDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresInSeconds
) {
}

