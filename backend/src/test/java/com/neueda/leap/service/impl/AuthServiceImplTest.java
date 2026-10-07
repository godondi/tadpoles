package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.AuthTokensResponseDto;
import com.neueda.leap.dto.IssuedRefreshTokenResponseDto;
import com.neueda.leap.dto.LoginRequestDto;
import com.neueda.leap.mapper.UserMapper;
import com.neueda.leap.service.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {
    private UserMapper userMapper;
    private RefreshTokenService refreshTokenService;
    private AccessTokenService accessTokenService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userMapper = org.mockito.Mockito.mock(UserMapper.class);
        refreshTokenService = org.mockito.Mockito.mock(RefreshTokenService.class);
        accessTokenService = new AccessTokenService("test-jwt-secret-test-jwt-secret-123456");
        authService = new AuthServiceImpl(userMapper, refreshTokenService, accessTokenService);
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() {
        AppUser user = buildUser();
        when(userMapper.findByUsername("client01")).thenReturn(user);
        when(userMapper.findRolesByUserId(14)).thenReturn(List.of("CLIENT"));
        when(refreshTokenService.issueRefreshToken(eq(14), eq(null))).thenReturn(new IssuedRefreshTokenResponseDto(
                21,
                14,
                "raw-refresh-token",
                "Bearer",
                604800L,
                LocalDateTime.of(2026, 10, 14, 12, 0),
                LocalDateTime.of(2026, 10, 7, 12, 0)
        ));

        AuthTokensResponseDto response = authService.login(new LoginRequestDto("client01", "client-password"));

        assertNotNull(response.accessToken());
        assertEquals("raw-refresh-token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresInSeconds());
    }

    @Test
    void loginRejectsDisabledUser() {
        AppUser user = buildUser();
        user.setEnabled(false);
        when(userMapper.findByUsername("client01")).thenReturn(user);

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequestDto("client01", "client-password")));
    }

    private AppUser buildUser() {
        AppUser user = new AppUser();
        user.setUserId(14);
        user.setUsername("client01");
        user.setEmail("client01@tadpoles.dev");
        user.setDisplayName("Client User");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("client-password"));
        user.setEnabled(true);
        user.setClientId(7);
        return user;
    }
}
