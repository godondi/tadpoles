package com.neueda.leap.mapper;

import com.neueda.leap.domain.AppUser;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface AppUserMapper {
    @Select("""
            SELECT u.user_id AS userId,
                   u.username AS username,
                   u.email AS email,
                   u.password_hash AS passwordHash,
                   u.display_name AS displayName,
                   u.enabled AS enabled,
                   u.created_at AS createdAt,
                   u.updated_at AS updatedAt,
                   a.advisor_id AS advisorId,
                   c.client_id AS clientId
            FROM users u
            LEFT JOIN advisors a
              ON a.user_id = u.user_id
            LEFT JOIN clients c
              ON c.user_id = u.user_id
            WHERE u.user_id = #{id}
            """)
    AppUser getUser(@Param("id") Integer id);

    @Select("""
            SELECT u.user_id AS userId,
                   u.username AS username,
                   u.email AS email,
                   u.password_hash AS passwordHash,
                   u.display_name AS displayName,
                   u.enabled AS enabled,
                   u.created_at AS createdAt,
                   u.updated_at AS updatedAt,
                   a.advisor_id AS advisorId,
                   c.client_id AS clientId
            FROM users u
            LEFT JOIN advisors a
              ON a.user_id = u.user_id
            LEFT JOIN clients c
              ON c.user_id = u.user_id
            WHERE u.username = #{username}
            """)
    AppUser getUserByUsername(@Param("username") String username);

    @Select("""
            SELECT u.user_id AS userId,
                   u.username AS username,
                   u.email AS email,
                   u.password_hash AS passwordHash,
                   u.display_name AS displayName,
                   u.enabled AS enabled,
                   u.created_at AS createdAt,
                   u.updated_at AS updatedAt,
                   a.advisor_id AS advisorId,
                   c.client_id AS clientId
            FROM users u
            LEFT JOIN advisors a
              ON a.user_id = u.user_id
            LEFT JOIN clients c
              ON c.user_id = u.user_id
            ORDER BY u.user_id
            """)
    List<AppUser> listUsers();

    @Select("""
            SELECT r.role_name
            FROM user_roles ur
            JOIN roles r
              ON r.role_id = ur.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY r.role_name
            """)
    List<String> listUserRoles(@Param("userId") Integer userId);

    @Select("""
            SELECT role_name
            FROM roles
            ORDER BY role_name
            """)
    List<String> listExistingRoleNames();

    @UpdateProvider(type = AppUserSqlProvider.class, method = "buildUpdateUser")
    int updateUser(AppUser user);

    @Delete("""
            DELETE FROM user_roles
            WHERE user_id = #{userId}
            """)
    int deleteUserRoles(@Param("userId") Integer userId);

    @Insert("""
            INSERT INTO user_roles (user_id, role_id)
            SELECT #{userId}, role_id
            FROM roles
            WHERE role_name = #{roleName}
            """)
    int insertUserRole(@Param("userId") Integer userId, @Param("roleName") String roleName);

    class AppUserSqlProvider {
        public String buildUpdateUser(AppUser user) {
            List<String> updates = new ArrayList<>();
            if (user.getDisplayName() != null) {
                updates.add("display_name = #{displayName}");
            }
            if (user.getEmail() != null) {
                updates.add("email = #{email}");
            }
            if (user.getEnabled() != null) {
                updates.add("enabled = #{enabled}");
            }
            updates.add("updated_at = CURRENT_TIMESTAMP");
            return "UPDATE users SET "
                    + String.join(", ", updates)
                    + " WHERE user_id = #{userId}";
        }
    }
}
