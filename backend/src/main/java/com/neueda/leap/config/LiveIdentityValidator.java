package com.neueda.leap.config;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.service.AppUserService;
import java.util.Collection;
import java.util.HashSet;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reject disabled identities and tokens with obsolete memberships before controller authorization. */
public final class LiveIdentityValidator implements OAuth2TokenValidator<Jwt> {
    private final AppUserService users;
    public LiveIdentityValidator(AppUserService users) { this.users = users; }

    @Override public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            Object claim = jwt.getClaims().get("roles");
            if (!(claim instanceof Collection<?> roles) || roles.isEmpty()
                    || roles.stream().anyMatch(role -> !(role instanceof String))) return denied();
            AppUser user = users.getUserByUsername(jwt.getSubject());
            if (!Boolean.TRUE.equals(user.getEnabled()) || user.getRoles() == null
                    || !new HashSet<>(roles).equals(new HashSet<>(user.getRoles()))) return denied();
            Object userId = jwt.getClaims().get("userId");
            if (userId != null && !String.valueOf(user.getUserId()).equals(String.valueOf(userId))) return denied();
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException failure) {
            return denied();
        }
    }
    private OAuth2TokenValidatorResult denied() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Session is no longer valid", null));
    }
}
