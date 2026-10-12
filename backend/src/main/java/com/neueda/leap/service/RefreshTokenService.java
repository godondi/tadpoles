package com.neueda.leap.service;

import com.neueda.leap.domain.RefreshToken;
import com.neueda.leap.dto.AuthTokensResponseDto;
import com.neueda.leap.dto.CreateRefreshTokenRequestDto;
import com.neueda.leap.dto.IssuedRefreshTokenResponseDto;
import com.neueda.leap.dto.RefreshTokenRequestDto;
import java.util.List;

public interface RefreshTokenService {
    RefreshToken getRefreshToken(Integer refreshTokenId);
    List<RefreshToken> listUserRefreshTokens(Integer userId);
    IssuedRefreshTokenResponseDto issueRefreshToken(Integer userId, CreateRefreshTokenRequestDto request);
    AuthTokensResponseDto refreshAccessToken(RefreshTokenRequestDto request);
    void revokeRefreshToken(RefreshTokenRequestDto request);
    void revokeUserRefreshTokens(Integer userId);
}
