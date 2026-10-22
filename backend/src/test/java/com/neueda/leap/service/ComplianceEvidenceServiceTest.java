package com.neueda.leap.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.ComplianceDtos.*;
import com.neueda.leap.mapper.ComplianceEvidenceMapper;
import com.neueda.leap.service.ComplianceAccessService.Access;

import java.time.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ComplianceEvidenceServiceTest {
    @Mock ComplianceEvidenceMapper db;
    @Mock ComplianceAccessService access;

    private final Instant now = Instant.parse("2026-10-22T19:00:00Z");
    private final UUID context = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final Access grant = new Access(7, context, "EVIDENCE_READ", "scope-v1", now);
    private ComplianceEvidenceService service;

    @BeforeEach
    void setUp() {
        service =
                new ComplianceEvidenceService(
                        db,
                        access,
                        new ObjectMapper(),
                        Clock.fixed(now, ZoneOffset.UTC));
        when(access.require(context, "EVIDENCE_READ")).thenReturn(grant);
        lenient().when(access.scopeLabel()).thenReturn("Assigned accounts only");
        lenient().when(access.actorLabel(grant)).thenReturn("compliance.actor");
    }

    @Test
    void rejectsUnknownFiltersBeforeQueryingDatabase() {
        Map<String, String> filters =
                new LinkedHashMap<>(Map.of("page", "0", "pageSize", "25", "unexpected", "value"));

        ResponseStatusException failure =
                assertThrows(ResponseStatusException.class, () -> service.search(context, filters));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verifyNoInteractions(db);
    }

    @Test
    void searchesWithScopeCountBeforePageRows() {
        when(db.count(anyMap())).thenReturn(1L);
        when(db.orders(anyMap())).thenReturn(List.of(orderRow()));

        SearchPage result =
                service.search(
                        context,
                        Map.of("page", "0", "pageSize", "25", "status", "FILLED"));

        assertEquals(1, result.items().size());
        assertEquals("1.2500", result.items().get(0).quantity());
        InOrder order = inOrder(db);
        order.verify(db).count(anyMap());
        order.verify(db).orders(anyMap());
        verify(access).success(grant, "SEARCH", "ORDERS", null, "{page=0, pageSize=25, status=FILLED}", 1, null);
    }

    @Test
    void missingDecisionContextIsReportedAsUnknown() {
        when(db.order(anyMap())).thenReturn(orderRow());
        when(db.section(anyMap()))
                .thenAnswer(
                        invocation -> {
                            Map<String, Object> parameters = invocation.getArgument(0);
                            if ("decisions".equals(parameters.get("section"))) {
                                Map<String, Object> decision = new LinkedHashMap<>();
                                decision.put("reference", "decision-1");
                                decision.put("sequence", null);
                                decision.put("actor", null);
                                decision.put("occurredAt", now);
                                decision.put("decisionContext", null);
                                return List.of(decision);
                            }
                            return List.of();
                        });
        when(db.sectionCount(anyMap()))
                .thenAnswer(
                        invocation ->
                                "decisions".equals(
                                                ((Map<?, ?>) invocation.getArgument(0))
                                                        .get("section"))
                                        ? 1L
                                        : 0L);
        when(db.findings(anyMap())).thenReturn(List.of());
        when(db.currentPolicy(3)).thenReturn(null);
        when(access.has(grant, "EXPORT_CREATE", 11)).thenReturn(false);

        Investigation result = service.detail(context, 42);

        assertTrue(
                result.findings().stream()
                        .anyMatch(
                                finding ->
                                        finding.code().equals("DECISION_CONTEXT_MISSING")
                                                && finding.status().equals("UNKNOWN")));
        assertEquals("NOT_EVALUATED", result.sections().get(1).completeness());
    }

    @Test
    void inaccessibleOrderIsNonDisclosing() {
        when(db.order(anyMap())).thenReturn(null);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"))
                .when(access)
                .notFound(grant, "999");

        ResponseStatusException failure =
                assertThrows(ResponseStatusException.class, () -> service.detail(context, 999));

        assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
        verify(access).notFound(grant, "999");
    }

    @Test
    void sourceFailureDoesNotBecomeAnEmptySuccessfulPage() {
        when(db.count(anyMap()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        ResponseStatusException failure =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.search(
                                        context,
                                        Map.of("page", "0", "pageSize", "25")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
        verify(db, never()).orders(anyMap());
    }

    private static Map<String, Object> orderRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderId", 42);
        row.put("clientId", 11);
        row.put("instrumentId", 3);
        row.put("clientReference", "DEMO-CLIENT");
        row.put("instrument", "SIM-USD");
        row.put("side", "BUY");
        row.put("quantity", "1.2500");
        row.put("unit", "shares");
        row.put("status", "FILLED");
        row.put("submittedAt", Instant.parse("2026-10-22T18:59:00Z"));
        row.put("acceptedAt", Instant.parse("2026-10-22T18:59:01Z"));
        row.put("outcomeAt", null);
        row.put("executionMode", "PROVIDER_BACKED");
        row.put("recordKind", "SELF_DIRECTED");
        row.put("policySnapshot", null);
        return row;
    }
}
