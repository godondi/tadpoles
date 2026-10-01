package com.neueda.leap.dto;

import com.neueda.leap.domain.RefreshToken;
import java.util.List;

public record RefreshTokenListResponseDto(
        List<RefreshTokenResponseDto> refreshTokens
) {
    public static RefreshTokenListResponseDto fromEntities(List<RefreshToken> refreshTokens) {
        return new RefreshTokenListResponseDto(
                refreshTokens.stream().map(RefreshTokenResponseDto::fromEntity).toList()
        );
    }
}

