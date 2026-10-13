package com.neueda.leap.mapper;

import com.neueda.leap.domain.Role;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper {
    @Select("""
            SELECT role_id AS roleId,
                   role_name AS roleName
            FROM roles
            WHERE role_id = #{id}
            """)
    Role getRole(@Param("id") Integer id);

    @Select("""
            SELECT role_id AS roleId,
                   role_name AS roleName
            FROM roles
            ORDER BY role_id
            """)
    List<Role> listRoles();
}

