package com.neueda.leap.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.neueda.leap.mapper.AuthSessionMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class SessionServiceTest {
    @Test
    void issuedSignedSessionChecksIssuerAudienceOwnerAndRevocation() {
        var db = mock(AuthSessionMapper.class);
        var key =
                new SecretKeySpec(
                        "session-unit-test-secret-at-least-32-bytes"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "HmacSHA256");
        var clock = Clock.systemUTC();
        var service = new SessionService(db, key, clock, "test-issuer", "test-audience");
        String encoded = service.issue("client", List.of("CLIENT"), 7, 11);
        var jwt = NimbusJwtDecoder.withSecretKey(key).build().decode(encoded);
        assertEquals("test-issuer", jwt.getClaimAsString("iss"));
        assertEquals(List.of("test-audience"), jwt.getAudience());
        UUID sid = UUID.fromString(jwt.getClaimAsString("sid"));
        verify(db).create(eq(sid), eq(7), any(), any());
        when(db.valid(eq(sid), eq(7), any())).thenReturn(true);
        assertTrue(service.valid(jwt));
        for (String claim : List.of("iss", "aud", "userId")) {
            var altered =
                    Jwt.withTokenValue("test")
                            .headers(h -> h.putAll(jwt.getHeaders()))
                            .claims(c -> c.putAll(jwt.getClaims()))
                            .claim(
                                    claim,
                                    claim.equals("aud")
                                            ? List.of("other")
                                            : claim.equals("userId") ? 8 : "other")
                            .build();
            assertFalse(service.valid(altered), claim);
        }
        service.revoke(jwt);
        verify(db).revoke(eq(sid), eq(7), any());
        when(db.valid(eq(sid), eq(7), any())).thenReturn(false);
        assertFalse(service.valid(jwt));
    }
}
