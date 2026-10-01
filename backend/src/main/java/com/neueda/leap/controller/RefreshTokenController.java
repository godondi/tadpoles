package com.neueda.leap.controller;

import com.neueda.leap.domain.RefreshToken;
import com.neueda.leap.dto.AuthTokensResponseDto;
import com.neueda.leap.dto.CreateRefreshTokenRequestDto;
import com.neueda.leap.dto.IssuedRefreshTokenResponseDto;
import com.neueda.leap.dto.RefreshTokenListResponseDto;
import com.neueda.leap.dto.RefreshTokenRequestDto;
import com.neueda.leap.dto.RefreshTokenResponseDto;
import com.neueda.leap.service.RefreshTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RefreshTokenController {
    private final RefreshTokenService refreshTokenService;

    public RefreshTokenController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/auth/refresh")
    public AuthTokensResponseDto refreshToken(@RequestBody RefreshTokenRequestDto request) {
        return refreshTokenService.refreshAccessToken(request);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RefreshTokenRequestDto request
    ) {
        SecurityRoleSupport.requireAuthenticated(jwt);
        refreshTokenService.revokeRefreshToken(request);
    }

    @GetMapping("/users/{userId}/refresh-tokens")
    public RefreshTokenListResponseDto listUserRefreshTokens(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer userId
    ) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN");
        return RefreshTokenListResponseDto.fromEntities(refreshTokenService.listUserRefreshTokens(userId));
    }

    @GetMapping("/refresh-tokens/{refreshTokenId}")
    public RefreshTokenResponseDto getRefreshToken(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer refreshTokenId
    ) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN");
        RefreshToken refreshToken = refreshTokenService.getRefreshToken(refreshTokenId);
        return RefreshTokenResponseDto.fromEntity(refreshToken);
    }

    @PostMapping("/users/{userId}/refresh-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedRefreshTokenResponseDto issueRefreshToken(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer userId,
            @RequestBody(required = false) CreateRefreshTokenRequestDto request
    ) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN");
        return refreshTokenService.issueRefreshToken(userId, request);
    }
}

