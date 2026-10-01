package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Role;
import com.neueda.leap.exception.RoleNotFoundException;
import com.neueda.leap.mapper.RoleMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {
    @Mock
    private RoleMapper roleMapper;

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(roleMapper);
    }

    @Test
    void getRoleReturnsRoleWhenFound() {
        when(roleMapper.getRole(1)).thenReturn(buildRole());

        Role result = roleService.getRole(1);

        assertEquals(1, result.getRoleId());
        assertEquals("ADMIN", result.getRoleName());
    }

    @Test
    void listRolesReturnsRoles() {
        when(roleMapper.listRoles()).thenReturn(List.of(buildRole()));

        List<Role> results = roleService.listRoles();

        assertEquals(1, results.size());
        assertEquals("ADMIN", results.get(0).getRoleName());
    }

    @Test
    void getRoleThrowsWhenMissing() {
        when(roleMapper.getRole(99)).thenReturn(null);

        assertThrows(RoleNotFoundException.class, () -> roleService.getRole(99));
    }

    @Test
    void getRoleThrowsOnInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> roleService.getRole(0));
    }

    private Role buildRole() {
        Role role = new Role();
        role.setRoleId(1);
        role.setRoleName("ADMIN");
        return role;
    }
}

