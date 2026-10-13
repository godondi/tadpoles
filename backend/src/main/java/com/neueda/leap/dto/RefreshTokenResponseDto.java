package com.neueda.leap.dto;

import com.neueda.leap.domain.RefreshToken;
import java.time.LocalDateTime;

public record RefreshTokenResponseDto(
        Integer refreshTokenId,
        Integer userId,
        String tokenHash,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt,
        LocalDateTime createdAt,
        boolean active
) {
    public static RefreshTokenResponseDto fromEntity(RefreshToken refreshToken) {
        LocalDateTime now = LocalDateTime.now();
        boolean active = refreshToken.getRevokedAt() == null
                && refreshToken.getExpiresAt() != null
                && refreshToken.getExpiresAt().isAfter(now);

        return new RefreshTokenResponseDto(
                refreshToken.getRefreshTokenId(),
                refreshToken.getUserId(),
                refreshToken.getTokenHash(),
                refreshToken.getExpiresAt(),
                refreshToken.getRevokedAt(),
                refreshToken.getCreatedAt(),
                active
        );
    }
}

