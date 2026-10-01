package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.neueda.leap.domain.RefreshToken;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql"})
class RefreshTokenMapperTest {
    @Autowired
    private RefreshTokenMapper refreshTokenMapper;

    @Test
    void getRefreshTokenReturnsToken() {
        RefreshToken refreshToken = refreshTokenMapper.getRefreshToken(21);

        assertNotNull(refreshToken);
        assertEquals(1, refreshToken.getUserId());
        assertEquals("seed-token-hash-1", refreshToken.getTokenHash());
        assertNull(refreshToken.getRevokedAt());
    }

    @Test
    void listUserRefreshTokensReturnsRows() {
        List<RefreshToken> refreshTokens = refreshTokenMapper.listUserRefreshTokens(1);

        assertEquals(1, refreshTokens.size());
        assertEquals(21, refreshTokens.get(0).getRefreshTokenId());
    }

    @Test
    void getRefreshTokenByTokenHashReturnsMatchingRow() {
        RefreshToken refreshToken = refreshTokenMapper.getRefreshTokenByTokenHash("seed-token-hash-1");

        assertNotNull(refreshToken);
        assertEquals(21, refreshToken.getRefreshTokenId());
    }

    @Test
    void insertRefreshTokenCreatesNewRowWithGeneratedId() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(4);
        refreshToken.setTokenHash("inserted-token-hash");
        refreshToken.setExpiresAt(LocalDateTime.of(2026, 10, 30, 8, 0));

        int rows = refreshTokenMapper.insertRefreshToken(refreshToken);

        assertEquals(1, rows);
        assertNotNull(refreshToken.getRefreshTokenId());
        RefreshToken stored = refreshTokenMapper.getRefreshToken(refreshToken.getRefreshTokenId());
        assertEquals("inserted-token-hash", stored.getTokenHash());
    }

    @Test
    void revokeRefreshTokenSetsRevokedAtTimestamp() {
        LocalDateTime revokedAt = LocalDateTime.of(2026, 9, 30, 12, 0);

        int rows = refreshTokenMapper.revokeRefreshToken(21, revokedAt);

        assertEquals(1, rows);
        RefreshToken stored = refreshTokenMapper.getRefreshToken(21);
        assertEquals(revokedAt, stored.getRevokedAt());
    }
}

