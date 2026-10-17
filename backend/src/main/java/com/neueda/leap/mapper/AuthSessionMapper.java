package com.neueda.leap.mapper;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuthSessionMapper {
    @Insert("INSERT INTO auth_sessions VALUES(#{sid},#{user},#{expires},NULL,#{now})")
    void create(
            @Param("sid") UUID sid,
            @Param("user") int user,
            @Param("expires") Instant expires,
            @Param("now") Instant now);

    @Select(
            """
            SELECT EXISTS
                (SELECT 1
                 FROM auth_sessions
                 WHERE session_id = #{sid}
                     AND user_id = #{user}
                     AND revoked_at IS NULL
                     AND expires_at > #{now})
            """)
    boolean valid(@Param("sid") UUID sid, @Param("user") int user, @Param("now") Instant now);

    @Update(
            """
            UPDATE auth_sessions
            SET revoked_at = #{now}
            WHERE session_id = #{sid}
                AND user_id = #{user}
                AND revoked_at IS NULL
            """)
    void revoke(@Param("sid") UUID sid, @Param("user") int user, @Param("now") Instant now);
}
