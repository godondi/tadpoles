package com.neueda.leap.config;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.service.AppUserService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveIdentityValidatorTest {
    @Test void persistedMembershipAndEnabledStateOverrideOldToken() {
        AppUserService users = mock(AppUserService.class);
        AppUser user = new AppUser(); user.setUserId(1); user.setEnabled(true); user.setRoles(List.of("FINANCE"));
        when(users.getUserByUsername("finance")).thenReturn(user);
        Jwt token = Jwt.withTokenValue("test").header("alg", "HS256").subject("finance")
                .claim("userId", 1).claim("roles", List.of("FINANCE")).issuedAt(Instant.now()).build();
        LiveIdentityValidator validator = new LiveIdentityValidator(users);
        assertFalse(validator.validate(token).hasErrors());
        user.setRoles(List.of("GUEST"));
        assertTrue(validator.validate(token).hasErrors());
        user.setRoles(List.of("FINANCE")); user.setEnabled(false);
        assertTrue(validator.validate(token).hasErrors());
    }
    @Test void realSignedTokensAreRejectedAfterDisableOrMembershipChange() {
        AppUserService users=mock(AppUserService.class);
        AppUser user=new AppUser();user.setUserId(1);user.setEnabled(true);user.setRoles(List.of("FINANCE"));
        when(users.getUserByUsername("finance")).thenReturn(user);
        var beans=new org.springframework.beans.factory.support.DefaultListableBeanFactory();beans.registerSingleton("users",users);
        SecurityConfig config=new SecurityConfig();var key=config.jwtSigningKey("test-jwt-secret-test-jwt-secret-123456");
        var decoder=config.jwtDecoder(key,beans.getBeanProvider(AppUserService.class));
        String token=io.jsonwebtoken.Jwts.builder().subject("finance").claim("userId",1).claim("roles",List.of("FINANCE"))
            .expiration(java.util.Date.from(Instant.now().plusSeconds(600))).signWith(key).compact();
        assertEquals("finance",decoder.decode(token).getSubject());
        user.setRoles(List.of("GUEST"));assertThrows(org.springframework.security.oauth2.jwt.JwtValidationException.class,()->decoder.decode(token));
        user.setRoles(List.of("FINANCE"));user.setEnabled(false);assertThrows(org.springframework.security.oauth2.jwt.JwtValidationException.class,()->decoder.decode(token));
        user.setEnabled(true);
        String expired=io.jsonwebtoken.Jwts.builder().subject("finance").claim("roles",List.of("FINANCE"))
            .expiration(java.util.Date.from(Instant.now().minusSeconds(120))).signWith(key).compact();
        assertThrows(org.springframework.security.oauth2.jwt.JwtValidationException.class,()->decoder.decode(expired));
    }

    @Test void converterRequiresCollectionOfStringRoles() {
        var converter = new SecurityConfig().jwtAuthenticationConverter();
        assertTrue(converter.convert(Jwt.withTokenValue("test").header("alg", "HS256").claim("roles", "FINANCE").build()).getAuthorities().isEmpty());
        assertEquals("ROLE_FINANCE", converter.convert(Jwt.withTokenValue("test").header("alg", "HS256").claim("roles", List.of("FINANCE")).build()).getAuthorities().iterator().next().getAuthority());
    }
}
