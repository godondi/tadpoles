package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.domain.RefreshToken;
import com.neueda.leap.dto.AuthTokensResponseDto;
import com.neueda.leap.dto.CreateRefreshTokenRequestDto;
import com.neueda.leap.dto.IssuedRefreshTokenResponseDto;
import com.neueda.leap.dto.RefreshTokenRequestDto;
import com.neueda.leap.exception.RefreshTokenNotFoundException;
import com.neueda.leap.mapper.RefreshTokenMapper;
import com.neueda.leap.service.AppUserService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {
    @Mock
    private RefreshTokenMapper refreshTokenMapper;
    @Mock
    private AppUserService appUserService;
    private AccessTokenService accessTokenService;

    private RefreshTokenServiceImpl refreshTokenService;

    @BeforeEach
    void setUp() {
        accessTokenService = new AccessTokenService("test-jwt-secret-test-jwt-secret-123456");
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenMapper, appUserService, accessTokenService);
    }

    @Test
    void getRefreshTokenReturnsTokenWhenFound() {
        when(refreshTokenMapper.getRefreshToken(21)).thenReturn(buildStoredRefreshToken());

        RefreshToken result = refreshTokenService.getRefreshToken(21);

        assertEquals(21, result.getRefreshTokenId());
        assertEquals(1, result.getUserId());
    }

    @Test
    void listUserRefreshTokensReturnsTokens() {
        when(appUserService.getUser(1)).thenReturn(buildUser());
        when(refreshTokenMapper.listUserRefreshTokens(1)).thenReturn(List.of(buildStoredRefreshToken()));

        List<RefreshToken> results = refreshTokenService.listUserRefreshTokens(1);

        assertEquals(1, results.size());
        assertEquals(21, results.get(0).getRefreshTokenId());
    }

    @Test
    void issueRefreshTokenCreatesHashedTokenAndReturnsRawValue() {
        CreateRefreshTokenRequestDto request = new CreateRefreshTokenRequestDto(3600L);
        when(appUserService.getUser(1)).thenReturn(buildUser());
        when(refreshTokenMapper.insertRefreshToken(any())).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0, RefreshToken.class);
            token.setRefreshTokenId(33);
            token.setCreatedAt(LocalDateTime.of(2026, 9, 30, 9, 0));
            return 1;
        });
        when(refreshTokenMapper.getRefreshToken(33)).thenReturn(buildIssuedRefreshToken());

        IssuedRefreshTokenResponseDto result = refreshTokenService.issueRefreshToken(1, request);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenMapper).insertRefreshToken(captor.capture());
        assertNotNull(result.refreshToken());
        assertNotEquals(result.refreshToken(), captor.getValue().getTokenHash());
        assertEquals(33, result.refreshTokenId());
        assertEquals(3600L, result.expiresInSeconds());
    }

    @Test
    void refreshAccessTokenRotatesRefreshToken() {
        String rawRefreshToken = "raw-refresh-token";
        RefreshToken stored = buildStoredRefreshToken();
        stored.setTokenHash(refreshTokenService.hashTokenForTesting(rawRefreshToken));
        AppUser user = buildUser();
        user.setRoles(List.of("ADMIN"));
        when(refreshTokenMapper.getRefreshTokenByTokenHash(stored.getTokenHash())).thenReturn(stored);
        when(appUserService.getUser(1)).thenReturn(user);
        when(refreshTokenMapper.revokeRefreshToken(eq(21), any())).thenReturn(1);
        when(refreshTokenMapper.insertRefreshToken(any())).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0, RefreshToken.class);
            token.setRefreshTokenId(34);
            return 1;
        });
        when(refreshTokenMapper.getRefreshToken(34)).thenReturn(buildRotatedRefreshToken());

        AuthTokensResponseDto result = refreshTokenService.refreshAccessToken(new RefreshTokenRequestDto(rawRefreshToken));

        assertNotNull(result.accessToken());
        assertNotNull(result.refreshToken());
        assertNotEquals(rawRefreshToken, result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(3600L, result.expiresInSeconds());
    }

    @Test
    void revokeRefreshTokenRevokesStoredToken() {
        String rawRefreshToken = "raw-refresh-token";
        RefreshToken stored = buildStoredRefreshToken();
        stored.setTokenHash(refreshTokenService.hashTokenForTesting(rawRefreshToken));
        when(refreshTokenMapper.getRefreshTokenByTokenHash(stored.getTokenHash())).thenReturn(stored);
        when(refreshTokenMapper.revokeRefreshToken(eq(21), any())).thenReturn(1);

        refreshTokenService.revokeRefreshToken(new RefreshTokenRequestDto(rawRefreshToken));

        verify(refreshTokenMapper).revokeRefreshToken(eq(21), any());
    }

    @Test
    void revokeUserRefreshTokensRevokesAllActiveTokens() {
        when(appUserService.getUser(1)).thenReturn(buildUser());

        refreshTokenService.revokeUserRefreshTokens(1);

        verify(refreshTokenMapper).revokeUserRefreshTokens(eq(1), any());
    }

    @Test
    void getRefreshTokenThrowsWhenMissing() {
        when(refreshTokenMapper.getRefreshToken(99)).thenReturn(null);

        assertThrows(RefreshTokenNotFoundException.class, () -> refreshTokenService.getRefreshToken(99));
    }

    @Test
    void refreshAccessTokenThrowsUnauthorizedWhenTokenIsInvalid() {
        when(refreshTokenMapper.getRefreshTokenByTokenHash(any())).thenReturn(null);

        assertThrows(ResponseStatusException.class,
                () -> refreshTokenService.refreshAccessToken(new RefreshTokenRequestDto("missing-token")));
    }

    private AppUser buildUser() {
        AppUser user = new AppUser();
        user.setUserId(1);
        user.setUsername("admin01");
        user.setDisplayName("Admin User");
        user.setEnabled(true);
        return user;
    }

    private RefreshToken buildStoredRefreshToken() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setRefreshTokenId(21);
        refreshToken.setUserId(1);
        refreshToken.setTokenHash("stored-token-hash");
        refreshToken.setExpiresAt(LocalDateTime.of(2026, 10, 30, 12, 0));
        refreshToken.setCreatedAt(LocalDateTime.of(2026, 9, 30, 12, 0));
        return refreshToken;
    }

    private RefreshToken buildIssuedRefreshToken() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setRefreshTokenId(33);
        refreshToken.setUserId(1);
        refreshToken.setTokenHash("newly-hashed-token");
        refreshToken.setExpiresAt(LocalDateTime.of(2026, 9, 30, 10, 0));
        refreshToken.setCreatedAt(LocalDateTime.of(2026, 9, 30, 9, 0));
        return refreshToken;
    }

    private RefreshToken buildRotatedRefreshToken() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setRefreshTokenId(34);
        refreshToken.setUserId(1);
        refreshToken.setTokenHash("rotated-token-hash");
        refreshToken.setExpiresAt(LocalDateTime.of(2026, 10, 7, 12, 0));
        refreshToken.setCreatedAt(LocalDateTime.of(2026, 9, 30, 12, 1));
        return refreshToken;
    }
}
