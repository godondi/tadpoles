package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.AppUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql"})
class AppUserMapperTest {
    @Autowired
    private AppUserMapper appUserMapper;

    @Test
    void getUserReturnsUserWithAdvisorId() {
        AppUser user = appUserMapper.getUser(4);

        assertNotNull(user);
        assertEquals("advisor01", user.getUsername());
        assertEquals(3, user.getAdvisorId());
    }

    @Test
    void getUserReturnsClientIdWhenLinked() {
        AppUser user = appUserMapper.getUser(14);

        assertNotNull(user);
        assertEquals("client01", user.getUsername());
        assertEquals(7, user.getClientId());
    }

    @Test
    void getUserByUsernameReturnsUser() {
        AppUser user = appUserMapper.getUserByUsername("admin01");

        assertNotNull(user);
        assertEquals(1, user.getUserId());
        assertEquals("admin01@tadpoles.dev", user.getEmail());
    }

    @Test
    void listUsersReturnsRows() {
        List<AppUser> users = appUserMapper.listUsers();

        assertEquals(5, users.size());
        assertEquals(1, users.get(0).getUserId());
    }

    @Test
    void listUserRolesReturnsAssignedRoles() {
        List<String> roles = appUserMapper.listUserRoles(1);

        assertEquals(List.of("ADMIN"), roles);
    }

    @Test
    void updateUserUpdatesRequestedFields() {
        AppUser update = new AppUser();
        update.setUserId(1);
        update.setDisplayName("Admin Updated");
        update.setEmail("admin.updated@tadpoles.dev");
        update.setEnabled(false);

        int rows = appUserMapper.updateUser(update);

        assertEquals(1, rows);
        AppUser stored = appUserMapper.getUser(1);
        assertEquals("Admin Updated", stored.getDisplayName());
        assertEquals("admin.updated@tadpoles.dev", stored.getEmail());
        assertFalse(stored.getEnabled());
    }

    @Test
    void replaceUserRolesReplacesAssignments() {
        int deletedRows = appUserMapper.deleteUserRoles(1);
        int auditorRows = appUserMapper.insertUserRole(1, "AUDITOR");
        int clientRows = appUserMapper.insertUserRole(1, "CLIENT");

        assertEquals(1, deletedRows);
        assertEquals(1, auditorRows);
        assertEquals(1, clientRows);
        assertEquals(List.of("AUDITOR", "CLIENT"), appUserMapper.listUserRoles(1));
    }
}
