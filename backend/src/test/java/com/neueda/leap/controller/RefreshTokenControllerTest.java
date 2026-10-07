package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.RefreshToken;
import com.neueda.leap.dto.AuthTokensResponseDto;
import com.neueda.leap.dto.IssuedRefreshTokenResponseDto;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RefreshTokenController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class RefreshTokenControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    void refreshTokenReturnsOkWithoutAuthentication() throws Exception {
        when(refreshTokenService.refreshAccessToken(any())).thenReturn(new AuthTokensResponseDto(
                "access-token",
                "refresh-token",
                "Bearer",
                900L
        ));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "raw-refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void logoutReturnsNoContentForAuthenticatedUser() throws Exception {
        doNothing().when(refreshTokenService).revokeRefreshToken(any());

        mockMvc.perform(post("/api/auth/logout")
                        .with(jwtWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "raw-refresh-token"
                                }
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void listUserRefreshTokensReturnsJsonResponseForAdmin() throws Exception {
        when(refreshTokenService.listUserRefreshTokens(1)).thenReturn(List.of(buildRefreshToken()));

        mockMvc.perform(get("/api/users/1/refresh-tokens")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshTokens[0].refreshTokenId").value(21))
                .andExpect(jsonPath("$.refreshTokens[0].active").value(true));
    }

    @Test
    void getRefreshTokenReturnsJsonResponseForAdmin() throws Exception {
        when(refreshTokenService.getRefreshToken(21)).thenReturn(buildRefreshToken());

        mockMvc.perform(get("/api/refresh-tokens/21")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshTokenId").value(21))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void createRefreshTokenReturnsCreatedResponseForAdmin() throws Exception {
        when(refreshTokenService.issueRefreshToken(eq(1), any())).thenReturn(new IssuedRefreshTokenResponseDto(
                33,
                1,
                "raw-issued-refresh-token",
                "Bearer",
                3600L,
                LocalDateTime.of(2026, 9, 30, 10, 0),
                LocalDateTime.of(2026, 9, 30, 9, 0)
        ));

        mockMvc.perform(post("/api/users/1/refresh-tokens")
                        .with(jwtWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expiresInSeconds": 3600
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshTokenId").value(33))
                .andExpect(jsonPath("$.refreshToken").value("raw-issued-refresh-token"));
    }

    @Test
    void revokeAllRefreshTokensReturnsNoContentForAdmin() throws Exception {
        doNothing().when(refreshTokenService).revokeUserRefreshTokens(1);

        mockMvc.perform(post("/api/users/1/refresh-tokens/revoke-all")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void listUserRefreshTokensReturnsUnauthorizedWhenAdminRoleMissing() throws Exception {
        mockMvc.perform(get("/api/users/1/refresh-tokens")
                        .with(jwtWithRoles("CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied"));
    }

    private RefreshToken buildRefreshToken() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setRefreshTokenId(21);
        refreshToken.setUserId(1);
        refreshToken.setTokenHash("seed-token-hash-1");
        refreshToken.setExpiresAt(LocalDateTime.of(2026, 10, 24, 12, 0));
        refreshToken.setCreatedAt(LocalDateTime.of(2026, 9, 24, 12, 0));
        return refreshToken;
    }
}
