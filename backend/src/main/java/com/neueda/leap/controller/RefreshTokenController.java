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
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("isAuthenticated()")
    public void logout(@RequestBody RefreshTokenRequestDto request) {
        refreshTokenService.revokeRefreshToken(request);
    }

    @GetMapping("/users/{userId}/refresh-tokens")
    @PreAuthorize("hasRole('ADMIN')")
    public RefreshTokenListResponseDto listUserRefreshTokens(
            @PathVariable Integer userId
    ) {
        return RefreshTokenListResponseDto.fromEntities(refreshTokenService.listUserRefreshTokens(userId));
    }

    @GetMapping("/refresh-tokens/{refreshTokenId}")
    @PreAuthorize("hasRole('ADMIN')")
    public RefreshTokenResponseDto getRefreshToken(@PathVariable Integer refreshTokenId) {
        RefreshToken refreshToken = refreshTokenService.getRefreshToken(refreshTokenId);
        return RefreshTokenResponseDto.fromEntity(refreshToken);
    }

    @PostMapping("/users/{userId}/refresh-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public IssuedRefreshTokenResponseDto issueRefreshToken(
            @PathVariable Integer userId,
            @RequestBody(required = false) CreateRefreshTokenRequestDto request
    ) {
        return refreshTokenService.issueRefreshToken(userId, request);
    }
}
