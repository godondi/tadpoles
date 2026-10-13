package com.neueda.leap.dto;

import java.time.LocalDateTime;

public record IssuedRefreshTokenResponseDto(
        Integer refreshTokenId,
        Integer userId,
        String refreshToken,
        String tokenType,
        Long expiresInSeconds,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}

