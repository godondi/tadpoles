package com.neueda.leap.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.*;
import com.neueda.leap.trading.TradingModels.*;
import com.neueda.leap.trading.TradingQuotes;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "trading.worker-enabled=false",
            "trading.admission-enabled=true",
            "support.workflow-enabled=true",
            "banking.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "operations.access-enabled=true",
            "operations.allowed-purposes=ORDER_REVIEW",
            "operations.policy-version=test-v1",
            "operations.context-seconds=600",
            "operations.export-seconds=600",
            "compliance.access-enabled=true",
            "compliance.allowed-purposes=CASE_REVIEW",
            "compliance.policy-version=test-v1",
            "compliance.context-seconds=600",
            "compliance.export-seconds=600"
        })
class SupportWorkflowPostgresTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");
    static final String external = System.getenv("ISOLATED_TEST_DB_URL");
    static final String runtimeRole = System.getenv("WORKFLOW_RUNTIME_ROLE");
    static final String url;

    static {
        if (external == null) {
            POSTGRES.start();
            url = POSTGRES.getJdbcUrl();
        } else {
            if (!external.matches("jdbc:postgresql://[a-zA-Z0-9.-]+:5432/tadpoles_isolated_test"))
                throw new IllegalArgumentException("Use the isolated test database only");
            url = external;
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        if (runtimeRole != null && !"workflow_runtime".equals(runtimeRole))
            throw new IllegalArgumentException(
                    "Only the dedicated workflow fixture role is supported");
        r.add("spring.datasource.url", () -> url);
        r.add(
                "spring.datasource.username",
                () ->
                        runtimeRole != null
                                ? runtimeRole
                                : external == null ? POSTGRES.getUsername() : "test");
        r.add(
                "spring.datasource.password",
                () ->
                        runtimeRole != null
                                ? "workflow-fixture-only"
                                : external == null ? POSTGRES.getPassword() : "test");
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean Clock clock;
    @MockBean TradingQuotes quotes;
    @Autowired com.neueda.leap.service.TradingService trading;
    @Autowired com.neueda.leap.service.BankingService banking;
    String client, other, staff, compliance;
    int account, staffId, reviewer;
    final Instant now = Instant.now();

    Connection connection() throws Exception {
        return DriverManager.getConnection(
                url,
                external == null ? POSTGRES.getUsername() : "test",
                external == null ? POSTGRES.getPassword() : "test");
    }

    void sql(String value) throws Exception {
        try (var c = connection();
                var s = c.createStatement()) {
            s.execute(value);
        }
    }

    void script(String file) throws Exception {
        sql(Files.readString(Path.of("../" + file)));
    }

    String scalar(String query) throws Exception {
        try (var c = connection();
                var s = c.createStatement();
                var r = s.executeQuery(query)) {
            assertTrue(r.next());
            return r.getString(1);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenAnswer(i -> now);
        sql("DROP SCHEMA public CASCADE;CREATE SCHEMA public");
        script("database/fresh/001_schema.sql");
        script("database/fresh/002_reference.sql");
        script("database/demo/role_workspaces.sql");
        script("database/demo/operations_workspace.sql");
        script("database/demo/compliance_workspace.sql");
        if (runtimeRole != null) {
            String grants = Files.readString(Path.of("../database/runtime-grants.sql"));
            sql(
                    grants.substring(grants.indexOf("BEGIN;"), grants.indexOf("COMMIT;") + 7)
                            .replace(":\"runtime_role\"", "workflow_runtime"));
        }
        account =
                Integer.parseInt(
                        scalar(
                                "SELECT client_id FROM clients JOIN users USING(user_id) WHERE"
                                        + " username='demo.client'"));
        staffId =
                Integer.parseInt(
                        scalar("SELECT user_id FROM users WHERE username='demo.operations'"));
        reviewer =
                Integer.parseInt(
                        scalar("SELECT user_id FROM users WHERE username='demo.compliance'"));
        sql(
                """
INSERT INTO operations_access_grants(grant_id,staff_user_id,permission,client_id,account_reference,valid_from,valid_until,approved_by,approval_reference)
SELECT gen_random_uuid(),s.user_id,'ACCOUNT_DIAGNOSTICS',c.client_id,'DEMO-CLIENT',now()-interval '1 day',now()+interval '1 day',a.user_id,'TEST-DIAGNOSTICS'
FROM users s,users a,clients c JOIN users u USING(user_id)
WHERE s.username='demo.operations' AND a.username='demo.finance' AND u.username='demo.client'
  AND NOT EXISTS(SELECT 1 FROM operations_access_grants g WHERE g.staff_user_id=s.user_id AND g.client_id=c.client_id AND g.permission='ACCOUNT_DIAGNOSTICS');
INSERT INTO compliance_access_grants(grant_id,staff_user_id,permission,client_id,account_reference,valid_from,valid_until,approved_by,approval_reference)
SELECT gen_random_uuid(),s.user_id,p.permission,c.client_id,'DEMO-CLIENT',now()-interval '1 day',now()+interval '1 day',a.user_id,'TEST-INVESTIGATION'
FROM users s CROSS JOIN users a CROSS JOIN clients c JOIN users u ON u.user_id=c.user_id
CROSS JOIN (VALUES ('INVESTIGATION_READ'),('INVESTIGATION_UPDATE')) p(permission)
WHERE s.username='demo.compliance' AND a.username='demo.finance' AND u.username='demo.client'
  AND NOT EXISTS(SELECT 1 FROM compliance_access_grants g WHERE g.staff_user_id=s.user_id AND g.client_id=c.client_id AND g.permission=p.permission)
""");
        client = login("demo.client");
        other = login("demo.client2");
        staff = login("demo.operations");
        compliance = login("demo.compliance");
    }

    String login(String user) throws Exception {
        return json.readTree(
                        mvc.perform(
                                        org.springframework.test.web.servlet.request
                                                .MockMvcRequestBuilders.post("/auth/login")
                                                .contentType("application/json")
                                                .content(
                                                        json.writeValueAsString(
                                                                Map.of(
                                                                        "username",
                                                                        user,
                                                                        "password",
                                                                        "RoleDemo2026!"))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("token")
                .asText();
    }

    JsonNode read(String token, String path, int expected) throws Exception {
        return json.readTree(
                mvc.perform(get(path).header("Authorization", "Bearer " + token))
                        .andExpect(status().is(expected))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    JsonNode post(String token, String path, String key, Map<String, Object> body, int expected)
            throws Exception {
        return json.readTree(
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post(path)
                                        .header("Authorization", "Bearer " + token)
                                        .header("Idempotency-Key", key)
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(body)))
                        .andExpect(status().is(expected))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    String create(String category) throws Exception {
        return post(
                        client,
                        "/api/support/cases",
                        UUID.randomUUID().toString(),
                        Map.of(
                                "category",
                                category,
                                "subject",
                                "Investigate the reported issue",
                                "description",
                                "Please investigate this reported problem safely."),
                        200)
                .path("reference")
                .asText();
    }

    String base(String reference) {
        return "/api/operations/cases/" + reference;
    }

    JsonNode command(
            String reference, String action, long version, Map<String, Object> data, int expected)
            throws Exception {
        var body = new HashMap<>(data);
        body.put("expectedWorkflowVersion", version);
        return post(
                staff,
                base(reference) + "/workflow/" + action,
                UUID.randomUUID().toString(),
                body,
                expected);
    }

    long version(String reference) throws Exception {
        return read(staff, base(reference) + "/workbench", 200).path("workflowVersion").asLong();
    }

    void finishOther(String reference) throws Exception {
        command(reference, "assignment", version(reference), Map.of("assignee", staffId), 200);
        var tasks = read(staff, base(reference) + "/workbench", 200).path("tasks");
        for (var task : tasks)
            command(
                    reference,
                    "tasks",
                    version(reference),
                    Map.of(
                            "code",
                            task.path("code").asText(),
                            "state",
                            "COMPLETED",
                            "reason",
                            "The reported question and outcome were checked."),
                    200);
    }

    @Test
    void privateCommandsReplayBeforeVersionsAndNeverChangeClientProjection() throws Exception {
        String reference = create("OTHER"), key = UUID.randomUUID().toString();
        JsonNode before = read(client, "/api/support/cases/" + reference, 200);
        String unread = scalar("SELECT count(*) FROM support_case_events");
        var body = Map.<String, Object>of("expectedWorkflowVersion", 1, "assignee", staffId);
        var receipt = post(staff, base(reference) + "/workflow/assignment", key, body, 200);
        assertEquals(
                receipt, post(staff, base(reference) + "/workflow/assignment", key, body, 200));
        var changed = Map.<String, Object>of("expectedWorkflowVersion", 1, "assignee", reviewer);
        assertEquals(
                "IDEMPOTENCY_CONFLICT",
                post(staff, base(reference) + "/workflow/assignment", key, changed, 409)
                        .path("code")
                        .asText());
        assertEquals(
                "WORKFLOW_CONFLICT",
                command(
                                reference,
                                "priority",
                                1,
                                Map.of(
                                        "priority",
                                        "HIGH",
                                        "reason",
                                        "The owner has requested faster follow-up."),
                                409)
                        .path("code")
                        .asText());
        assertEquals(before, read(client, "/api/support/cases/" + reference, 200));
        assertEquals(unread, scalar("SELECT count(*) FROM support_case_events"));
        command(
                reference,
                "classification",
                2,
                Map.of(
                        "category",
                        "ACCOUNT_REVIEW",
                        "reason",
                        "The reported issue concerns available account resources."),
                200);
        var work = read(staff, base(reference) + "/workbench", 200);
        assertEquals(2, work.path("checklistRevision").asInt());
        assertTrue(
                work.path("tasks").findValuesAsText("state").stream().allMatch("PENDING"::equals));
        assertEquals(before, read(client, "/api/support/cases/" + reference, 200));
        read(other, "/api/support/cases/" + reference, 404);
    }

    @Test
    void resolutionsAreAtomicAndReopenCreatesFreshChecklist() throws Exception {
        String reference = create("OTHER");
        assertEquals(
                "WORKFLOW_INCOMPLETE",
                post(
                                staff,
                                base(reference) + "/transitions",
                                UUID.randomUUID().toString(),
                                Map.of(
                                        "expectedVersion",
                                        1,
                                        "body",
                                        "We have explained the reported issue.",
                                        "status",
                                        "RESOLVED"),
                                409)
                        .path("code")
                        .asText());
        command(
                reference,
                "resolution",
                1,
                Map.of(
                        "expectedVersion",
                        1,
                        "code",
                        "GENERAL_GUIDANCE_PROVIDED",
                        "body",
                        "The requested explanation has been supplied."),
                409);
        finishOther(reference);
        command(
                reference,
                "resolution",
                version(reference),
                Map.of(
                        "expectedVersion",
                        1,
                        "code",
                        "GENERAL_GUIDANCE_PROVIDED",
                        "body",
                        "The reported issue was reviewed and general guidance was provided."),
                200);
        assertEquals(
                "RESOLVED",
                read(client, "/api/support/cases/" + reference, 200).path("status").asText());
        assertEquals("1", scalar("SELECT count(*) FROM support_case_resolutions"));
        post(
                client,
                "/api/support/cases/" + reference + "/reopen",
                UUID.randomUUID().toString(),
                Map.of(
                        "expectedVersion",
                        2,
                        "body",
                        "The reported issue still affects my account."),
                200);
        var work = read(staff, base(reference) + "/workbench", 200);
        assertEquals(2, work.path("checklistRevision").asInt());
        for (var task : work.path("tasks")) assertEquals("PENDING", task.path("state").asText());
        assertEquals(
                "4", scalar("SELECT count(*) FROM support_case_tasks WHERE checklist_revision=1"));
    }

    @Test
    void structuredIntakeChecksOwnershipAndRetainsNormalizedReplay() throws Exception {
        sql(
                "INSERT INTO financial_migration_exceptions(source_reference,kind,client_id,facts)"
                        + " VALUES('owned-history','UNKNOWN_CASH_CURRENCY',"
                        + account
                        + ",'{\"cashBalance\":\"32\",\"secret\":\"must-not-leak\"}')");
        long exception =
                Long.parseLong(
                        scalar(
                                "SELECT exception_id FROM financial_migration_exceptions WHERE"
                                        + " source_reference='owned-history'"));
        var body =
                Map.<String, Object>of(
                        "category",
                        "HISTORICAL_RECORD",
                        "subject",
                        "Clarify this historical record",
                        "description",
                        "Explain the limits of the retained historical evidence.",
                        "intakeSchemaVersion",
                        1,
                        "intake",
                        Map.of("reason", "PROVENANCE", "exceptionId", exception));
        String key = UUID.randomUUID().toString();
        var created = post(client, "/api/support/cases", key, body, 200);
        assertEquals(created, post(client, "/api/support/cases", key, body, 200));
        post(other, "/api/support/cases", UUID.randomUUID().toString(), body, 404);
        var details =
                read(
                        staff,
                        base(created.path("reference").asText())
                                + "/diagnostics?kind=historical&evidence="
                                + exception,
                        200);
        assertFalse(details.toString().contains("must-not-leak"));
        assertTrue(details.path("currency").asText().contains("no USD"));
        assertEquals(
                details.path("reference"),
                read(
                                staff,
                                base(created.path("reference").asText())
                                        + "/diagnostics?kind=historical",
                                200)
                        .path("reference"));
        var invalid = new HashMap<>(body);
        invalid.put("intakeSchemaVersion", 4294967297L);
        post(client, "/api/support/cases", UUID.randomUUID().toString(), invalid, 400);
    }

    @Test
    void referralLifecycleIsIndependentAndGatesResolutionWithoutPublishingPrivateDecisions()
            throws Exception {
        String reference = create("OTHER");
        finishOther(reference);
        JsonNode before = read(client, "/api/support/cases/" + reference, 200);
        String key = UUID.randomUUID().toString();
        var body =
                Map.<String, Object>of(
                        "expectedWorkflowVersion",
                        version(reference),
                        "reviewer",
                        reviewer,
                        "reasonCode",
                        "INTEGRITY",
                        "summary",
                        "Review this approved source summary without unrelated client details.");
        var receipt = post(staff, base(reference) + "/referrals", key, body, 200);
        assertEquals(receipt, post(staff, base(reference) + "/referrals", key, body, 200));
        String review = receipt.path("investigationReference").asText(),
                path = "/api/compliance/cases/" + review;
        read(staff, path, 403);
        command(
                reference,
                "resolution",
                version(reference),
                Map.of(
                        "expectedVersion",
                        1,
                        "code",
                        "GENERAL_GUIDANCE_PROVIDED",
                        "body",
                        "We have explained this reported issue."),
                409);
        post(
                compliance,
                path + "/actions",
                UUID.randomUUID().toString(),
                Map.of(
                        "expectedVersion",
                        1,
                        "action",
                        "TRANSITION",
                        "status",
                        "IN_REVIEW",
                        "reason",
                        "An eligible reviewer has begun the approved investigation."),
                200);
        post(
                compliance,
                path + "/actions",
                UUID.randomUUID().toString(),
                Map.of(
                        "expectedVersion",
                        2,
                        "action",
                        "TRANSITION",
                        "status",
                        "DECIDED",
                        "reason",
                        "The approved retained evidence has been reviewed without changing"
                                + " financial records.",
                        "disposition",
                        "NO_CORRECTION_AUTHORIZED",
                        "clientSafeExplanation",
                        "The review is complete; no automatic account correction was performed."),
                200);
        assertEquals(before, read(client, "/api/support/cases/" + reference, 200));
        var work = read(staff, base(reference) + "/workbench", 200);
        assertFalse(work.path("mandatoryReferralPending").asBoolean());
        assertFalse(work.toString().contains("approved retained evidence"));
        command(
                reference,
                "resolution",
                version(reference),
                Map.of(
                        "expectedVersion",
                        1,
                        "code",
                        "GENERAL_GUIDANCE_PROVIDED",
                        "body",
                        "The reported question was reviewed and the outcome has now been explicitly"
                                + " explained."),
                200);
        assertEquals("0", scalar("SELECT count(*) FROM order_fills"));
        assertEquals("0", scalar("SELECT count(*) FROM client_trades"));
        assertEquals("0", scalar("SELECT count(*) FROM cash_movements"));
    }

    @Test
    void readOnlyAndRevokedGrantsDoNotAuthorizeCommandsOrReceiptRecovery() throws Exception {
        String reference = create("OTHER");
        String key = UUID.randomUUID().toString();
        var body = Map.<String, Object>of("expectedWorkflowVersion", 1, "assignee", staffId);
        post(staff, base(reference) + "/workflow/assignment", key, body, 200);
        sql(
                "UPDATE operations_access_grants SET revoked_at=now() WHERE staff_user_id="
                        + staffId
                        + " AND permission='CASE_UPDATE'");
        var actions = read(staff, base(reference), 200).path("actions");
        assertFalse(actions.path("reply").asBoolean());
        assertTrue(actions.path("transitions").isEmpty());
        read(staff, base(reference) + "/workflow/requests/" + key, 403);
        post(staff, base(reference) + "/workflow/assignment", key, body, 403);
    }

    JsonNode protectedGet(String path, String context, int expected) throws Exception {
        return json.readTree(
                mvc.perform(
                                get(path)
                                        .header("Authorization", "Bearer " + compliance)
                                        .header("X-Compliance-Context", context))
                        .andExpect(status().is(expected))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    JsonNode protectedPost(String path, String context, Map<String, Object> body, int expected)
            throws Exception {
        return json.readTree(
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post(path)
                                        .header("Authorization", "Bearer " + compliance)
                                        .header("X-Compliance-Context", context)
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(body)))
                        .andExpect(status().is(expected))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    String reviewContext(String reference, String type) throws Exception {
        return post(
                        compliance,
                        "/api/compliance/access-contexts",
                        UUID.randomUUID().toString(),
                        Map.of(
                                "purposeCode",
                                "CASE_REVIEW",
                                "caseReference",
                                reference,
                                "referenceType",
                                type),
                        201)
                .path("contextId")
                .asText();
    }

    @Test
    void managedContextNarrowsSearchCountsSectionsAndArchivesAndInvalidatesOnReviewChange()
            throws Exception {
        script("backend/src/test/resources/provider_accounts.sql");
        int actor =
                Integer.parseInt(scalar("SELECT user_id FROM users WHERE username='demo.client'"));
        int instrument =
                Integer.parseInt(
                        scalar("SELECT instrument_id FROM instruments WHERE ticker='AAPL'"));
        banking.save(
                account,
                actor,
                null,
                new com.neueda.leap.dto.BankingDtos.BankRequest(
                        "Synthetic reference", "000000001", "00005678", true, null));
        when(quotes.quote(any()))
                .thenAnswer(
                        invocation -> {
                            Policy p = invocation.getArgument(0);
                            return new Quote(
                                    UUID.randomUUID(),
                                    p.instrumentId(),
                                    p.providerSymbol(),
                                    p.currency(),
                                    p.rawPriceUnit(),
                                    p.priceUnit(),
                                    new BigDecimal("49"),
                                    new BigDecimal("50"),
                                    new BigDecimal("49"),
                                    new BigDecimal("50"),
                                    now,
                                    now,
                                    "TEST",
                                    "Synthetic scoped fixture",
                                    "modelled",
                                    "open",
                                    false,
                                    now);
                        });
        for (int i = 0; i < 25; i++)
            trading.submit(
                    account,
                    actor,
                    UUID.randomUUID().toString(),
                    new Command(instrument, "BUY", "1", "MARKET"));
        int order = Integer.parseInt(scalar("SELECT min(trade_id) FROM client_trades"));
        String reference =
                post(
                                client,
                                "/api/support/cases",
                                UUID.randomUUID().toString(),
                                Map.of(
                                        "category",
                                        "ORDER_QUESTION",
                                        "subject",
                                        "Review this retained order",
                                        "description",
                                        "Please review this retained order without inferring"
                                                + " external settlement.",
                                        "orderId",
                                        order),
                                200)
                        .path("reference")
                        .asText();
        command(reference, "assignment", 1, Map.of("assignee", staffId), 200);
        String review =
                post(
                                staff,
                                base(reference) + "/referrals",
                                UUID.randomUUID().toString(),
                                Map.of(
                                        "expectedWorkflowVersion",
                                        2,
                                        "reviewer",
                                        reviewer,
                                        "reasonCode",
                                        "PROVENANCE",
                                        "summary",
                                        "Review this verified owned order source and its"
                                                + " limitations."),
                                200)
                        .path("investigationReference")
                        .asText();
        String externalContext = reviewContext("EXTERNAL-REVIEW-123", "EXTERNAL");
        var page = protectedGet("/api/compliance/investigations?pageSize=1", externalContext, 200);
        assertEquals(25, page.path("total").asInt());
        assertEquals(1, page.path("items").size());
        var foreignArchive =
                protectedPost(
                        "/api/compliance/evidence/exports",
                        externalContext,
                        Map.of("orderId", order + 1),
                        201);
        String context = reviewContext(review, "MANAGED");
        var narrowed = protectedGet("/api/compliance/investigations?pageSize=20", context, 200);
        assertEquals(1, narrowed.path("total").asInt());
        assertEquals(order, narrowed.path("items").get(0).path("orderId").asInt());
        protectedGet("/api/compliance/orders/" + (order + 1) + "/evidence", context, 404);
        protectedGet("/api/compliance/orders/" + (order + 1) + "/evidence/events", context, 404);
        protectedPost(
                "/api/compliance/evidence/exports", context, Map.of("orderId", order + 1), 404);
        String foreignReference = foreignArchive.path("requestReference").asText();
        protectedGet("/api/compliance/evidence/exports/" + foreignReference, context, 404);
        protectedGet(
                "/api/compliance/evidence/exports/" + foreignReference + "/download", context, 404);
        assertEquals(
                0, protectedGet("/api/compliance/evidence", context, 200).path("total").asInt());
        protectedPost("/api/compliance/evidence/exports", context, Map.of("orderId", order), 201);
        assertEquals(
                1, protectedGet("/api/compliance/evidence", context, 200).path("total").asInt());
        assertEquals("1", scalar("SELECT count(*) FROM compliance_investigation_exports"));
        sql(
                "INSERT INTO order_quote_observations(quote_id,order_id,stage,facts,recorded_at) "
                        + "SELECT gen_random_uuid(),"
                        + order
                        + ",'EXECUTION','{\"source\":\"Synthetic capacity"
                        + " fixture\"}'::jsonb,TIMESTAMPTZ '"
                        + now
                        + "' FROM"
                        + " generate_series(1,2000-(SELECT count(*) FROM order_quote_observations"
                        + " WHERE order_id="
                        + order
                        + "))");
        assertEquals(
                "2000",
                scalar("SELECT count(*) FROM order_quote_observations WHERE order_id=" + order));
        protectedPost("/api/compliance/evidence/exports", context, Map.of("orderId", order), 201);
        String retainedExports = scalar("SELECT count(*) FROM compliance_evidence_exports");
        sql(
                "INSERT INTO order_quote_observations(quote_id,order_id,stage,facts,recorded_at)"
                        + " VALUES(gen_random_uuid(),"
                        + order
                        + ",'EXECUTION','{\"source\":\"Synthetic over-capacity"
                        + " fixture\"}',TIMESTAMPTZ '"
                        + now
                        + "')");
        assertEquals(
                "EVIDENCE_LIMIT",
                protectedPost(
                                "/api/compliance/evidence/exports",
                                context,
                                Map.of("orderId", order),
                                409)
                        .path("code")
                        .asText());
        assertEquals(retainedExports, scalar("SELECT count(*) FROM compliance_evidence_exports"));
        post(
                compliance,
                "/api/compliance/cases/" + review + "/actions",
                UUID.randomUUID().toString(),
                Map.of(
                        "expectedVersion",
                        1,
                        "action",
                        "TRANSITION",
                        "status",
                        "IN_REVIEW",
                        "reason",
                        "Begin the separately scoped assigned investigation."),
                200);
        assertEquals(
                "CONTEXT_INVALID",
                protectedGet("/api/compliance/investigations", context, 403).path("code").asText());
        String replacement = reviewContext(review, "MANAGED");
        assertEquals(
                1,
                protectedGet("/api/compliance/investigations", replacement, 200)
                        .path("total")
                        .asInt());
    }

    @Test
    void requiredAuditFailureRollsBackWorkflowAndPreservesNonemptyFinancialEvidence()
            throws Exception {
        script("backend/src/test/resources/provider_accounts.sql");
        var tables =
                List.of(
                        "client_cash_accounts",
                        "client_positions",
                        "client_trades",
                        "order_execution_jobs",
                        "order_reservations",
                        "order_fills",
                        "cash_journal",
                        "position_journal",
                        "cash_movements");
        Map<String, String> before = new TreeMap<>();
        for (String table : tables)
            before.put(
                    table,
                    scalar(
                            "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY"
                                    + " to_jsonb(t)::text),'[]')::text FROM "
                                    + table
                                    + " t"));
        assertNotEquals("[]", before.get("cash_journal"));
        String reference = create("OTHER");
        sql(
                """
CREATE FUNCTION fail_workflow_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.action LIKE 'CASE_WORKFLOW_%' THEN RAISE EXCEPTION 'Injected audit failure'; END IF; RETURN NEW; END $$;
CREATE TRIGGER fail_workflow_audit BEFORE INSERT ON operations_access_events
FOR EACH ROW EXECUTE FUNCTION fail_workflow_audit()
""");
        command(reference, "assignment", 1, Map.of("assignee", staffId), 503);
        assertEquals("1", scalar("SELECT version FROM support_case_workflow"));
        assertNull(scalar("SELECT assignee FROM support_case_workflow"));
        assertEquals("0", scalar("SELECT count(*) FROM support_case_workflow_requests"));
        assertEquals("1", scalar("SELECT count(*) FROM support_case_workflow_events"));
        for (String table : tables)
            assertEquals(
                    before.get(table),
                    scalar(
                            "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY"
                                    + " to_jsonb(t)::text),'[]')::text FROM "
                                    + table
                                    + " t"),
                    table);
    }

    @Test
    void everyCategoryRetainsAnEvidenceBoundedDispositionAndFreshReopenChecklist()
            throws Exception {
        sql(
                "INSERT INTO financial_migration_exceptions(source_reference,kind,client_id,facts)"
                        + " VALUES('matrix-history','UNKNOWN_CASH_CURRENCY',"
                        + account
                        + ",'{\"cashBalance\":\"32\"}')");
        String historical =
                scalar(
                        "SELECT exception_id FROM financial_migration_exceptions"
                                + " WHERE source_reference='matrix-history'");
        var dispositions =
                Map.of(
                        "ORDER_QUESTION", "DUPLICATE_NOT_CONFIRMED",
                        "ACCOUNT_REVIEW", "EXPLAINED_BALANCE",
                        "HISTORICAL_RECORD", "HISTORICAL_RECORD_EXPLAINED",
                        "TRADING_AVAILABILITY", "PREREQUISITE_EXPLAINED",
                        "OTHER", "GENERAL_GUIDANCE_PROVIDED");
        for (var category : dispositions.entrySet()) {
            String reference = create(category.getKey());
            command(reference, "assignment", version(reference), Map.of("assignee", staffId), 200);
            for (var task : read(staff, base(reference) + "/workbench", 200).path("tasks")) {
                String code = task.path("code").asText();
                var body = new HashMap<String, Object>();
                body.put("code", code);
                body.put(
                        "reason",
                        "Reviewed scoped source facts and documented the evidence limitations.");
                body.put("state", code.equals("ORDER_EVIDENCE") ? "UNAVAILABLE" : "COMPLETED");
                if (Set.of(
                                "ACCOUNT_RESOURCES",
                                "ACCOUNT_DEPENDENCIES",
                                "AVAILABILITY_ACCOUNT",
                                "AVAILABILITY_CONTROLS")
                        .contains(code)) {
                    body.put("evidenceKind", "ACCOUNT");
                    body.put("evidenceReference", reference);
                    var foreign = new HashMap<>(body);
                    foreign.put("evidenceReference", "CASE-aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
                    command(reference, "tasks", version(reference), foreign, 404);
                } else if (Set.of("HISTORY_SOURCE", "HISTORY_PROVENANCE").contains(code)) {
                    body.put("evidenceKind", "HISTORICAL");
                    body.put("evidenceReference", historical);
                }
                command(reference, "tasks", version(reference), body, 200);
            }
            if (category.getKey().equals("ACCOUNT_REVIEW"))
                command(
                        reference,
                        "resolution",
                        version(reference),
                        Map.of(
                                "expectedVersion",
                                1,
                                "code",
                                "CLIENT_SETUP_COMPLETED",
                                "body",
                                "This must not claim an unavailable bank prerequisite."),
                        409);
            if (category.getKey().equals("TRADING_AVAILABILITY"))
                command(
                        reference,
                        "resolution",
                        version(reference),
                        Map.of(
                                "expectedVersion",
                                1,
                                "code",
                                "POLICY_CHANGE_VERIFIED",
                                "body",
                                "This must not claim a policy change without its independent"
                                        + " audit."),
                        409);
            command(
                    reference,
                    "resolution",
                    version(reference),
                    Map.of(
                            "expectedVersion",
                            1,
                            "code",
                            category.getValue(),
                            "body",
                            "We reviewed the available records and explained their limitations; no"
                                    + " financial correction or execution is claimed."),
                    200);
            var resolved = read(client, "/api/support/cases/" + reference, 200);
            assertEquals("RESOLVED", resolved.path("status").asText());
            assertEquals(category.getKey(), resolved.path("category").asText());
            assertFalse(resolved.has("assignee"));
            assertFalse(
                    read(staff, "/api/operations/cases", 200)
                            .findValuesAsText("reference")
                            .contains(reference));
            assertTrue(
                    read(staff, "/api/operations/cases?view=ALL", 200)
                            .findValuesAsText("reference")
                            .contains(reference));
            assertEquals(
                    1,
                    read(staff, "/api/operations/cases?view=RESOLVED", 200).path("total").asInt());
            assertEquals(
                    1,
                    read(staff, "/api/operations/cases?status=RESOLVED", 200)
                            .path("total")
                            .asInt());
            post(
                    client,
                    "/api/support/cases/" + reference + "/reopen",
                    UUID.randomUUID().toString(),
                    Map.of(
                            "expectedVersion",
                            2,
                            "body",
                            "The explanation needs additional follow-up on this issue."),
                    200);
            var reopened = read(staff, base(reference) + "/workbench", 200);
            assertEquals(2, reopened.path("checklistRevision").asInt());
            for (var task : reopened.path("tasks"))
                assertEquals("PENDING", task.path("state").asText());
        }
        assertEquals("5", scalar("SELECT count(*) FROM support_case_resolutions"));
        assertEquals("0", scalar("SELECT count(*) FROM order_fills"));
        assertEquals("0", scalar("SELECT count(*) FROM cash_movements"));
    }

    @Test
    void privateQueueScopesBeforeCountAndFiltersExplicitRecordedDueTimes() throws Exception {
        String first = create("OTHER");
        create("ACCOUNT_REVIEW");
        post(
                other,
                "/api/support/cases",
                UUID.randomUUID().toString(),
                Map.of(
                        "category",
                        "OTHER",
                        "subject",
                        "Another account's private issue",
                        "description",
                        "This report must stay outside the scoped queue."),
                200);
        sql(
                "UPDATE operations_access_grants SET revoked_at=now() WHERE staff_user_id="
                        + staffId
                        + " AND permission='CASE_READ' AND client_id<>"
                        + account);
        command(first, "assignment", version(first), Map.of("assignee", staffId), 200);
        command(
                first,
                "priority",
                version(first),
                Map.of(
                        "priority",
                        "HIGH",
                        "reason",
                        "A deliberate attributable priority decision."),
                200);
        command(
                first,
                "next-action",
                version(first),
                Map.of(
                        "nextAction",
                        "Follow up with the approved incident owner.",
                        "dueAt",
                        now.plusSeconds(60).toString()),
                200);
        assertEquals(
                0,
                read(staff, "/api/operations/cases?overdueOnly=true", 200).path("total").asInt());
        when(clock.instant()).thenReturn(now.plusSeconds(120));
        var page = read(staff, "/api/operations/cases?pageSize=1", 200);
        assertEquals(2, page.path("total").asInt());
        assertEquals(1, page.path("items").size());
        assertTrue(page.path("workflowEnabled").asBoolean());
        assertEquals(first, page.path("items").get(0).path("reference").asText());
        var filtered =
                read(
                        staff,
                        "/api/operations/cases?assignee="
                                + staffId
                                + "&priority=HIGH&category=OTHER&overdueOnly=true",
                        200);
        assertEquals(1, filtered.path("total").asInt());
        assertEquals(first, filtered.path("items").get(0).path("reference").asText());
        assertEquals(
                1, read(staff, "/api/operations/cases?view=UNASSIGNED", 200).path("total").asInt());
        assertEquals(
                first,
                read(staff, "/api/operations/cases?view=MINE", 200)
                        .path("items")
                        .get(0)
                        .path("reference")
                        .asText());
        assertEquals(
                0,
                read(staff, "/api/operations/cases?view=AWAITING_CLIENT", 200)
                        .path("total")
                        .asInt());
        assertEquals(
                0,
                read(staff, "/api/operations/cases?view=AWAITING_REVIEW", 200)
                        .path("total")
                        .asInt());
        assertEquals(
                0, read(staff, "/api/operations/cases?view=RESOLVED", 200).path("total").asInt());
        post(
                staff,
                base(first) + "/referrals",
                UUID.randomUUID().toString(),
                Map.of(
                        "expectedWorkflowVersion",
                        version(first),
                        "reviewer",
                        reviewer,
                        "reasonCode",
                        "POLICY",
                        "summary",
                        "Independent review under the approved queue scope."),
                200);
        assertEquals(
                first,
                read(staff, "/api/operations/cases?view=AWAITING_REVIEW&pageSize=1", 200)
                        .path("items")
                        .get(0)
                        .path("reference")
                        .asText());
        assertEquals(2, read(staff, "/api/operations/cases?view=ALL", 200).path("total").asInt());
        read(staff, "/api/operations/cases?view=UNKNOWN", 400);
        post(
                staff,
                base(first) + "/transitions",
                UUID.randomUUID().toString(),
                Map.of(
                        "expectedVersion",
                        1,
                        "status",
                        "AWAITING_CLIENT",
                        "body",
                        "Please provide the safe clarification needed for this review."),
                200);
        assertEquals(
                1,
                read(staff, "/api/operations/cases?view=AWAITING_CLIENT", 200)
                        .path("total")
                        .asInt());
        var publicPage = read(client, "/api/support/cases", 200);
        assertFalse(publicPage.has("workflowEnabled"));
        assertTrue(publicPage.findValues("assignee").isEmpty());
        assertTrue(publicPage.findValues("priority").isEmpty());
    }

    @Test
    void accountWideAvailabilityDoesNotInventAnInstrumentPolicyOrQuote() throws Exception {
        String reference = create("TRADING_AVAILABILITY");
        var observation = read(staff, base(reference) + "/diagnostics?kind=availability", 200);
        assertTrue(observation.path("policy").isNull());
        assertTrue(observation.path("policyStatus").asText().startsWith("NOT_APPLICABLE"));
        assertTrue(observation.path("quoteEligibility").asText().startsWith("UNKNOWN"));
        assertTrue(observation.has("account"));
        assertTrue(observation.has("observedAt"));
        read(staff, base(reference) + "/diagnostics?kind=availability&evidence=2147483647", 404);
        assertEquals("0", scalar("SELECT count(*) FROM client_trades"));
        assertEquals("0", scalar("SELECT count(*) FROM order_quote_observations"));
    }

    List<Map.Entry<Integer, JsonNode>> concurrently(
            String path,
            String firstKey,
            String secondKey,
            Map<String, Object> first,
            Map<String, Object> second)
            throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<Map.Entry<Integer, JsonNode>>>();
            for (int i = 0; i < 2; i++) {
                String key = i == 0 ? firstKey : secondKey;
                var body = i == 0 ? first : second;
                results.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    if (!start.await(10, TimeUnit.SECONDS))
                                        throw new IllegalStateException(
                                                "Concurrent start timed out");
                                    var response =
                                            mvc.perform(
                                                            org.springframework.test.web.servlet
                                                                    .request.MockMvcRequestBuilders
                                                                    .post(path)
                                                                    .header(
                                                                            "Authorization",
                                                                            "Bearer " + staff)
                                                                    .header("Idempotency-Key", key)
                                                                    .contentType("application/json")
                                                                    .content(
                                                                            json.writeValueAsString(
                                                                                    body)))
                                                    .andReturn()
                                                    .getResponse();
                                    return Map.entry(
                                            response.getStatus(),
                                            json.readTree(response.getContentAsString()));
                                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    results.get(0).get(20, TimeUnit.SECONDS),
                    results.get(1).get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentReferralReplayIsUniqueAndCompetingPrivateVersionsConflict() throws Exception {
        String reference = create("OTHER");
        command(reference, "assignment", 1, Map.of("assignee", staffId), 200);
        String key = UUID.randomUUID().toString();
        var body =
                Map.<String, Object>of(
                        "expectedWorkflowVersion",
                        version(reference),
                        "reviewer",
                        reviewer,
                        "reasonCode",
                        "POLICY",
                        "summary",
                        "Please independently review this approved source summary.");
        var referrals = concurrently(base(reference) + "/referrals", key, key, body, body);
        assertEquals(200, referrals.get(0).getKey());
        assertEquals(200, referrals.get(1).getKey());
        assertEquals(referrals.get(0).getValue(), referrals.get(1).getValue());
        assertEquals("1", scalar("SELECT count(*) FROM compliance_investigations"));
        assertEquals("1", scalar("SELECT count(*) FROM support_case_referrals"));
        long expected = version(reference);
        var first =
                Map.<String, Object>of(
                        "expectedWorkflowVersion",
                        expected,
                        "priority",
                        "HIGH",
                        "reason",
                        "First deliberate concurrent priority change.");
        var second =
                Map.<String, Object>of(
                        "expectedWorkflowVersion",
                        expected,
                        "priority",
                        "LOW",
                        "reason",
                        "Second deliberate concurrent priority change.");
        var priorities =
                concurrently(
                        base(reference) + "/workflow/priority",
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        first,
                        second);
        assertEquals(
                Set.of(200, 409), Set.of(priorities.get(0).getKey(), priorities.get(1).getKey()));
        assertEquals(expected + 1, version(reference));
        assertEquals(
                1, read(client, "/api/support/cases/" + reference, 200).path("version").asInt());
    }
}
