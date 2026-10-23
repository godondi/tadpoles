package com.neueda.leap.service.impl;

import com.neueda.leap.dto.AdvisorWorkspaceDtos.*;
import com.neueda.leap.mapper.AdvisorWorkspaceMapper;
import com.neueda.leap.mapper.AdvisorWorkspaceMapper.Scope;
import com.neueda.leap.service.AdvisorWorkspaceService;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Advisory projections only: no financial service, order or history-writing dependencies. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdvisorWorkspaceServiceImpl implements AdvisorWorkspaceService {
    private static final long MAX_VERSION = 9_007_199_254_740_991L;
    private final AdvisorWorkspaceMapper db;

    public AdvisorWorkspaceServiceImpl(AdvisorWorkspaceMapper db) {
        this.db = db;
    }

    @Override
    public Page<AdvisorClient> clients(int userId, int page, int pageSize, String search) {
        int offset = offset(page, pageSize);
        String term = search(search);
        Scope scope = scope(userId, 0);
        return new Page<>(db.clients(scope, term, pageSize, offset), page, pageSize,
                db.clientCount(scope, term));
    }

    @Override
    public Portfolio portfolio(int userId, int clientId) {
        Scope scope = scope(userId, positive(clientId));
        AdvisorClient client = requireClient(scope);
        return new Portfolio(client.clientId(), client.displayName(), client.clientReference(),
                db.positions(scope), Instant.now());
    }

    @Override
    public GuidanceResponse guidance(int userId, int clientId) {
        Scope scope = scope(userId, positive(clientId));
        requireClient(scope);
        var items = db.guidance(scope);
        return new GuidanceResponse(items, latest(items.stream().map(HoldingGuidance::updatedAt)));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldingGuidance saveGuidance(int userId, int clientId, int instrumentId,
                                        GuidanceRequest request) {
        positive(clientId);
        positive(instrumentId);
        if (request == null || request.status() == null
                || !Set.of("NONE", "HOLD", "SELL").contains(request.status())) {
            throw bad("Guidance status must be NONE, HOLD or SELL");
        }
        String reasoning = rationale(request.reasoning(), false);
        if (request.expectedVersion() != null) version(request.expectedVersion());
        if ("NONE".equals(request.status())) reasoning = null;
        Scope scope = lockedScope(userId, clientId);
        if (!db.held(scope, instrumentId)) throw missing();
        int changed = request.expectedVersion() == null
                ? db.insertGuidance(scope, instrumentId, request.status(), reasoning)
                : db.updateGuidance(scope, instrumentId, request.status(), reasoning, request.expectedVersion());
        if (changed != 1) throw conflict();
        return db.guidanceItem(scope, instrumentId);
    }

    @Override
    public RecommendationsResponse recommendations(int userId, int clientId) {
        Scope scope = scope(userId, positive(clientId));
        requireClient(scope);
        var items = db.recommendations(scope);
        return new RecommendationsResponse(items, latest(items.stream().map(Recommendation::updatedAt)));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Recommendation createRecommendation(int userId, int clientId, RecommendationRequest request) {
        positive(clientId);
        if (request == null || request.instrumentId() == null) throw bad("Instrument is required");
        int instrument = positive(request.instrumentId());
        String reasoning = rationale(request.reasoning(), true);
        Scope scope = lockedScope(userId, clientId);
        eligible(scope, instrument);
        Integer id = db.insertRecommendation(scope, instrument, reasoning);
        if (id == null || id <= 0) throw conflict();
        return db.recommendation(scope, id);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Recommendation updateRecommendation(int userId, int clientId, int recommendationId,
                                                RecommendationUpdateRequest request) {
        positive(clientId);
        positive(recommendationId);
        if (request == null) throw bad("Request is required");
        long version = version(request.expectedVersion());
        String reasoning = rationale(request.reasoning(), true);
        Scope scope = lockedScope(userId, clientId);
        Recommendation current = current(scope, recommendationId, version);
        eligible(scope, current.instrumentId());
        if (db.updateRecommendation(scope, recommendationId, reasoning, version) != 1) throw conflict();
        return db.recommendation(scope, recommendationId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ArchiveResponse archiveRecommendation(int userId, int clientId, int recommendationId,
                                                 ArchiveRequest request) {
        positive(clientId);
        positive(recommendationId);
        if (request == null) throw bad("Request is required");
        long version = version(request.expectedVersion());
        Scope scope = lockedScope(userId, clientId);
        current(scope, recommendationId, version);
        // Archiving must remain possible after acquisition or catalog deactivation.
        if (db.archiveRecommendation(scope, recommendationId, version) != 1) throw conflict();
        return new ArchiveResponse(recommendationId, version + 1);
    }

    @Override
    public Page<Instrument> instruments(int userId, int page, int pageSize, String search) {
        int offset = offset(page, pageSize);
        String term = search(search);
        Scope scope = scope(userId, 0);
        return new Page<>(db.instruments(scope, term, pageSize, offset), page, pageSize,
                db.instrumentCount(scope, term));
    }

    @Override
    public ClientAssignment clientAdvisor(int userId) {
        requireOwnClient(userId);
        AssignedAdvisor advisor = db.clientAdvisor(userId);
        // The existing assignment schema has no historical timestamp. Never fabricate one.
        return new ClientAssignment(advisor == null ? "PENDING" : "ASSIGNED", advisor, null);
    }

    @Override
    public ClientAdvisory clientAdvisory(int userId) {
        requireOwnClient(userId);
        var guidance = db.clientGuidance(userId);
        var recommendations = db.clientRecommendations(userId);
        Instant asOf = latest(Stream.concat(guidance.stream().map(ClientGuidance::updatedAt),
                recommendations.stream().map(ClientRecommendation::updatedAt)));
        return new ClientAdvisory(guidance, recommendations, asOf, "Committed advisory records");
    }

    private Scope scope(int userId, int clientId) {
        if (userId <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid identity");
        Integer advisor = db.advisorForUser(userId);
        if (advisor == null || advisor <= 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Advisor workspace access required");
        return new Scope(userId, advisor, clientId);
    }

    private Scope lockedScope(int userId, int clientId) {
        Scope scope = scope(userId, clientId);
        // Match TradingMapper: client first, before positions or advisory records.
        Integer locked = db.lockClient(scope);
        if (locked == null || locked != clientId) throw missing();
        return scope;
    }

    private AdvisorClient requireClient(Scope scope) {
        AdvisorClient client = db.client(scope);
        if (client == null) throw missing();
        return client;
    }

    private void requireOwnClient(int userId) {
        if (userId <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid identity");
        Integer client = db.clientForUser(userId);
        if (client == null || client <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client workspace access required");
        }
    }

    private Recommendation current(Scope scope, int recommendationId, long version) {
        Recommendation current = db.recommendation(scope, recommendationId);
        if (current == null) throw missing();
        if (current.version() != version) throw conflict();
        return current;
    }

    private void eligible(Scope scope, int instrumentId) {
        if (!db.approvedInstrument(scope, instrumentId)) throw missing();
        if (db.held(scope, instrumentId)) throw conflict();
    }

    private static int positive(int value) {
        if (value <= 0) throw bad("IDs must be positive integers");
        return value;
    }

    private static long version(Long value) {
        if (value == null || value < 1 || value >= MAX_VERSION) {
            throw bad("A positive expectedVersion is required");
        }
        return value;
    }

    private static int offset(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw bad("Invalid page or pageSize");
        }
        return page * size;
    }

    private static String search(String value) {
        String term = value == null ? "" : value.strip();
        if (term.length() > 100 || term.indexOf('\0') >= 0) throw bad("Search must be at most 100 characters");
        return term.toLowerCase(Locale.ROOT);
    }

    private static String rationale(String value, boolean required) {
        if (value == null && !required) return null;
        if (value == null) throw bad("Reasoning is required");
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.length() > 2000 || trimmed.indexOf('\0') >= 0) {
            throw bad("Reasoning must contain 1 to 2000 characters");
        }
        return trimmed;
    }

    private static Instant latest(Stream<Instant> timestamps) {
        return timestamps.filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "This advisory record is unavailable");
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "The advisory record or eligibility changed. Refresh before retrying.");
    }
}