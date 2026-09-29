package com.neueda.leap.service.impl;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.LoginRequestDto;
import com.neueda.leap.dto.LoginResponseDto;
import com.neueda.leap.mapper.UserMapper;
import com.neueda.leap.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private static final long TOKEN_EXPIRATION_MS = 3600000; // 1 hour
    private final UserMapper userMapper;
    private final String jwtSecret;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthServiceImpl(
            UserMapper userMapper,
            @Value("${jwt.secret}") String jwtSecret
    ) {
        this.userMapper = userMapper;
        this.jwtSecret = jwtSecret;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public LoginResponseDto login(LoginRequestDto request) {
        if (request == null || request.username() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required");
        }

        // Query database for user
        AppUser user = userMapper.findByUsername(request.username());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        // Check if user is enabled
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account is disabled");
        }

        // Validate password against BCrypt hash
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        // Get user roles from database
        List<String> roles = userMapper.findRolesByUserId(user.getUserId());

        // Generate JWT token
        String token = generateToken(user.getUsername(), roles);
        return new LoginResponseDto(token, "Bearer", TOKEN_EXPIRATION_MS / 1000);
    }

    private String generateToken(String username, List<String> roles) {
        SecretKeySpec keySpec = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8),
                SignatureAlgorithm.HS256.getJcaName()
        );

        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRATION_MS))
                .signWith(keySpec, SignatureAlgorithm.HS256)
                .compact();
    }
}
