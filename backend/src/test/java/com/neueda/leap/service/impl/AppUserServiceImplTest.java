package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.SetAppUserRolesRequestDto;
import com.neueda.leap.dto.UpdateAppUserRequestDto;
import com.neueda.leap.exception.AppUserNotFoundException;
import com.neueda.leap.mapper.AppUserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppUserServiceImplTest {
    @Mock
    private AppUserMapper appUserMapper;

    private AppUserServiceImpl appUserService;

    @BeforeEach
    void setUp() {
        appUserService = new AppUserServiceImpl(appUserMapper);
    }

    @Test
    void getUserReturnsUserWhenFound() {
        when(appUserMapper.getUser(1)).thenReturn(buildUser());
        when(appUserMapper.listUserRoles(1)).thenReturn(List.of("ADMIN"));

        AppUser result = appUserService.getUser(1);

        assertEquals(1, result.getUserId());
        assertEquals(List.of("ADMIN"), result.getRoles());
    }

    @Test
    void getUserByUsernameReturnsUserWhenFound() {
        when(appUserMapper.getUserByUsername("admin01")).thenReturn(buildUser());
        when(appUserMapper.listUserRoles(1)).thenReturn(List.of("ADMIN"));

        AppUser result = appUserService.getUserByUsername("admin01");

        assertEquals("admin01", result.getUsername());
        assertEquals(List.of("ADMIN"), result.getRoles());
    }

    @Test
    void listUsersReturnsUsersWithRoles() {
        when(appUserMapper.listUsers()).thenReturn(List.of(buildUser()));
        when(appUserMapper.listUserRoles(1)).thenReturn(List.of("ADMIN"));

        List<AppUser> results = appUserService.listUsers();

        assertEquals(1, results.size());
        assertEquals(List.of("ADMIN"), results.get(0).getRoles());
    }

    @Test
    void updateUserReturnsUpdatedUser() {
        UpdateAppUserRequestDto request = new UpdateAppUserRequestDto(
                "Admin Updated",
                "admin.updated@tadpoles.dev",
                false
        );
        AppUser updatedUser = buildUser();
        updatedUser.setDisplayName("Admin Updated");
        updatedUser.setEmail("admin.updated@tadpoles.dev");
        updatedUser.setEnabled(false);

        when(appUserMapper.updateUser(any())).thenReturn(1);
        when(appUserMapper.getUser(1)).thenReturn(updatedUser);
        when(appUserMapper.listUserRoles(1)).thenReturn(List.of("ADMIN"));

        AppUser result = appUserService.updateUser(1, request);

        assertEquals("Admin Updated", result.getDisplayName());
        assertEquals(false, result.getEnabled());
    }

    @Test
    void setUserRolesNormalizesAndReturnsUpdatedUser() {
        SetAppUserRolesRequestDto request = new SetAppUserRolesRequestDto(List.of(" auditor ", "client"));

        when(appUserMapper.getUser(1)).thenReturn(buildUser(), buildUser());
        when(appUserMapper.listUserRoles(1)).thenReturn(List.of("ADMIN"), List.of("AUDITOR", "CLIENT"));
        when(appUserMapper.listExistingRoleNames()).thenReturn(List.of("ADMIN", "AUDITOR", "CLIENT"));
        when(appUserMapper.insertUserRole(1, "AUDITOR")).thenReturn(1);
        when(appUserMapper.insertUserRole(1, "CLIENT")).thenReturn(1);

        AppUser result = appUserService.setUserRoles(1, request);

        verify(appUserMapper).deleteUserRoles(1);
        verify(appUserMapper).insertUserRole(1, "AUDITOR");
        verify(appUserMapper).insertUserRole(1, "CLIENT");
        assertEquals(List.of("AUDITOR", "CLIENT"), result.getRoles());
    }

    @Test
    void getUserThrowsWhenMissing() {
        when(appUserMapper.getUser(99)).thenReturn(null);

        assertThrows(AppUserNotFoundException.class, () -> appUserService.getUser(99));
    }

    @Test
    void updateUserThrowsWhenNoFieldsProvided() {
        UpdateAppUserRequestDto request = new UpdateAppUserRequestDto(null, null, null);

        assertThrows(IllegalArgumentException.class, () -> appUserService.updateUser(1, request));
    }

    @Test
    void setUserRolesThrowsOnUnknownRole() {
        when(appUserMapper.getUser(1)).thenReturn(buildUser());
        when(appUserMapper.listUserRoles(1)).thenReturn(List.of("ADMIN"));
        when(appUserMapper.listExistingRoleNames()).thenReturn(List.of("ADMIN", "AUDITOR", "CLIENT"));

        assertThrows(IllegalArgumentException.class,
                () -> appUserService.setUserRoles(1, new SetAppUserRolesRequestDto(List.of("SUPERUSER"))));
    }

    private AppUser buildUser() {
        AppUser user = new AppUser();
        user.setUserId(1);
        user.setUsername("admin01");
        user.setEmail("admin01@tadpoles.dev");
        user.setPasswordHash("hash");
        user.setDisplayName("Admin User");
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.of(2026, 9, 24, 8, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 9, 24, 8, 0));
        return user;
    }
}
