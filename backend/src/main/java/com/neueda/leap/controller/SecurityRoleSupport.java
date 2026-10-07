package com.neueda.leap.controller;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.service.AppUserService;
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

    static void requireClientOwnership(Jwt jwt, AppUserService appUserService, Integer clientId) {
        requireAuthenticated(jwt);
        if (!hasAnyRole(jwt, "CLIENT")) {
            return;
        }

        AppUser currentUser = resolveCurrentUser(jwt, appUserService);
        if (currentUser.getClientId() == null || !currentUser.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clients may only access their own records.");
        }
    }

    static AppUser resolveCurrentUser(Jwt jwt, AppUserService appUserService) {
        requireAuthenticated(jwt);

        Integer userId = resolvePositiveIntegerClaim(jwt, "userId");
        if (userId != null) {
            return appUserService.getUser(userId);
        }

        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            return appUserService.getUserByUsername(subject.trim());
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
    }

    static boolean hasAnyRole(Jwt jwt, String... requiredRoles) {
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

    private static Integer resolvePositiveIntegerClaim(Jwt jwt, String claimName) {
        Object claimValue = jwt.getClaims().get(claimName);
        if (claimValue instanceof Number number) {
            int value = number.intValue();
            return value > 0 ? value : null;
        }
        if (claimValue instanceof String stringValue && !stringValue.isBlank()) {
            try {
                int value = Integer.parseInt(stringValue.trim());
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase();
    }
}
