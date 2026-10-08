package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.domain.ClientProfile;
import com.neueda.leap.dto.LoginRequestDto;
import com.neueda.leap.dto.LoginResponseDto;
import com.neueda.leap.dto.SignupRequestDto;
import com.neueda.leap.dto.SignupResponseDto;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.mapper.ClientProfileMapper;
import com.neueda.leap.mapper.UserMapper;
import com.neueda.leap.service.UserService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserService userService;
    @Mock
    private ClientProfileMapper clientProfileMapper;
    @Mock
    private ClientMapper clientMapper;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        SecretKey jwtSigningKey = new SecretKeySpec(
                "test-jwt-secret-test-jwt-secret-123456".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        authService = new AuthServiceImpl(userMapper, userService, clientProfileMapper, clientMapper, jwtSigningKey);
    }

    @Test
    void loginReturnsTokenAndOnboardingState() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        AppUser user = new AppUser();
        user.setUserId(14);
        user.setUsername("client01@tadpoles.dev");
        user.setEmail("client01@tadpoles.dev");
        user.setDisplayName("Client One");
        user.setEnabled(true);
        user.setPasswordHash(passwordEncoder.encode("StrongPassword123!"));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        ClientProfile profile = new ClientProfile();
        profile.setUserId(14);
        profile.setClientId(7);
        profile.setOnboardingComplete(true);

        when(userMapper.findByUsername("client01@tadpoles.dev")).thenReturn(user);
        when(userMapper.findRolesByUserId(14)).thenReturn(List.of("CLIENT"));
        when(clientProfileMapper.findByUserId(14)).thenReturn(profile);

        LoginResponseDto response = authService.login(new LoginRequestDto("client01@tadpoles.dev", "StrongPassword123!"));

        assertNotNull(response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(14, response.userId());
        assertEquals(7, response.clientId());
        assertEquals(true, response.onboardingComplete());
    }

    @Test
    void loginRejectsInvalidPassword() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        AppUser user = new AppUser();
        user.setUserId(14);
        user.setUsername("client01@tadpoles.dev");
        user.setEnabled(true);
        user.setPasswordHash(passwordEncoder.encode("StrongPassword123!"));

        when(userMapper.findByUsername("client01@tadpoles.dev")).thenReturn(user);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequestDto("client01@tadpoles.dev", "WrongPassword123!"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void signupReturnsAutoLoginSession() {
        AppUser user = new AppUser();
        user.setUserId(24);
        user.setUsername("jane@example.com");
        user.setEmail("jane@example.com");
        user.setDisplayName("Jane Doe");

        when(userService.createClientAccount(new SignupRequestDto("jane@example.com", "StrongPassword123!", "Jane Doe")))
                .thenReturn(user);

        SignupResponseDto response = authService.signup(new SignupRequestDto("jane@example.com", "StrongPassword123!", "Jane Doe"));

        assertEquals(24, response.userId());
        assertEquals("CLIENT", response.role());
        assertFalse(response.onboardingComplete());
        assertNotNull(response.token());
    }
}

