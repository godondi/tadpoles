package com.neueda.leap.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class TestSecurityUtils {
    private TestSecurityUtils() {
    }

    public static RequestPostProcessor jwtWithRoles(String... roles) {
        return jwtWithSubjectAndRoles(null, roles);
    }

    public static RequestPostProcessor jwtWithSubjectAndRoles(String subject, String... roles) {
        List<String> roleClaims = List.of(roles);
        List<SimpleGrantedAuthority> authorities = Stream.of(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        return jwt()
                .authorities(authorities.toArray(SimpleGrantedAuthority[]::new))
                .jwt(token -> {
                    if (subject != null) {
                        token.subject(subject);
                    }
                    token.claim("roles", roleClaims);
                });
    }
}


