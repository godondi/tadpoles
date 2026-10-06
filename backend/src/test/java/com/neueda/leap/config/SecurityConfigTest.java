package com.neueda.leap.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class SecurityConfigTest {
    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void jwtSigningKeyRejectsSecretShorterThan32Bytes() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> securityConfig.jwtSigningKey("short-secret")
        );

        assertTrue(exception.getMessage().contains("at least 32 bytes"));
    }

    @Test
    void jwtSigningKeyAcceptsSecretThatIsAtLeast32Bytes() {
        String secret = "test-jwt-secret-test-jwt-secret-123456";

        SecretKey secretKey = securityConfig.jwtSigningKey(secret);

        assertEquals("HmacSHA256", secretKey.getAlgorithm());
        assertArrayEquals(secret.getBytes(StandardCharsets.UTF_8), secretKey.getEncoded());
    }
}

