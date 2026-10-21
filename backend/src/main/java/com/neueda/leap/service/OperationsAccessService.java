package com.neueda.leap.service;

import static com.neueda.leap.trading.TradingData.map;

import com.neueda.leap.dto.OperationsDtos.*;
import com.neueda.leap.mapper.OperationsAccessMapper;

import org.springframework.beans.factory.annotation.Value;
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
public class OperationsAccessService {
    public record Access(
            int actor,
            UUID context,
            String permission,
            String scopeVersion,
            Instant now,
            Integer managedClient,
            Integer managedOrder) {
        public Access(
                int actor, UUID context, String permission, String scopeVersion, Instant now) {
            this(actor, context, permission, scopeVersion, now, null, null);
        }

        public Map<String, Object> parameters() {
            return map(
                    "actor",
                    actor,
                    "permission",
                    permission,
                    "now",
                    now,
                    "managedClient",
                    managedClient,
                    "managedOrder",
                    managedOrder);
        }
    }

    private final OperationsAccessMapper db;
    private final OperationsAuditService audit;
    private final AppUserService users;
    private final Clock clock;
    private final SessionService sessions;
    private final boolean enabled;
    private final List<String> purposes;
    private final String policyVersion;
    private final int duration;

    public OperationsAccessService(
            OperationsAccessMapper db,
            OperationsAuditService audit,
            AppUserService users,
            Clock clock,
            SessionService sessions,
            @Value("${operations.access-enabled:false}") boolean enabled,
            @Value("${operations.allowed-purposes:}") String purposes,
            @Value("${operations.policy-version:}") String policyVersion,
            @Value("${operations.context-seconds:0}") int duration) {
        this.db = db;
        this.audit = audit;
        this.users = users;
        this.clock = clock;
        this.sessions = sessions;
        this.enabled = enabled;
        this.purposes =
                Arrays.stream(purposes.split(","))
                        .map(String::trim)
                        .filter(s -> s.matches("[A-Z][A-Z0-9_]{1,39}"))
                        .distinct()
                        .toList();
        this.policyVersion = policyVersion;
        this.duration = duration;
    }

    /** Also enforced for calls that do not originate in a controller. */
    public int actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated staff session required");
        if (jwt.getExpiresAt() == null
                || !jwt.getExpiresAt().isAfter(clock.instant())
                || !sessions.valid(jwt))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Session expired or revoked");
        var user =
                jwt.getClaim("userId") instanceof Number id
                        ? users.getUser(id.intValue())
                        : users.getUserByUsername(jwt.getSubject());
        if (user == null
                || !Boolean.TRUE.equals(user.getEnabled())
                || user.getRoles() == null
                || !user.getRoles().contains("TRADING_OPS")
                || user.getRoles().contains("CLIENT"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Operations staff access required");
        return user.getUserId();
    }

    public AccessPolicy policy() {
        actor();
        return new AccessPolicy(configured(), purposes, policyVersion);
    }

    private boolean configured() {
        return enabled
                && !purposes.isEmpty()
                && !policyVersion.isBlank()
                && duration >= 60
                && duration <= 3600;
    }

    @Transactional
    public AccessContext create(ContextRequest request) {
        int actor = actor();
        requireConfiguration();
        if (request == null
                || request.referenceType() == null
                || !Set.of("MANAGED", "EXTERNAL").contains(request.referenceType())
                || !purposes.contains(request.purposeCode())
                || request.caseReference() == null
                || request.caseReference().trim().length() < 8
                || request.caseReference().length() > 200
                || request.caseReference().chars().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose an allowed purpose and an 8–200 character case reference");
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        Object managedCase = null;
        if (request.referenceType().equals("MANAGED")) {
            var managed = db.managed(request.caseReference().trim(), actor, now);
            if (managed == null) notFound(new Access(actor, null, "CASE_READ", "CASE", now), null);
            managedCase = managed.get("case_id");
        } else if (request.caseReference().trim().matches("CASE-[0-9a-fA-F-]{36}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Managed case references require the managed reference type");
        }
        db.createContext(
                map(
                        "id",
                        id,
                        "actor",
                        actor,
                        "purpose",
                        request.purposeCode(),
                        "reason",
                        request.caseReference().trim(),
                        "now",
                        now,
                        "expires",
                        now.plusSeconds(duration),
                        "policy",
                        policyVersion,
                        "referenceType",
                        request.referenceType(),
                        "managedCase",
                        managedCase));
        success(
                new Access(actor, id, "CONTEXT", "NONE", now),
                "CONTEXT_CREATED",
                "CONTEXT",
                id.toString(),
                "",
                1,
                null);
        return new AccessContext(
                id,
                now.plusSeconds(duration),
                request.purposeCode(),
                request.referenceType(),
                request.caseReference().trim());
    }

    public Access require(UUID context, String permission) {
        int actor = actor();
        requireConfiguration();
        Instant now = clock.instant();
        var grants = db.grants(actor, permission, now);
        var access = new Access(actor, context, permission, digest(String.join("|", grants)), now);
        if (context == null
                || !db.context(context, actor, now, policyVersion)
                || grants.isEmpty()) {
            deny(access, "ACCESS", null);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Purpose context expired or permission unavailable");
        }
        var binding = db.binding(context);
        if ("MANAGED".equals(binding.get("reference_type"))) {
            Integer client = (Integer) binding.get("client_id"),
                    order = (Integer) binding.get("order_id");
            if (client == null || !has(access, "CASE_READ", client)) {
                deny(access, "CASE", null);
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Managed case permission unavailable");
            }
            return new Access(
                    actor,
                    context,
                    permission,
                    digest(
                            access.scopeVersion()
                                    + binding.get("managed_case_id")
                                    + String.join("|", db.grants(actor, "CASE_READ", now))),
                    now,
                    client,
                    order);
        }
        return access;
    }

    public boolean has(Access access, String permission, int client) {
        return (access.managedClient() == null || access.managedClient() == client)
                && db.grants(access.actor(), permission, clock.instant()).stream()
                        .anyMatch(g -> g.split(":", 3)[1].equals(Integer.toString(client)));
    }

    public String exportScope(Access access) {
        return digest(
                (access.managedClient() == null
                                ? ""
                                : String.join(
                                        "|",
                                        db.grants(access.actor(), "CASE_READ", clock.instant())))
                        + String.join(
                                "|", db.grants(access.actor(), "EVIDENCE_EXPORT", clock.instant()))
                        + String.join(
                                "|", db.grants(access.actor(), "EVIDENCE_READ", clock.instant()))
                        + String.join(
                                "|",
                                db.grants(access.actor(), "RECONCILIATION_READ", clock.instant())));
    }

    public void notFound(Access access, String target) {
        deny(access, "RESOURCE", target);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
    }

    private void requireConfiguration() {
        if (!configured())
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Operations access policy has not been configured");
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
        } catch (org.springframework.dao.DataAccessException failure) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Required access audit is unavailable");
        }
    }

    public void deny(Access access, String type, String target) {
        audit.denied(event(access, "READ_DENIED", type, target, "", "DENIED", 0, null));
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
