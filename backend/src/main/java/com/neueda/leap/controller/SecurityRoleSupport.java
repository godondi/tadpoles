package com.neueda.leap.controller;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

final class SecurityRoleSupport {
    private SecurityRoleSupport() {
    }

    static void requireAuthenticated(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        }
    }

    static void requireAnyRole(Jwt jwt, String... requiredRoles) {
        requireAuthenticated(jwt);
        if (!hasAnyRole(jwt, requiredRoles)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, buildRoleMessage(requiredRoles));
        }
    }

    private static boolean hasAnyRole(Jwt jwt, String... requiredRoles) {
        Set<String> normalizedRequiredRoles = new HashSet<>();
        Arrays.stream(requiredRoles)
                .map(SecurityRoleSupport::normalizeRole)
                .forEach(normalizedRequiredRoles::add);

        Object rolesClaim = jwt.getClaims().get("roles");
        if (rolesClaim instanceof Collection<?> roles) {
            return roles.stream()
                    .map(String::valueOf)
                    .map(SecurityRoleSupport::normalizeRole)
                    .anyMatch(normalizedRequiredRoles::contains);
        }

        if (rolesClaim != null) {
            return normalizedRequiredRoles.contains(normalizeRole(String.valueOf(rolesClaim)));
        }

        return false;
    }

    private static String buildRoleMessage(String... requiredRoles) {
        return String.join(" or ", requiredRoles) + " role required";
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase();
    }
}
