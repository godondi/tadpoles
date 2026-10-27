package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.neueda.leap.dto.AdvisorWorkspaceDtos.*;
import com.neueda.leap.mapper.AdvisorWorkspaceMapper;
import com.neueda.leap.mapper.AdvisorWorkspaceMapper.Scope;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.server.ResponseStatusException;

class AdvisorWorkspaceServiceImplTest {
    private final AdvisorWorkspaceMapper db = mock(AdvisorWorkspaceMapper.class);
    private final Scope scope = new Scope(11, 4, 7);
    private final Instant now = Instant.parse("2026-10-22T18:00:00Z");
    private AdvisorWorkspaceServiceImpl service;

    @BeforeEach
    void setup() {
        service = new AdvisorWorkspaceServiceImpl(db);
        lenient().when(db.advisorForUser(11)).thenReturn(4);
        lenient().when(db.lockClient(scope)).thenReturn(7);
    }

    private void status(int expected, Runnable action) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }

    private Recommendation rec(long version) {
        return new Recommendation(9, 43, "NEW", "New Company", "Review", version, now);
    }

    @Test
    void persistedScopeIsResolvedEveryCallAndMissingRoleOrLinkNeverFallsBack() {
        status(403, () -> service.clients(99, 0, 20, ""));
        status(403, () -> service.instruments(99, 0, 20, ""));
        verify(db, never()).clients(any(), any(), anyInt(), anyInt());
        verify(db, never()).instruments(any(), any(), anyInt(), anyInt());
        service.clients(11, 0, 20, "");
        service.clients(11, 0, 20, "");
        verify(db, times(2)).advisorForUser(11);
    }

    @Test
    void listIsScopedAndSearchIsLiteralTrimmedAndBounded() {
        Scope listScope = new Scope(11, 4, 0);
        when(db.clients(listScope, "demo%_", 20, 40)).thenReturn(List.of());
        when(db.clientCount(listScope, "demo%_")).thenReturn(0L);
        assertEquals(new Page<>(List.of(), 2, 20, 0), service.clients(11, 2, 20, " Demo%_ "));
        status(400, () -> service.clients(11, -1, 20, ""));
        status(400, () -> service.clients(11, 0, 101, ""));
        status(400, () -> service.clients(11, 0, 0, ""));
        status(400, () -> service.clients(11, Integer.MAX_VALUE, 100, ""));
        status(400, () -> service.clients(11, 0, 20, "x".repeat(101)));
    }

    @Test
    void advisorACannotReadOrMutateAdvisorBClient() {
        Scope foreign = new Scope(11, 4, 88);
        status(404, () -> service.portfolio(11, 88));
        status(404, () -> service.guidance(11, 88));
        status(404, () -> service.recommendations(11, 88));
        status(404, () -> service.saveGuidance(11, 88, 42, new GuidanceRequest("HOLD", null, null)));
        status(404, () -> service.createRecommendation(11, 88, new RecommendationRequest(43, "Review")));
        status(404, () -> service.updateRecommendation(11, 88, 9, new RecommendationUpdateRequest("Review", 1L)));
        status(404, () -> service.archiveRecommendation(11, 88, 9, new ArchiveRequest(1L)));
        verify(db, never()).positions(foreign);
        verify(db, never()).recommendation(foreign, 9);
        verify(db, never()).held(eq(foreign), anyInt());
    }

    @Test
    void portfolioPreservesQuarantinedFractionalFullyReservedPositionStrings() {
        when(db.client(scope)).thenReturn(new AdvisorClient(7, "Safe Name", "CLIENT-7", "ASSIGNED", now));
        Position p = new Position(42, "TST", "Test", "USD", "0.125000000000", "0.125000000000", "0.000000000000", now);
        when(db.positions(scope)).thenReturn(List.of(p));
        Portfolio result = service.portfolio(11, 7);
        assertEquals("Safe Name", result.clientName());
        assertEquals(List.of(p), result.currentPositions());
        assertNotNull(result.retrievedAt());
    }

    @Test
    void guidanceCreateTrimsAndLocksClientBeforeHoldingAndWrite() {
        when(db.held(scope, 42)).thenReturn(true);
        when(db.insertGuidance(scope, 42, "HOLD", "Review")).thenReturn(1);
        HoldingGuidance confirmed = new HoldingGuidance(42, "HOLD", "Review", "Advisor", 1, now);
        when(db.guidanceItem(scope, 42)).thenReturn(confirmed);
        assertEquals(confirmed, service.saveGuidance(11, 7, 42, new GuidanceRequest("HOLD", " Review ", null)));
        InOrder order = inOrder(db);
        order.verify(db).advisorForUser(11);
        order.verify(db).lockClient(scope);
        order.verify(db).held(scope, 42);
        order.verify(db).insertGuidance(scope, 42, "HOLD", "Review");
    }

    @Test
    void guidanceClearPersistsTombstoneAndRequiresCasEvenForSameValue() {
        when(db.held(scope, 42)).thenReturn(true);
        when(db.updateGuidance(scope, 42, "NONE", null, 3)).thenReturn(1);
        HoldingGuidance tombstone = new HoldingGuidance(42, "NONE", null, "Advisor", 4, now);
        when(db.guidanceItem(scope, 42)).thenReturn(tombstone);
        assertEquals(tombstone, service.saveGuidance(11, 7, 42, new GuidanceRequest("NONE", " discarded ", 3L)));
        status(409, () -> service.saveGuidance(11, 7, 42, new GuidanceRequest("NONE", null, null)));
        status(409, () -> service.saveGuidance(11, 7, 42, new GuidanceRequest("NONE", null, 2L)));
    }

    @Test
    void firstClearCreatesVersionOneAndNoneStillValidatesSuppliedRationale() {
        when(db.held(scope, 42)).thenReturn(true);
        when(db.insertGuidance(scope, 42, "NONE", null)).thenReturn(1);
        when(db.guidanceItem(scope, 42)).thenReturn(new HoldingGuidance(42, "NONE", null, null, 1, now));
        assertEquals(1, service.saveGuidance(11, 7, 42, new GuidanceRequest("NONE", null, null)).version());
        status(400, () -> service.saveGuidance(11, 7, 42, new GuidanceRequest("NONE", " ", 1L)));
        status(400, () -> service.saveGuidance(11, 7, 42, new GuidanceRequest("NONE", "x".repeat(2001), 1L)));
    }

    @Test
    void invalidGuidanceAndNonOwnedInstrumentsNeverWrite() {
        status(400, () -> service.saveGuidance(11, 7, 42, new GuidanceRequest("BUY", null, null)));
        status(400, () -> service.saveGuidance(11, 7, 42, new GuidanceRequest("SELL", null, 0L)));
        status(400, () -> service.saveGuidance(11, 7, -1, new GuidanceRequest("SELL", null, null)));
        status(404, () -> service.saveGuidance(11, 7, 42, new GuidanceRequest("SELL", null, null)));
        verify(db, never()).insertGuidance(any(), anyInt(), any(), any());
    }

    @Test
    void recommendationCreateRequiresApprovedNotHeldAndReturnsConfirmedRecord() {
        when(db.approvedInstrument(scope, 43)).thenReturn(true);
        when(db.insertRecommendation(scope, 43, "Review")).thenReturn(9);
        when(db.recommendation(scope, 9)).thenReturn(rec(1));
        assertEquals(rec(1), service.createRecommendation(11, 7, new RecommendationRequest(43, " Review ")));
        InOrder order = inOrder(db);
        order.verify(db).lockClient(scope);
        order.verify(db).approvedInstrument(scope, 43);
        order.verify(db).held(scope, 43);
        order.verify(db).insertRecommendation(scope, 43, "Review");
    }

    @Test
    void fractionalOrFullyReservedOwnershipAndActiveDuplicateConflict() {
        when(db.approvedInstrument(scope, 43)).thenReturn(true);
        when(db.held(scope, 43)).thenReturn(true);
        status(409, () -> service.createRecommendation(11, 7, new RecommendationRequest(43, "Review")));
        verify(db, never()).insertRecommendation(any(), anyInt(), any());
        when(db.held(scope, 43)).thenReturn(false);
        status(409, () -> service.createRecommendation(11, 7, new RecommendationRequest(43, "Review")));
    }

    @Test
    void unknownOrUnapprovedInstrumentIsNotFoundAndRationaleIsBounded() {
        status(404, () -> service.createRecommendation(11, 7, new RecommendationRequest(43, "Review")));
        status(400, () -> service.createRecommendation(11, 7, new RecommendationRequest(43, " ")));
        status(400, () -> service.createRecommendation(11, 7, new RecommendationRequest(43, "x".repeat(2001))));
        status(400, () -> service.createRecommendation(11, 7, new RecommendationRequest(null, "Review")));
    }

    @Test
    void guessedForeignRecommendationNeverReachesCasOrEligibility() {
        status(404, () -> service.updateRecommendation(11, 7, 999, new RecommendationUpdateRequest("Review", 1L)));
        status(404, () -> service.archiveRecommendation(11, 7, 999, new ArchiveRequest(1L)));
        verify(db, never()).held(any(), anyInt());
        verify(db, never()).updateRecommendation(any(), anyInt(), any(), anyLong());
        verify(db, never()).archiveRecommendation(any(), anyInt(), anyLong());
    }

    @Test
    void staleVersionAndMissingVersionNeverWrite() {
        when(db.recommendation(scope, 9)).thenReturn(rec(3));
        status(409, () -> service.updateRecommendation(11, 7, 9, new RecommendationUpdateRequest("Review", 2L)));
        status(409, () -> service.archiveRecommendation(11, 7, 9, new ArchiveRequest(2L)));
        status(400, () -> service.updateRecommendation(11, 7, 9, new RecommendationUpdateRequest("Review", null)));
        status(400, () -> service.archiveRecommendation(11, 7, 9, new ArchiveRequest(null)));
        verify(db, never()).updateRecommendation(any(), anyInt(), any(), anyLong());
        verify(db, never()).archiveRecommendation(any(), anyInt(), anyLong());
    }

    @Test
    void patchRechecksCatalogAndHoldingsAndCasFailureIsConflict() {
        when(db.recommendation(scope, 9)).thenReturn(rec(3));
        status(404, () -> service.updateRecommendation(11, 7, 9, new RecommendationUpdateRequest("Review", 3L)));
        when(db.approvedInstrument(scope, 43)).thenReturn(true);
        when(db.held(scope, 43)).thenReturn(true);
        status(409, () -> service.updateRecommendation(11, 7, 9, new RecommendationUpdateRequest("Review", 3L)));
        when(db.held(scope, 43)).thenReturn(false);
        status(409, () -> service.updateRecommendation(11, 7, 9, new RecommendationUpdateRequest("Review", 3L)));
        when(db.updateRecommendation(scope, 9, "Review", 3)).thenReturn(1);
        when(db.recommendation(scope, 9)).thenReturn(rec(3), rec(4));
        assertEquals(4, service.updateRecommendation(11, 7, 9, new RecommendationUpdateRequest(" Review ", 3L)).version());
    }

    @Test
    void archiveCanRemoveNowHeldOrInactiveInstrumentAndReturnsIncrementedVersion() {
        when(db.recommendation(scope, 9)).thenReturn(rec(3));
        when(db.archiveRecommendation(scope, 9, 3)).thenReturn(1);
        assertEquals(new ArchiveResponse(9, 4), service.archiveRecommendation(11, 7, 9, new ArchiveRequest(3L)));
        verify(db, never()).held(any(), anyInt());
        verify(db, never()).approvedInstrument(any(), anyInt());
        when(db.archiveRecommendation(scope, 9, 3)).thenReturn(0);
        status(409, () -> service.archiveRecommendation(11, 7, 9, new ArchiveRequest(3L)));
    }

    @Test
    void clientIdentityComesOnlyFromVerifiedUserAndAssignmentTimestampIsUnknown() {
        when(db.clientForUser(21)).thenReturn(7);
        assertEquals(new ClientAssignment("PENDING", null, null), service.clientAdvisor(21));
        when(db.clientAdvisor(21)).thenReturn(new AssignedAdvisor(4, "Current Advisor"));
        assertEquals(new ClientAssignment("ASSIGNED", new AssignedAdvisor(4, "Current Advisor"), null), service.clientAdvisor(21));
        status(403, () -> service.clientAdvisor(22));
        status(403, () -> service.clientAdvisory(22));
        verify(db, never()).clientGuidance(22);
    }

    @Test
    void emptyReadsAreSuccessfulAndClientRecordsHaveNoMutationVersions() {
        when(db.client(scope)).thenReturn(new AdvisorClient(7, "Safe", "CLIENT-7", "ASSIGNED", now));
        when(db.guidance(scope)).thenReturn(List.of());
        when(db.recommendations(scope)).thenReturn(List.of());
        assertEquals(new GuidanceResponse(List.of(), null), service.guidance(11, 7));
        assertEquals(new RecommendationsResponse(List.of(), null), service.recommendations(11, 7));
        when(db.clientForUser(21)).thenReturn(7);
        when(db.clientGuidance(21)).thenReturn(List.of(new ClientGuidance(42, "NONE", null, null, now)));
        when(db.clientRecommendations(21)).thenReturn(List.of());
        ClientAdvisory result = service.clientAdvisory(21);
        assertEquals(now, result.asOf());
        assertEquals("Committed advisory records", result.source());
        assertEquals("NONE", result.guidance().get(0).status());
    }
}