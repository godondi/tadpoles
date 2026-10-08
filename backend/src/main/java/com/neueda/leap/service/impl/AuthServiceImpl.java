package com.neueda.leap.service.impl;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.ClientProfile;
import com.neueda.leap.dto.LoginRequestDto;
import com.neueda.leap.dto.LoginResponseDto;
import com.neueda.leap.dto.SignupRequestDto;
import com.neueda.leap.dto.SignupResponseDto;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.mapper.ClientProfileMapper;
import com.neueda.leap.mapper.UserMapper;
import com.neueda.leap.service.AuthService;
import com.neueda.leap.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private static final long TOKEN_EXPIRATION_MS = 3600000; // 1 hour
    private final UserMapper userMapper;
    private final UserService userService;
    private final ClientProfileMapper clientProfileMapper;
    private final ClientMapper clientMapper;
    private final SecretKey jwtSigningKey;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthServiceImpl(
            UserMapper userMapper,
            UserService userService,
            ClientProfileMapper clientProfileMapper,
            ClientMapper clientMapper,
            SecretKey jwtSigningKey
    ) {
        this.userMapper = userMapper;
        this.userService = userService;
        this.clientProfileMapper = clientProfileMapper;
        this.clientMapper = clientMapper;
        this.jwtSigningKey = jwtSigningKey;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public LoginResponseDto login(LoginRequestDto request) {
        if (request == null || request.username() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required");
        }

        // Query database for user
        String normalizedUsername = normalizeLoginIdentifier(request.username());
        AppUser user = userMapper.findByUsername(normalizedUsername);
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
        SessionMetadata sessionMetadata = resolveSessionMetadata(user, roles);

        // Generate JWT token
        String token = generateToken(user.getUsername(), roles, user.getUserId(), sessionMetadata.clientId());
        return new LoginResponseDto(
                token,
                "Bearer",
                TOKEN_EXPIRATION_MS / 1000,
                user.getUserId(),
                user.getEmail(),
                user.getDisplayName(),
                sessionMetadata.clientId(),
                sessionMetadata.onboardingComplete()
        );
    }

    @Override
    public SignupResponseDto signup(SignupRequestDto request) {
        AppUser newUser = userService.createClientAccount(request);
        List<String> roles = List.of("CLIENT");
        String token = generateToken(newUser.getUsername(), roles, newUser.getUserId(), null);

        return new SignupResponseDto(
                newUser.getUserId(),
                newUser.getEmail(),
                newUser.getDisplayName(),
                "CLIENT",
                null,
                false,
                token,
                "Bearer",
                TOKEN_EXPIRATION_MS / 1000
        );
    }

    private SessionMetadata resolveSessionMetadata(AppUser user, List<String> roles) {
        if (roles.stream().noneMatch("CLIENT"::equalsIgnoreCase)) {
            return new SessionMetadata(null, true);
        }

        ClientProfile profile = clientProfileMapper.findByUserId(user.getUserId());
        if (profile != null) {
            return new SessionMetadata(profile.getClientId(), Boolean.TRUE.equals(profile.getOnboardingComplete()));
        }

        Client client = clientMapper.getClientByUserId(user.getUserId());
        return new SessionMetadata(client == null ? null : client.getClientId(), client != null);
    }

    private String generateToken(String username, List<String> roles, Integer userId, Integer clientId) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRATION_MS))
                .signWith(jwtSigningKey, SignatureAlgorithm.HS256);

        if (clientId != null) {
            builder.claim("clientId", clientId);
        }

        return builder.compact();
    }

    private String normalizeLoginIdentifier(String username) {
        String normalizedUsername = username.trim();
        return normalizedUsername.contains("@") ? normalizedUsername.toLowerCase() : normalizedUsername;
    }

    private record SessionMetadata(Integer clientId, Boolean onboardingComplete) {
    }
}
