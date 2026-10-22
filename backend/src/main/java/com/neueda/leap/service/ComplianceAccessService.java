package com.neueda.leap.service;

import static com.neueda.leap.trading.TradingData.map;

import com.neueda.leap.dto.ComplianceDtos.*;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.exception.AppUserNotFoundException;
import com.neueda.leap.mapper.ComplianceAccessMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class ComplianceAccessService {
    public record Access(
            int actor, UUID context, String permission, String scopeVersion, Instant now) {}

    private final ComplianceAccessMapper db;
    private final ComplianceAuditService audit;
    private final AppUserService users;
    private final SessionService sessions;
    private final Clock clock;
    private final boolean enabled;
    private final List<String> purposes;
    private final String policyVersion;
    private final String scopeLabel;
    private final int duration;

    public ComplianceAccessService(
            ComplianceAccessMapper db,
            ComplianceAuditService audit,
            AppUserService users,
            SessionService sessions,
            Clock clock,
            @Value("${compliance.access-enabled:false}") boolean enabled,
            @Value("${compliance.allowed-purposes:}") String allowedPurposes,
            @Value("${compliance.policy-version:}") String policyVersion,
            @Value("${compliance.scope-label:Assigned accounts only}") String scopeLabel,
            @Value("${compliance.context-seconds:0}") int duration) {
        this.db = db;
        this.audit = audit;
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
        this.enabled = enabled;
        this.purposes =
                Arrays.stream(allowedPurposes.split(","))
                        .map(String::trim)
                        .filter(value -> value.matches("[A-Z][A-Z0-9_]{1,39}"))
                        .distinct()
                        .toList();
        this.policyVersion = policyVersion;
        this.scopeLabel = scopeLabel;
        this.duration = duration;
    }

    public int actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated Compliance session required");
        }
        if (jwt.getExpiresAt() == null
                || !jwt.getExpiresAt().isAfter(clock.instant())
                || !sessions.valid(jwt)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Session expired or revoked");
        }
        AppUser user;
        try {
            user =
                    jwt.getClaim("userId") instanceof Number id
                            ? users.getUser(id.intValue())
                            : users.getUserByUsername(jwt.getSubject());
        } catch (AppUserNotFoundException failure) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated Compliance session required");
        }
        if (user == null
                || !Boolean.TRUE.equals(user.getEnabled())
                || user.getRoles() == null
                || !user.getRoles().contains("COMPLIANCE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Compliance access required");
        }
        return user.getUserId();
    }

    public AccessPolicy policy() {
        actor();
        return new AccessPolicy(configured(), purposes, policyVersion, scopeLabel);
    }

    @Transactional
    public AccessContext create(ContextRequest request) {
        int actor = actor();
        requireConfiguration();
        if (request == null
                || request.purposeCode() == null
                || !purposes.contains(request.purposeCode())
                || request.caseReference() == null
                || !request.caseReference().trim().matches("[A-Za-z0-9][A-Za-z0-9._:/-]{7,199}")
                || request.caseReference().chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose an allowed purpose and an 8–200 character safe case reference");
        }
        Instant now = clock.instant();
        try {
            List<String> permissions = db.permissions(actor, now);
            if (!permissions.contains("EVIDENCE_READ")) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "No Compliance account scope is assigned");
            }
            UUID id = UUID.randomUUID();
            db.createContext(
                    map(
                            "id",
                            id,
                            "actor",
                            actor,
                            "purpose",
                            request.purposeCode(),
                            "caseReference",
                            request.caseReference().trim(),
                            "now",
                            now,
                            "expires",
                            now.plusSeconds(duration),
                            "policy",
                            policyVersion,
                            "scope",
                            scope(db.allGrantIdentities(actor, now))));
            success(
                    new Access(actor, id, "CONTEXT", scope(permissions), now),
                    "CONTEXT_CREATED",
                    "CONTEXT",
                    id.toString(),
                    request.purposeCode(),
                    1,
                    null);
            return new AccessContext(
                    id,
                    request.purposeCode(),
                    request.caseReference().trim(),
                    scopeLabel,
                    permissions,
                    now.plusSeconds(duration),
                    policyVersion);
        } catch (DataAccessException failure) {
            throw unavailable();
        }
    }

    public Access require(UUID context, String permission) {
        int actor = actor();
        requireConfiguration();
        try {
            Instant now = clock.instant();
            List<String> grants = db.grants(actor, permission, now);
            String scope = scope(grants);
            Access access = new Access(actor, context, permission, scope, now);
            Map<String, Object> details = context == null ? null : db.contextDetails(context, actor);
            String purpose = details == null ? null : Objects.toString(details.get("purpose_code"), null);
            String contextScope =
                    details == null ? null : Objects.toString(details.get("scope_version"), null);
            if (context == null
                    || purpose == null
                    || !purposes.contains(purpose)
                    || !Objects.equals(contextScope, scope(db.allGrantIdentities(actor, now)))
                    || !db.context(context, actor, purpose, policyVersion, now)
                    || grants.isEmpty()) {
                deny(access, "ACCESS", null);
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Purpose context expired or permission unavailable");
            }
            return access;
        } catch (DataAccessException failure) {
            throw unavailable();
        }
    }

    public boolean has(Access access, String permission, int client) {
        return db.has(access.actor(), permission, client, clock.instant());
    }

    public String accountReference(Access access, int client) {
        return db.accountReference(access.actor(), access.permission(), client, clock.instant());
    }

    public String scope(Access access) {
        return scope(db.grants(access.actor(), access.permission(), clock.instant()));
    }

    public String caseReference(Access access) {
        Map<String, Object> details = db.contextDetails(access.context(), access.actor());
        return details == null ? null : Objects.toString(details.get("case_reference"), null);
    }

    public String actorLabel(Access access) {
        String username = db.username(access.actor());
        return username == null ? "user:" + access.actor() : username;
    }

    public String scopeLabel() {
        return scopeLabel;
    }

    public String policyVersion() {
        return policyVersion;
    }

    public void notFound(Access access, String target) {
        deny(access, "RESOURCE", target);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
    }

    public void success(
            Access access,
            String action,
            String type,
            String target,
            String filter,
            int count,
            UUID export) {
        try {
            db.audit(event(access, action, type, target, filter, "SUCCESS", count, export));
        } catch (DataAccessException failure) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Required access audit is unavailable");
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Compliance data source is unavailable");
    }

    public void deny(Access access, String type, String target) {
        audit.denied(event(access, "READ_DENIED", type, target, "", "DENIED", 0, null));
    }

    private Map<String, Object> event(
            Access access,
            String action,
            String type,
            String target,
            String filter,
            String outcome,
            int count,
            UUID export) {
        return map(
                "id",
                UUID.randomUUID(),
                "actor",
                access.actor(),
                "context",
                access.context(),
                "action",
                action,
                "type",
                type,
                "target",
                target,
                "filter",
                digest(filter),
                "scope",
                access.scopeVersion(),
                "outcome",
                outcome,
                "count",
                count,
                "now",
                clock.instant(),
                "correlation",
                UUID.randomUUID(),
                "export",
                export);
    }

    private boolean configured() {
        return enabled
                && !purposes.isEmpty()
                && !policyVersion.isBlank()
                && duration >= 60
                && duration <= 3600;
    }

    private void requireConfiguration() {
        if (!configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Compliance access policy has not been configured");
        }
    }

    private static String scope(Collection<String> grants) {
        return digest(String.join("|", grants));
    }

    public static String digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
