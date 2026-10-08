package com.neueda.leap.service.impl;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.AuthTokensResponseDto;
import com.neueda.leap.dto.IssuedRefreshTokenResponseDto;
import com.neueda.leap.dto.LoginRequestDto;
import com.neueda.leap.mapper.UserMapper;
import com.neueda.leap.service.AuthService;
import com.neueda.leap.service.RefreshTokenService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthServiceImpl(
            UserMapper userMapper,
            RefreshTokenService refreshTokenService,
            AccessTokenService accessTokenService
    ) {
        this.userMapper = userMapper;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenService = accessTokenService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public AuthTokensResponseDto login(LoginRequestDto request) {
        if (request == null || request.username() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required");
        }

        AppUser user = userMapper.findByUsername(request.username());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account is disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        List<String> roles = userMapper.findRolesByUserId(user.getUserId());
        IssuedRefreshTokenResponseDto refreshToken = refreshTokenService.issueRefreshToken(user.getUserId(), null);
        String accessToken = accessTokenService.generateAccessToken(user, roles, refreshToken.refreshTokenId());

        return new AuthTokensResponseDto(
                accessToken,
                refreshToken.refreshToken(),
                refreshToken.tokenType(),
                accessTokenService.getAccessTokenTtlSeconds()
        );
    }
}
