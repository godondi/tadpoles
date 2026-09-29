package com.neueda.leap.mapper;

import com.neueda.leap.domain.AppUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("""
            SELECT user_id AS userId,
                   username,
                   email,
                   password_hash AS passwordHash,
                   display_name AS displayName,
                   enabled,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM users
            WHERE username = #{username}
            """)
    AppUser findByUsername(@Param("username") String username);

    @Select("""
            SELECT role_name
            FROM roles
            WHERE role_id IN (
                SELECT role_id FROM user_roles WHERE user_id = #{userId}
            )
            """)
    List<String> findRolesByUserId(@Param("userId") Integer userId);

    @Insert("""
            INSERT INTO users (username, email, password_hash, display_name, enabled, created_at, updated_at)
            VALUES (#{username}, #{email}, #{passwordHash}, #{displayName}, #{enabled}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "userId", keyColumn = "user_id")
    int insertUser(AppUser user);

    @Insert("""
            INSERT INTO user_roles (user_id, role_id)
            SELECT #{userId}, role_id FROM roles WHERE role_name = #{roleName}
            """)
    int assignRole(@Param("userId") Integer userId, @Param("roleName") String roleName);
}

