package com.neueda.leap.service.impl;

import com.neueda.leap.domain.RefreshToken;
import com.neueda.leap.dto.AuthTokensResponseDto;
import com.neueda.leap.dto.CreateRefreshTokenRequestDto;
import com.neueda.leap.dto.IssuedRefreshTokenResponseDto;
import com.neueda.leap.dto.RefreshTokenRequestDto;
import com.neueda.leap.exception.RefreshTokenNotFoundException;
import com.neueda.leap.mapper.RefreshTokenMapper;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.RefreshTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private static final long DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 604800L;

    private final RefreshTokenMapper refreshTokenMapper;
    private final AppUserService appUserService;
    private final AccessTokenService accessTokenService;

    public RefreshTokenServiceImpl(
            RefreshTokenMapper refreshTokenMapper,
            AppUserService appUserService,
            AccessTokenService accessTokenService
    ) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.appUserService = appUserService;
        this.accessTokenService = accessTokenService;
    }

    @Override
    public RefreshToken getRefreshToken(Integer refreshTokenId) {
        validateRefreshTokenId(refreshTokenId);

        RefreshToken refreshToken = refreshTokenMapper.getRefreshToken(refreshTokenId);
        if (refreshToken == null) {
            throw new RefreshTokenNotFoundException(refreshTokenId);
        }

        return refreshToken;
    }

    @Override
    public List<RefreshToken> listUserRefreshTokens(Integer userId) {
        validateUserId(userId);
        appUserService.getUser(userId);
        return refreshTokenMapper.listUserRefreshTokens(userId);
    }

    @Override
    public IssuedRefreshTokenResponseDto issueRefreshToken(Integer userId, CreateRefreshTokenRequestDto request) {
        validateUserId(userId);
        appUserService.getUser(userId);
        long ttlSeconds = resolveTtlSeconds(request);
        return issueRefreshTokenInternal(userId, ttlSeconds);
    }

    @Override
    public AuthTokensResponseDto refreshAccessToken(RefreshTokenRequestDto request) {
        RefreshToken stored = resolveUsableRefreshToken(request);
        com.neueda.leap.domain.AppUser user = appUserService.getUser(stored.getUserId());

        LocalDateTime revokedAt = LocalDateTime.now();
        int rows = refreshTokenMapper.revokeRefreshToken(stored.getRefreshTokenId(), revokedAt);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is no longer active.");
        }

        IssuedRefreshTokenResponseDto rotatedToken = issueRefreshTokenInternal(
                stored.getUserId(),
                DEFAULT_REFRESH_TOKEN_TTL_SECONDS
        );

        return new AuthTokensResponseDto(
                accessTokenService.generateAccessToken(
                        user,
                        user.getRoles() == null ? List.of() : List.copyOf(user.getRoles()),
                        rotatedToken.refreshTokenId()
                ),
                rotatedToken.refreshToken(),
                rotatedToken.tokenType(),
                accessTokenService.getAccessTokenTtlSeconds()
        );
    }

    @Override
    public void revokeRefreshToken(RefreshTokenRequestDto request) {
        RefreshToken stored = resolveRefreshToken(request);
        if (stored.getRevokedAt() != null) {
            return;
        }

        int rows = refreshTokenMapper.revokeRefreshToken(stored.getRefreshTokenId(), LocalDateTime.now());
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is no longer active.");
        }
    }

    @Override
    public void revokeUserRefreshTokens(Integer userId) {
        validateUserId(userId);
        appUserService.getUser(userId);
        refreshTokenMapper.revokeUserRefreshTokens(userId, LocalDateTime.now());
    }

    String hashTokenForTesting(String rawToken) {
        return hashToken(rawToken);
    }

    private IssuedRefreshTokenResponseDto issueRefreshTokenInternal(Integer userId, long ttlSeconds) {
        String rawRefreshToken = generateOpaqueToken("refresh");
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(expiresAt);

        refreshTokenMapper.insertRefreshToken(refreshToken);
        RefreshToken stored = getRefreshToken(refreshToken.getRefreshTokenId());

        return new IssuedRefreshTokenResponseDto(
                stored.getRefreshTokenId(),
                stored.getUserId(),
                rawRefreshToken,
                "Bearer",
                ttlSeconds,
                stored.getExpiresAt(),
                stored.getCreatedAt()
        );
    }

    private RefreshToken resolveUsableRefreshToken(RefreshTokenRequestDto request) {
        RefreshToken refreshToken = resolveRefreshToken(request);
        if (refreshToken.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked.");
        }
        if (refreshToken.getExpiresAt() == null || !refreshToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired.");
        }
        return refreshToken;
    }

    private RefreshToken resolveRefreshToken(RefreshTokenRequestDto request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new IllegalArgumentException("Refresh token is required.");
        }

        String tokenHash = hashToken(request.refreshToken().trim());
        RefreshToken refreshToken = refreshTokenMapper.getRefreshTokenByTokenHash(tokenHash);
        if (refreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }

        return refreshToken;
    }

    private long resolveTtlSeconds(CreateRefreshTokenRequestDto request) {
        if (request == null || request.expiresInSeconds() == null) {
            return DEFAULT_REFRESH_TOKEN_TTL_SECONDS;
        }
        if (request.expiresInSeconds() < 1) {
            throw new IllegalArgumentException("Refresh token expiry must be greater than zero seconds.");
        }
        return request.expiresInSeconds();
    }

    private void validateUserId(Integer userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("User id must be a positive integer.");
        }
    }

    private void validateRefreshTokenId(Integer refreshTokenId) {
        if (refreshTokenId == null || refreshTokenId < 1) {
            throw new IllegalArgumentException("Refresh token id must be a positive integer.");
        }
    }

    private String generateOpaqueToken(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 hashing is unavailable.", exception);
        }
    }
}
