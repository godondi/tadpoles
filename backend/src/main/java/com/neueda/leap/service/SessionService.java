package com.neueda.leap.service;

import com.neueda.leap.mapper.AuthSessionMapper;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    private final AuthSessionMapper db;
    private final SecretKey key;
    private final Clock clock;
    private final String issuer, audience;

    public SessionService(
            AuthSessionMapper db,
            SecretKey key,
            Clock clock,
            @Value("${jwt.issuer:tadpoles}") String issuer,
            @Value("${jwt.audience:tadpoles-workspace}") String audience) {
        this.db = db;
        this.key = key;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
    }

    @Transactional
    public String issue(String username, List<String> roles, int user, Integer client) {
        UUID sid = UUID.randomUUID();
        var now = clock.instant();
        var expires = now.plusSeconds(3600);
        db.create(sid, user, expires, now);
        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .id(sid.toString())
                .claim("sid", sid.toString())
                .claim("userId", user)
                .claim("clientId", client)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
    }

    public boolean valid(Jwt jwt) {
        try {
            return issuer.equals(jwt.getClaimAsString("iss"))
                    && jwt.getAudience().contains(audience)
                    && db.valid(
                            UUID.fromString(jwt.getClaimAsString("sid")),
                            ((Number) jwt.getClaims().get("userId")).intValue(),
                            clock.instant());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void revoke(Jwt jwt) {
        db.revoke(
                UUID.fromString(jwt.getClaimAsString("sid")),
                ((Number) jwt.getClaims().get("userId")).intValue(),
                clock.instant());
    }
}
