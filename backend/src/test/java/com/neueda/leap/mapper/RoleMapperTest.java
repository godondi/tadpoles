package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql"})
class RoleMapperTest {
    @Autowired
    private RoleMapper roleMapper;

    @Test
    void getRoleReturnsRole() {
        Role role = roleMapper.getRole(1);

        assertNotNull(role);
        assertEquals("ADMIN", role.getRoleName());
    }

    @Test
    void listRolesReturnsRowsOrderedByRoleId() {
        List<Role> roles = roleMapper.listRoles();

        assertEquals(5, roles.size());
        assertEquals(1, roles.get(0).getRoleId());
        assertEquals("CLIENT", roles.get(4).getRoleName());
    }
}

