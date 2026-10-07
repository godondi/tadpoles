package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static com.neueda.leap.support.TestSecurityUtils.jwtWithSubjectAndRoles;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.SetAppUserRolesRequestDto;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.AppUserService;
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

@WebMvcTest(AppUserController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class AppUserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppUserService appUserService;

    @Test
    void getCurrentUserReturnsJsonResponse() throws Exception {
        when(appUserService.getUserByUsername("admin01")).thenReturn(buildUser(List.of("ADMIN")));

        mockMvc.perform(get("/api/users/me")
                        .with(jwtWithSubjectAndRoles("admin01", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("admin01"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void listUsersReturnsJsonResponseForAdmin() throws Exception {
        when(appUserService.listUsers()).thenReturn(List.of(buildUser(List.of("ADMIN"))));

        mockMvc.perform(get("/api/users")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].userId").value(1))
                .andExpect(jsonPath("$.users[0].displayName").value("Admin User"));
    }

    @Test
    void getUserReturnsJsonResponseForAdmin() throws Exception {
        when(appUserService.getUser(1)).thenReturn(buildUser(List.of("ADMIN")));

        mockMvc.perform(get("/api/users/1")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("admin01@tadpoles.dev"));
    }

    @Test
    void updateUserReturnsJsonResponseForAdmin() throws Exception {
        AppUser updatedUser = buildUser(List.of("ADMIN"));
        updatedUser.setDisplayName("Admin Updated");
        updatedUser.setEnabled(false);
        when(appUserService.updateUser(eq(1), any())).thenReturn(updatedUser);

        mockMvc.perform(patch("/api/users/1")
                        .with(jwtWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Admin Updated",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Admin Updated"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void setUserRolesReturnsJsonResponseForAdmin() throws Exception {
        AppUser updatedUser = buildUser(List.of("ADMIN", "AUDITOR"));
        when(appUserService.setUserRoles(eq(1), any(SetAppUserRolesRequestDto.class))).thenReturn(updatedUser);

        mockMvc.perform(put("/api/users/1/roles")
                        .with(jwtWithRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roles": ["ADMIN", "AUDITOR"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.roles[1]").value("AUDITOR"));
    }

    @Test
    void getCurrentUserReturnsUnauthorizedWhenMissingToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing token"));
    }

    @Test
    void listUsersReturnsUnauthorizedWhenAdminRoleMissing() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(jwtWithRoles("AUDITOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied"));
    }

    @Test
    void getUserReturnsBadRequestForInvalidId() throws Exception {
        when(appUserService.getUser(0)).thenThrow(new IllegalArgumentException("User id must be a positive integer."));

        mockMvc.perform(get("/api/users/0")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User id must be a positive integer."));
    }

    private AppUser buildUser(List<String> roles) {
        AppUser user = new AppUser();
        user.setUserId(1);
        user.setUsername("admin01");
        user.setEmail("admin01@tadpoles.dev");
        user.setPasswordHash("hash");
        user.setDisplayName("Admin User");
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.of(2026, 9, 24, 8, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 9, 24, 8, 0));
        user.setRoles(roles);
        return user;
    }
}
