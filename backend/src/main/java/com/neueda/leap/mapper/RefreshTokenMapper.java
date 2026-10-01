package com.neueda.leap.mapper;

import com.neueda.leap.domain.RefreshToken;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefreshTokenMapper {
    @Select("""
            SELECT refresh_token_id AS refreshTokenId,
                   user_id AS userId,
                   token_hash AS tokenHash,
                   expires_at AS expiresAt,
                   revoked_at AS revokedAt,
                   created_at AS createdAt
            FROM refresh_tokens
            WHERE refresh_token_id = #{refreshTokenId}
            """)
    RefreshToken getRefreshToken(@Param("refreshTokenId") Integer refreshTokenId);

    @Select("""
            SELECT refresh_token_id AS refreshTokenId,
                   user_id AS userId,
                   token_hash AS tokenHash,
                   expires_at AS expiresAt,
                   revoked_at AS revokedAt,
                   created_at AS createdAt
            FROM refresh_tokens
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, refresh_token_id DESC
            """)
    List<RefreshToken> listUserRefreshTokens(@Param("userId") Integer userId);

    @Select("""
            SELECT refresh_token_id AS refreshTokenId,
                   user_id AS userId,
                   token_hash AS tokenHash,
                   expires_at AS expiresAt,
                   revoked_at AS revokedAt,
                   created_at AS createdAt
            FROM refresh_tokens
            WHERE token_hash = #{tokenHash}
            """)
    RefreshToken getRefreshTokenByTokenHash(@Param("tokenHash") String tokenHash);

    @Insert("""
            INSERT INTO refresh_tokens (
                user_id,
                token_hash,
                expires_at
            )
            VALUES (
                #{userId},
                #{tokenHash},
                #{expiresAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "refreshTokenId", keyColumn = "refresh_token_id")
    int insertRefreshToken(RefreshToken refreshToken);

    @Update("""
            UPDATE refresh_tokens
            SET revoked_at = #{revokedAt}
            WHERE refresh_token_id = #{refreshTokenId}
              AND revoked_at IS NULL
            """)
    int revokeRefreshToken(@Param("refreshTokenId") Integer refreshTokenId, @Param("revokedAt") LocalDateTime revokedAt);
}

