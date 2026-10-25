package com.neueda.leap.advisor;

import static org.junit.jupiter.api.Assertions.*;

import com.neueda.leap.dto.AdvisorWorkspaceDtos.*;
import com.neueda.leap.service.AdvisorWorkspaceService;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;

/** Disposable PostgreSQL integration. Never points at Compose or retained user data. */
@SpringBootTest(properties = {"trading.admission-enabled=false", "trading.worker-enabled=false",
        "trading.local-demo-setup=false"})
class AdvisorWorkspacePostgresTest {
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:15-alpine");
    static { DB.start(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired AdvisorWorkspaceService service;
    int advisor, clientUser, otherClientUser, client, otherClient, held, eligible;

    Connection connection() throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }

    void sql(String statement) throws Exception {
        try (var c = connection(); var s = c.createStatement()) { s.execute(statement); }
    }

    void script(String path) throws Exception { sql(Files.readString(Path.of("../" + path))); }

    String scalar(String statement) throws Exception {
        try (var c = connection(); var s = c.createStatement(); var rs = s.executeQuery(statement)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    int number(String statement) throws Exception { return Integer.parseInt(scalar(statement)); }

    @BeforeEach
    void setup() throws Exception {
        sql("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        script("database/fresh/001_schema.sql");
        script("database/fresh/002_reference.sql");
        script("database/demo/role_workspaces.sql");
        advisor = number("SELECT user_id FROM users WHERE username='demo.advisor'");
        clientUser = number("SELECT user_id FROM users WHERE username='demo.client'");
        otherClientUser = number("SELECT user_id FROM users WHERE username='demo.client2'");
        client = number("SELECT client_id FROM clients WHERE user_id=" + clientUser);
        otherClient = number("SELECT client_id FROM clients WHERE user_id=" + otherClientUser);
        sql("INSERT INTO instruments(ticker,instrument_name,currency,is_active) VALUES ('ADV-HELD','Held instrument','USD',true),('ADV-NEW','Eligible instrument','USD',true)");
        held = number("SELECT instrument_id FROM instruments WHERE ticker='ADV-HELD'");
        eligible = number("SELECT instrument_id FROM instruments WHERE ticker='ADV-NEW'");
        sql("INSERT INTO client_positions(client_id,instrument_id,posted,reserved,available_for_trading) VALUES (" + client + "," + held + ",0.125,0.125,false)");
    }

    void status(int expected, Runnable operation) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, operation::run).getStatusCode().value());
    }

    @Test
    void freshSchemaContainsDedicatedAdvisoryHistory() throws Exception {
        assertEquals("advisor_guidance", scalar("SELECT to_regclass('public.advisor_guidance')::text"));
        assertEquals("advisor_recommendations", scalar("SELECT to_regclass('public.advisor_recommendations')::text"));
    }

    @Test
    void listsOnlyAssignedClientsAndReadsExactQuarantinedHoldings() {
        var clients = service.clients(advisor, 0, 20, "Demo");
        assertEquals(1, clients.total());
        assertEquals(client, clients.items().get(0).clientId());
        var position = service.portfolio(advisor, client).currentPositions().get(0);
        assertEquals(0, new BigDecimal("0.125").compareTo(new BigDecimal(position.owned())));
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(position.available())));
        status(404, () -> service.portfolio(advisor, otherClient));
        status(404, () -> service.guidance(advisor, otherClient));
        status(404, () -> service.recommendations(advisor, otherClient));
        status(403, () -> service.clients(clientUser, 0, 20, ""));
    }

    @Test
    void savesAndClearsGuidanceWithAtomicAttributedImmutableHistory() throws Exception {
        var before = financialSnapshot();
        var first = service.saveGuidance(advisor, client, held, new GuidanceRequest("HOLD", " Keep ", null));
        assertEquals(1, first.version());
        assertEquals("Keep", first.reasoning());
        assertEquals("Demo Advisor", first.authorName());
        status(409, () -> service.saveGuidance(advisor, client, held, new GuidanceRequest("SELL", null, null)));
        var cleared = service.saveGuidance(advisor, client, held, new GuidanceRequest("NONE", "Clearing", 1L));
        assertEquals(2, cleared.version());
        assertNull(cleared.reasoning());
        assertEquals("NONE", service.guidance(advisor, client).items().get(0).status());
        status(409, () -> service.saveGuidance(advisor, client, held, new GuidanceRequest("SELL", null, 1L)));
        assertEquals("2", scalar("SELECT count(*) FROM advisor_guidance_history"));
        assertEquals(Integer.toString(advisor), scalar("SELECT author_user_id FROM advisor_guidance_history ORDER BY version DESC LIMIT 1"));
        assertThrows(SQLException.class, () -> sql("UPDATE advisor_guidance_history SET reasoning='erase'"));
        assertThrows(SQLException.class, () -> sql("DELETE FROM advisor_guidance_history"));
        assertThrows(SQLException.class, () -> sql("TRUNCATE advisor_guidance_history"));
        assertEquals(before, financialSnapshot());
    }

    @Test
    void recommendationsUseOwnedNotAvailableAndArchiveRatherThanDelete() throws Exception {
        var before = financialSnapshot();
        status(409, () -> service.createRecommendation(advisor, client, new RecommendationRequest(held, "Fully reserved still held")));
        sql("INSERT INTO client_positions(client_id,instrument_id,posted,reserved) VALUES (" + client + "," + eligible + ",0,0)");
        var created = service.createRecommendation(advisor, client, new RecommendationRequest(eligible, " New idea "));
        assertEquals("New idea", created.reasoning());
        status(409, () -> service.createRecommendation(advisor, client, new RecommendationRequest(eligible, "Duplicate")));
        status(404, () -> service.updateRecommendation(advisor, otherClient, created.recommendationId(), new RecommendationUpdateRequest("Guessed", 1L)));
        status(404, () -> service.archiveRecommendation(advisor, otherClient, created.recommendationId(), new ArchiveRequest(1L)));
        var changed = service.updateRecommendation(advisor, client, created.recommendationId(), new RecommendationUpdateRequest("Revised", 1L));
        assertEquals(2, changed.version());
        status(409, () -> service.archiveRecommendation(advisor, client, created.recommendationId(), new ArchiveRequest(1L)));
        var archived = service.archiveRecommendation(advisor, client, created.recommendationId(), new ArchiveRequest(2L));
        assertEquals(3, archived.version());
        assertTrue(service.recommendations(advisor, client).items().isEmpty());
        assertEquals("1", scalar("SELECT count(*) FROM advisor_recommendations WHERE archived_at IS NOT NULL"));
        assertEquals("3", scalar("SELECT count(*) FROM advisor_recommendation_history"));
        assertThrows(SQLException.class, () -> sql("DELETE FROM advisor_recommendations"));
        assertEquals(before.get(0), financialSnapshot().get(0)); // cash unchanged; zero-position fixture was added explicitly
    }

    @Test
    void acquisitionHidesActiveRecommendationButPreservesHistoryAndAllowsArchive() throws Exception {
        var item = service.createRecommendation(advisor, client, new RecommendationRequest(eligible, "Before acquisition"));
        sql("INSERT INTO client_positions(client_id,instrument_id,posted,reserved) VALUES (" + client + "," + eligible + ",0.000000000001,0.000000000001)");
        assertTrue(service.recommendations(advisor, client).items().isEmpty());
        assertTrue(service.clientAdvisory(clientUser).recommendations().isEmpty());
        status(409, () -> service.updateRecommendation(advisor, client, item.recommendationId(), new RecommendationUpdateRequest("Now held", 1L)));
        assertEquals("1", scalar("SELECT count(*) FROM advisor_recommendation_history"));
        service.archiveRecommendation(advisor, client, item.recommendationId(), new ArchiveRequest(1L));
        assertEquals("2", scalar("SELECT count(*) FROM advisor_recommendation_history"));
    }

    @Test
    void clientReadsDeriveOwnershipAndDisabledAdvisorCannotWrite() throws Exception {
        service.saveGuidance(advisor, client, held, new GuidanceRequest("SELL", null, null));
        assertEquals("Demo Advisor", service.clientAdvisor(clientUser).advisor().name());
        assertNull(service.clientAdvisor(clientUser).assignedAt());
        assertEquals("PENDING", service.clientAdvisor(otherClientUser).assignmentStatus());
        assertTrue(service.clientAdvisory(otherClientUser).guidance().isEmpty());
        assertEquals(1, service.clientAdvisory(clientUser).guidance().size());
        sql("UPDATE users SET enabled=false WHERE user_id=" + advisor);
        status(403, () -> service.saveGuidance(advisor, client, held, new GuidanceRequest("NONE", null, 1L)));
    }

    @Test
    void concurrentFirstWritesProduceOneVersionAndOneHistoryEvent() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Integer> create = () -> {
                start.await();
                try { service.saveGuidance(advisor, client, held, new GuidanceRequest("HOLD", null, null)); return 200; }
                catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            };
            var a = executor.submit(create);
            var b = executor.submit(create);
            start.countDown();
            assertEquals(Set.of(200, 409), Set.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)));
        } finally { executor.shutdownNow(); }
        assertEquals("1", scalar("SELECT count(*) FROM advisor_guidance_history"));
    }

    @Test
    void advisorAAndBCannotReadOrMutateEachOthersGuessedRecords() throws Exception {
        sql("INSERT INTO users(username,email,password_hash,display_name,enabled) VALUES ('advisor.b','advisor.b@example.test','fixture-not-a-credential','Advisor B',true)");
        int b = number("SELECT user_id FROM users WHERE username='advisor.b'");
        sql("INSERT INTO user_roles(user_id,role_id) SELECT " + b + ",role_id FROM roles WHERE role_name='ADVISOR';"
                + "INSERT INTO advisors(user_id,advisor_name) VALUES (" + b + ",'Advisor B');"
                + "UPDATE clients SET advisor_id=(SELECT advisor_id FROM advisors WHERE user_id=" + b + ") WHERE client_id=" + otherClient);
        var recommendation = service.createRecommendation(b, otherClient, new RecommendationRequest(eligible, "Advisor B idea"));
        assertEquals(1, service.clients(b, 0, 20, "").total());
        assertEquals(otherClient, service.clients(b, 0, 20, "").items().get(0).clientId());
        status(404, () -> service.portfolio(advisor, otherClient));
        status(404, () -> service.guidance(advisor, otherClient));
        status(404, () -> service.recommendations(advisor, otherClient));
        status(404, () -> service.updateRecommendation(advisor, client, recommendation.recommendationId(), new RecommendationUpdateRequest("Guessed id", 1L)));
        status(404, () -> service.archiveRecommendation(advisor, client, recommendation.recommendationId(), new ArchiveRequest(1L)));
        status(404, () -> service.portfolio(b, client));
        assertEquals("Advisor B idea", service.recommendations(b, otherClient).items().get(0).reasoning());
    }

    @Test
    void recommendationWaitsForTradingClientLockAndRereadsCommittedOwnership() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try (var trade = connection()) {
            trade.setAutoCommit(false);
            try (var statement = trade.createStatement()) {
                statement.execute("SELECT client_id FROM clients WHERE client_id=" + client + " FOR UPDATE");
                statement.execute("INSERT INTO client_positions(client_id,instrument_id,posted,reserved) VALUES (" + client + "," + eligible + ",0.000000000001,0)");
            }
            var started = new CountDownLatch(1);
            var future = executor.submit(() -> {
                started.countDown();
                try { service.createRecommendation(advisor, client, new RecommendationRequest(eligible, "Racing acquisition")); return 201; }
                catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            trade.commit();
            assertEquals(409, future.get(10, TimeUnit.SECONDS));
            assertEquals("0", scalar("SELECT count(*) FROM advisor_recommendations"));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void nonOwnerRuntimeCanWriteProjectionButCannotForgeOrEraseHistory() throws Exception {
        sql("DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='advisor_test_runtime') THEN CREATE ROLE advisor_test_runtime NOLOGIN; END IF; END $$;"
                + "GRANT USAGE ON SCHEMA public TO advisor_test_runtime;"
                + "GRANT SELECT ON clients,users,advisors,roles,user_roles,instruments,client_positions,advisor_guidance_history,advisor_recommendation_history TO advisor_test_runtime;"
                + "GRANT UPDATE ON clients TO advisor_test_runtime;"
                + "GRANT SELECT,INSERT,UPDATE ON advisor_guidance,advisor_recommendations TO advisor_test_runtime;"
                + "GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO advisor_test_runtime;");
        int advisorId = number("SELECT advisor_id FROM advisors WHERE user_id=" + advisor);
        try (var runtime = connection(); var statement = runtime.createStatement()) {
            statement.execute("SET ROLE advisor_test_runtime");
            statement.execute("INSERT INTO advisor_guidance(client_id,instrument_id,status,author_advisor_id,author_user_id) VALUES (" + client + "," + held + ",'HOLD'," + advisorId + "," + advisor + ")");
            assertEquals("1", scalar("SELECT count(*) FROM advisor_guidance_history"));
            for (String forbidden : List.of("DELETE FROM advisor_guidance_history", "TRUNCATE advisor_guidance_history",
                    "INSERT INTO advisor_guidance_history(guidance_id,client_id,instrument_id,status,reasoning,author_advisor_id,author_user_id,version,created_at,updated_at,action) "
                        + "SELECT guidance_id,client_id,instrument_id,status,reasoning,author_advisor_id,author_user_id,version,created_at,updated_at,action FROM advisor_guidance_history",
                    "SELECT append_advisor_history()")) {
                assertEquals("42501", assertThrows(SQLException.class, () -> statement.execute(forbidden)).getSQLState());
            }
        }
        assertEquals("1", scalar("SELECT count(*) FROM advisor_guidance_history"));
    }

    List<String> financialSnapshot() throws Exception {
        return List.of(scalar("SELECT coalesce(jsonb_agg(to_jsonb(t))::text,'[]') FROM client_cash_accounts t"),
                scalar("SELECT coalesce(jsonb_agg(to_jsonb(t))::text,'[]') FROM client_positions t"),
                scalar("SELECT count(*) FROM client_trades"), scalar("SELECT count(*) FROM order_reservations"),
                scalar("SELECT count(*) FROM cash_journal"), scalar("SELECT count(*) FROM position_journal"));
    }
}