package com.neueda.leap.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.Role;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.RoleService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoleController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class RoleControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleService roleService;

    @Test
    void listRolesReturnsJsonResponseForAdmin() throws Exception {
        when(roleService.listRoles()).thenReturn(List.of(buildRole()));

        mockMvc.perform(get("/api/roles")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].roleId").value(1))
                .andExpect(jsonPath("$.roles[0].roleName").value("ADMIN"));
    }

    @Test
    void getRoleReturnsJsonResponseForAdmin() throws Exception {
        when(roleService.getRole(1)).thenReturn(buildRole());

        mockMvc.perform(get("/api/roles/1")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(1))
                .andExpect(jsonPath("$.roleName").value("ADMIN"));
    }

    @Test
    void listRolesReturnsUnauthorizedWhenAdminRoleMissing() throws Exception {
        mockMvc.perform(get("/api/roles")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("AUDITOR")))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("ADMIN role required"));
    }

    private Role buildRole() {
        Role role = new Role();
        role.setRoleId(1);
        role.setRoleName("ADMIN");
        return role;
    }
}

