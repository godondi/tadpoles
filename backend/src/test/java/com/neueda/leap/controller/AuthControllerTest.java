package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.dto.AuthTokensResponseDto;
import com.neueda.leap.dto.ClientRegistrationResponseDto;
import com.neueda.leap.dto.CreateUserResponseDto;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.AuthService;
import com.neueda.leap.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    @Test
    void loginReturnsAccessAndRefreshTokens() throws Exception {
        when(authService.login(any())).thenReturn(new AuthTokensResponseDto(
                "access-token",
                "refresh-token",
                "Bearer",
                3600L
        ));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "client01",
                                  "password": "client-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));
    }

    @Test
    void registerClientAllowsAnonymousRegistration() throws Exception {
        when(userService.registerClient(any())).thenReturn(
                new ClientRegistrationResponseDto(14, "client14", "Client Fourteen", "CLIENT", 7)
        );

        mockMvc.perform(post("/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "client14",
                                  "password": "client-password",
                                  "email": "client14@tadpoles.dev",
                                  "displayName": "Client Fourteen",
                                  "clientName": "Client Fourteen"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(14))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.clientId").value(7));
    }

    @Test
    void registerAdminUserStillRequiresAdminRole() throws Exception {
        when(userService.registerUser(any())).thenReturn(
                new CreateUserResponseDto(1, "admin01", "Admin User", "ADMIN", null)
        );

        mockMvc.perform(post("/auth/register")
                        .with(jwtWithRoles("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin01",
                                  "password": "password123",
                                  "email": "admin01@tadpoles.dev",
                                  "displayName": "Admin User",
                                  "roleType": "ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied"));
    }
}
