package com.neueda.leap.trading;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.domain.FauxnanceQuote;
import com.neueda.leap.domain.FauxnanceQuoteMeta;
import com.neueda.leap.domain.FauxnanceQuoteResponse;
import com.neueda.leap.mapper.TradingMapper;
import com.neueda.leap.service.TradingService;
import com.neueda.leap.service.impl.PortfolioValuationService;
import com.neueda.leap.trading.TradingModels.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "trading.admission-enabled=true",
            "trading.worker-enabled=false",
            "banking.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "operations.access-enabled=true",
            "operations.allowed-purposes=ORDER_REVIEW,INCIDENT",
            "operations.policy-version=test-v1",
            "operations.context-seconds=600",
            "operations.export-seconds=600"
        })
class TradingPostgresTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");
    static final String external = System.getenv("ISOLATED_TEST_DB_URL");
    static String url;

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
        r.add("spring.datasource.url", () -> url);
        r.add(
                "spring.datasource.username",
                () -> external == null ? POSTGRES.getUsername() : "test");
        r.add(
                "spring.datasource.password",
                () -> external == null ? POSTGRES.getPassword() : "test");
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired TradingService service;
    @Autowired TradingTransactions tx;
    @Autowired DemoAccountSetup demoSetup;
    @Autowired CatalogSetup catalogSetup;
    @Autowired TradingMapper db;
    @Autowired ObjectMapper json;
    @Autowired com.neueda.leap.service.BankingService banking;
    @Autowired PortfolioValuationService valuations;

    @MockBean Clock clock;
    @MockBean TradingQuotes quotes;
    @MockBean BookingProbe probe;
    @MockBean com.neueda.leap.service.FauxnanceQuoteService provider;
    @MockBean com.neueda.leap.service.FauxnanceDailyCandleService providerCandles;
    Instant now = Instant.parse("2026-10-14T14:00:00Z");
    int client, actor, instrument;

    Connection connection() throws Exception {
        return DriverManager.getConnection(
                url,
                external == null ? POSTGRES.getUsername() : "test",
                external == null ? POSTGRES.getPassword() : "test");
    }

    void sql(String sql) throws Exception {
        try (var c = connection();
                var s = c.createStatement()) {
            s.execute(sql);
        }
    }

    void script(String path) throws Exception {
        sql(Files.readString(Path.of("../" + path)));
    }

    String scalar(String sql) throws Exception {
        try (var c = connection();
                var s = c.createStatement();
                var r = s.executeQuery(sql)) {
            r.next();
            return r.getString(1);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        sql("DROP SCHEMA public CASCADE;CREATE SCHEMA public");
        script("database/schema/001_tadpoles_schema.sql");
        script("database/migrations/003_workspace_roles.sql");
        script("database/migrations/004_client_simulation.sql");
        sql("INSERT INTO schema_migrations(version) VALUES(3) ON CONFLICT DO NOTHING");
        script("database/demo/role_workspaces.sql");
        script("database/migrations/005_client_banking.sql");
        script("database/migrations/006_trading_cutover.sql");
        script("database/migrations/007_trading_openings.sql");
        script("database/migrations/008_trading_controls.sql");
        script("database/migrations/009_quote_cache_age.sql");
        script("database/migrations/010_quote_cache_twenty_minutes.sql");
        script("database/migrations/011_operations_workspace.sql");
        script("database/migrations/012_managed_support.sql");
        script("database/migrations/013_compliance_workspace.sql");
        script("database/migrations/014_compliance_context_scope.sql");
        script("database/migrations/015_compliance_scope_compatibility.sql");
        script("database/migrations/016_support_workflow.sql");
        script("database/migrations/017_compliance_referrals.sql");
        script("database/migrations/018_advisor_workspace.sql");
        script("database/migrations/019_instrument_exchange.sql");
        script("database/migrations/020_user_trade_access.sql");
        script("database/migrations/021_quote_stale_after_thirty_minutes.sql");
        script("backend/src/test/resources/provider_accounts.sql");
        client =
                Integer.parseInt(
                        scalar(
                                "SELECT client_id FROM clients JOIN users USING(user_id) WHERE"
                                        + " username='demo.client'"));
        actor = Integer.parseInt(scalar("SELECT user_id FROM users WHERE username='demo.client'"));
        instrument =
                Integer.parseInt(
                        scalar("SELECT instrument_id FROM instruments WHERE ticker='AAPL'"));
        when(clock.instant()).thenAnswer(i -> now);
        banking.save(
                client,
                actor,
                null,
                new com.neueda.leap.dto.BankingDtos.BankRequest(
                        "Fixture reference", "000000001", "00005678", true, null));
        when(quotes.quote(any())).thenAnswer(i -> quote(i.getArgument(0), "49", "50"));
    }

    @Test
    void thirtyMinuteQuoteMigrationWidensOldDefaultsAndKeepsCustomLimits() throws Exception {
        sql("ALTER TABLE instrument_trading_policies DROP CONSTRAINT"
                + " instrument_trading_policies_quote_max_age_seconds_check");
        sql("ALTER TABLE instrument_trading_policies ADD CONSTRAINT"
                + " instrument_trading_policies_quote_max_age_seconds_check"
                + " CHECK (quote_max_age_seconds BETWEEN 1 AND 1200)");
        sql("UPDATE instrument_trading_policies SET quote_max_age_seconds = 1200"
                + " WHERE provider_symbol = 'AAPL'");
        sql("INSERT INTO instruments(instrument_name,ticker,currency,asset_class,security_type)"
                + " VALUES ('Custom age test','CUSTOM','USD','EQUITY','STOCK')");
        String customInstrument = scalar(
                "SELECT instrument_id FROM instruments WHERE ticker='CUSTOM'");
        sql("INSERT INTO instrument_trading_policies (instrument_id,policy_version,venue,"
                + "quantity_unit,price_unit,currency,quantity_step,currency_scale,max_quantity,"
                + "max_notional,quote_max_age_seconds,future_skew_seconds,retry_seconds,"
                + "max_attempts,session,enabled,raw_price_unit,price_multiplier,provider_symbol,"
                + "buy_allowed,sell_allowed) SELECT " + customInstrument + ",policy_version,venue,"
                + "quantity_unit,price_unit,currency,quantity_step,currency_scale,max_quantity,"
                + "max_notional,30,future_skew_seconds,retry_seconds,max_attempts,session,enabled,"
                + "raw_price_unit,price_multiplier,'CUSTOM',buy_allowed,sell_allowed "
                + "FROM instrument_trading_policies WHERE provider_symbol='AAPL'");
        script("database/migrations/021_quote_stale_after_thirty_minutes.sql");
        assertEquals("1800", scalar("SELECT quote_max_age_seconds FROM"
                + " instrument_trading_policies WHERE provider_symbol = 'AAPL'"));
        assertEquals("30", scalar("SELECT quote_max_age_seconds FROM"
                + " instrument_trading_policies WHERE provider_symbol = 'CUSTOM'"));
    }

    Quote quote(Policy p, String bid, String ask) {
        if (p == null) return null;
        return new Quote(
                UUID.randomUUID(),
                p.instrumentId(),
                p.providerSymbol(),
                p.currency(),
                p.rawPriceUnit(),
                p.priceUnit(),
                new BigDecimal(bid),
                new BigDecimal(ask),
                new BigDecimal(bid).multiply(p.priceMultiplier()),
                new BigDecimal(ask).multiply(p.priceMultiplier()),
                now,
                now,
                "TEST",
                "Synthetic fixture",
                "modelled",
                "open",
                false,
                now);
    }

    Command buy(String quantity) {
        return new Command(instrument, "BUY", quantity, "MARKET");
    }

    Map<String, Object> submit(String key, Command c) {
        return service.submit(client, actor, key, c);
    }

    int id(Map<String, Object> receipt) {
        return ((Number) receipt.get("orderId")).intValue();
    }

    String key() {
        return UUID.randomUUID().toString();
    }

    void fill(int id, String bid, String ask) {
        var claim = tx.claim(id);
        assertNotNull(claim);
        tx.book(claim, quote(claim.policy(), bid, ask), null);
    }

    void equalDecimal(String expected, String query) throws Exception {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(scalar(query))), query);
    }

    void reconciled() throws Exception {
        assertEquals(
                "0",
                scalar(
                        "SELECT count(*) FROM client_cash_accounts c WHERE c.posted<>(SELECT"
                                + " COALESCE(sum(delta),0) FROM cash_journal j WHERE"
                                + " j.client_id=c.client_id AND j.currency=c.currency)"));
        assertEquals(
                "0",
                scalar(
                        "SELECT count(*) FROM client_positions p WHERE p.posted<>(SELECT"
                                + " COALESCE(sum(delta),0) FROM position_journal j WHERE"
                                + " j.client_id=p.client_id AND j.instrument_id=p.instrument_id)"));
    }

    com.fasterxml.jackson.databind.JsonNode decisionContext(int order, String stage)
            throws Exception {
        return json.readTree(
                scalar(
                        "SELECT c.facts::text FROM order_decision_contexts c "
                                + "JOIN order_rule_decisions d USING(decision_id) WHERE d.order_id="
                                + order
                                + " AND d.stage='"
                                + stage
                                + "'"));
    }

    String checkResult(com.fasterxml.jackson.databind.JsonNode context, String code) {
        for (var check : context.path("checks"))
            if (code.equals(check.path("code").asText())) return check.path("result").asText();
        throw new AssertionError("Missing check " + code);
    }

    @Test
    void buyPartialAndFullSaleUseExecutionSideAndNeverResurrectPositions() throws Exception {
        var preview = service.preview(client, buy("10"));
        assertEquals("500", preview.gross());
        assertEquals("9500", preview.projectedCash());
        assertEquals("0", scalar("SELECT count(*) FROM client_trades"));
        int order = id(submit(key(), buy("10")));
        assertEquals("ACCEPTED", db.workerOrder(order).get("status"));
        equalDecimal(
                "500",
                "SELECT reserved FROM client_cash_accounts WHERE client_id="
                        + client
                        + " AND currency='USD'");
        var admission = decisionContext(order, "ADMISSION");
        assertEquals("PASS", checkResult(admission, "INSUFFICIENT_CASH"));
        assertEquals("NOT_EVALUATED", checkResult(admission, "INSUFFICIENT_UNITS"));
        assertEquals("10000", admission.path("resources").path("cashAvailable").asText());
        fill(order, "50", "51");
        var execution = decisionContext(order, "EXECUTION");
        assertEquals("PASS", checkResult(execution, "INSUFFICIENT_EXECUTION_CASH"));
        assertEquals("500", execution.path("resources").path("reservationAmount").asText());
        assertEquals(1, db.positions(client).size(), "A filled buy creates a visible position");
        int sell = id(submit(key(), new Command(instrument, "SELL", "4", "MARKET")));
        fill(sell, "52", "53");
        equalDecimal(
                "9698",
                "SELECT posted FROM client_cash_accounts WHERE client_id="
                        + client
                        + " AND currency='USD'");
        assertEquals(1, db.positions(client).size(), "A partial sale keeps the position visible");
        int remaining = id(submit(key(), new Command(instrument, "SELL", "6", "MARKET")));
        assertEquals(
                1, db.positions(client).size(), "Fully reserved shares remain owned until filled");
        fill(remaining, "52", "53");
        assertTrue(
                db.positions(client).isEmpty(),
                "A full sale removes the ticker from the portfolio");
        equalDecimal(
                "0",
                "SELECT posted FROM client_positions WHERE client_id="
                        + client
                        + " AND instrument_id="
                        + instrument);
        assertEquals(
                "1", scalar("SELECT count(*) FROM client_positions WHERE client_id=" + client));
        reconciled();
        script("backend/src/test/resources/provider_accounts.sql");
        equalDecimal(
                "10010",
                "SELECT posted FROM client_cash_accounts WHERE client_id="
                        + client
                        + " AND currency='USD'");
        reconciled();
        assertTrue(db.positions(client).isEmpty(), "Setup must not resurrect a closed position");
        sql(
                "UPDATE instrument_trading_policies SET quantity_step=0.5 WHERE instrument_id="
                        + instrument);
        int rebuy = id(submit(key(), buy("0.5")));
        fill(rebuy, "49", "50");
        assertEquals(
                1, db.positions(client).size(), "Even a fractional repurchase restores the ticker");
        reconciled();
    }

    @Test
    void investingValueUsesCommittedCashAndOwnedSharesThroughoutSettlement() throws Exception {
        var quoteData = new FauxnanceQuote();
        quoteData.setSymbol("AAPL");
        quoteData.setCurrency("USD");
        quoteData.setPrice(new BigDecimal("100"));
        quoteData.setAsOf(now.atOffset(ZoneOffset.UTC));
        var quoteMeta = new FauxnanceQuoteMeta();
        quoteMeta.setSymbol("AAPL");
        quoteMeta.setSource("Test provider");
        quoteMeta.setStale(false);
        quoteMeta.setAsOf(now.atOffset(ZoneOffset.UTC));
        var response = new FauxnanceQuoteResponse();
        response.setData(quoteData);
        response.setMeta(quoteMeta);
        when(provider.getQuote("AAPL"))
                .thenAnswer(
                        invocation -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    "Provider I/O must run after the ledger snapshot transaction"
                                            + " closes");
                            return response;
                        });

        int buyOrder = id(submit(key(), buy("2")));
        assertEquals("10000.00", valuations.value(client).totalValue());
        equalDecimal("100", "SELECT reserved FROM client_cash_accounts WHERE client_id=" + client);
        fill(buyOrder, "49", "50");
        assertEquals("9900.00", valuations.value(client).cashBalance());
        assertEquals("10100.00", valuations.value(client).totalValue());

        int saleOrder = id(submit(key(), new Command(instrument, "SELL", "2", "MARKET")));
        assertEquals("200.00", valuations.value(client).holdingsValue());
        assertEquals("9900.00", valuations.value(client).cashBalance());
        fill(saleOrder, "90", "91");
        assertEquals("0.00", valuations.value(client).holdingsValue());
        assertEquals("10080.00", valuations.value(client).totalValue());
        equalDecimal("0", "SELECT reserved FROM client_cash_accounts WHERE client_id=" + client);
        reconciled();
    }

    @Test
    void duplicateRequestsCanonicalizeAndReturnBeforeQuoteFetch() throws Exception {
        String key = key();
        var first = submit(key, buy("1.0"));
        reset(quotes);
        when(quotes.quote(any())).thenThrow(new RuntimeException("offline"));
        assertEquals(first.get("orderId"), submit(key, buy("1.00")).get("orderId"));
        verifyNoInteractions(quotes);
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> submit(key, buy("2")));
        assertEquals("1", scalar("SELECT count(*) FROM order_execution_jobs"));
    }

    @Test
    void parallelReservationsAndSameKeyRaceCannotOvercommit() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Map<String, Object>> command =
                    () -> {
                        barrier.await();
                        return submit(key(), buy("150"));
                    };
            var a = pool.submit(command);
            var b = pool.submit(command);
            assertEquals(
                    Set.of("ACCEPTED", "REJECTED"),
                    Set.of(a.get().get("status"), b.get().get("status")));
            String key = key();
            var same = new CyclicBarrier(2);
            Callable<Map<String, Object>> duplicate =
                    () -> {
                        same.await();
                        return submit(key, buy("1"));
                    };
            var c = pool.submit(duplicate);
            var d = pool.submit(duplicate);
            assertEquals(c.get().get("orderId"), d.get().get("orderId"));
        } finally {
            pool.shutdownNow();
        }
        equalDecimal(
                "7550",
                "SELECT reserved FROM client_cash_accounts WHERE client_id="
                        + client
                        + " AND currency='USD'");
    }

    @Test
    void higherExecutionPriceCannotSpendOtherReservationsAndReleaseOccursOnce() throws Exception {
        int first = id(submit(key(), buy("100")));
        submit(key(), buy("100"));
        var claim = tx.claim(first);
        var q = quote(claim.policy(), "50", "51");
        tx.book(claim, q, null);
        tx.book(claim, q, null);
        assertEquals("REJECTED", db.workerOrder(first).get("status"));
        var evidence = decisionContext(first, "EXECUTION");
        assertEquals("FAIL", checkResult(evidence, "INSUFFICIENT_EXECUTION_CASH"));
        assertEquals("NOT_EVALUATED", checkResult(evidence, "INSUFFICIENT_EXECUTION_UNITS"));
        assertEquals("5000", evidence.path("resources").path("reservationAmount").asText());
        equalDecimal(
                "5000",
                "SELECT reserved FROM client_cash_accounts WHERE client_id="
                        + client
                        + " AND currency='USD'");
        equalDecimal(
                "10000",
                "SELECT posted FROM client_cash_accounts WHERE client_id="
                        + client
                        + " AND currency='USD'");
        reconciled();
    }

    @Test
    void staleClaimCannotBookAfterRestartAndLeaseRecovery() throws Exception {
        int id = id(submit(key(), buy("1")));
        var old = tx.claim(id);
        now = now.plusSeconds(31);
        var recovered = tx.claim(id);
        assertNotNull(recovered);
        tx.book(old, quote(old.policy(), "49", "50"), null);
        assertNull(db.fillFor(id));
        tx.book(recovered, quote(recovered.policy(), "49", "51"), null);
        assertEquals("FILLED", db.workerOrder(id).get("status"));
        reconciled();
    }

    @Test
    void everyBookingWriteRollsBackThenRetriesOnce() throws Exception {
        for (String point :
                List.of(
                        "evidence",
                        "position",
                        "cash",
                        "fill",
                        "cashJournal",
                        "positionJournal",
                        "event",
                        "outbox",
                        "job")) {
            int id = id(submit(key(), buy("1")));
            var claim = tx.claim(id);
            doThrow(new IllegalStateException("injected " + point)).when(probe).after(point);
            assertThrows(
                    IllegalStateException.class,
                    () -> tx.book(claim, quote(claim.policy(), "49", "50"), null));
            assertNull(db.fillFor(id));
            assertEquals(
                    "0",
                    scalar(
                            "SELECT count(*) FROM order_decision_contexts c JOIN"
                                    + " order_rule_decisions d USING(decision_id) WHERE"
                                    + " d.stage='EXECUTION' AND d.order_id="
                                    + id));
            assertEquals("ACTIVE", db.reservationFor(id).get("state"));
            assertEquals("EXECUTING", db.workerOrder(id).get("status"));
            reconciled();
            reset(probe);
            var q = quote(claim.policy(), "49", "50");
            tx.book(claim, q, null);
            tx.book(claim, q, null);
            assertEquals("FILLED", db.workerOrder(id).get("status"));
            reconciled();
        }
        assertEquals("9", scalar("SELECT count(*) FROM order_fills"));
    }

    @Test
    void rejectsOversellAndRetainsOwnedRequestIsolation() throws Exception {
        String key = key();
        var rejected = submit(key, new Command(instrument, "SELL", "1", "MARKET"));
        assertEquals("REJECTED", rejected.get("status"));
        assertNull(rejected.get("acceptedAt"));
        assertEquals("ADMISSION", rejected.get("rejectionStage"));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.byRequest(client + 1, key));
        assertEquals("0", scalar("SELECT count(*) FROM order_reservations"));
    }

    @Test
    void appendOnlyEvidenceAndLegacyWritesAreProtected() throws Exception {
        int id = id(submit(key(), buy("1")));
        fill(id, "49", "50");
        assertThrows(SQLException.class, () -> sql("UPDATE order_fills SET price=1"));
        assertThrows(SQLException.class, () -> sql("DELETE FROM cash_journal"));
        assertThrows(
                SQLException.class,
                () -> sql("UPDATE client_trades SET quantity=2 WHERE trade_id=" + id));
        assertThrows(
                SQLException.class,
                () -> sql("UPDATE clients SET cash_balance=100 WHERE client_id=" + client));
        assertThrows(
                SQLException.class,
                () ->
                        sql(
                                "INSERT INTO"
                                    + " client_holdings(client_id,instrument_id,quantity,as_of_date)"
                                    + " VALUES("
                                        + client
                                        + ","
                                        + instrument
                                        + ",1,current_date)"));
        reconciled();
    }

    String login(String username) throws Exception {
        var response =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "username",
                                                                username,
                                                                "password",
                                                                "RoleDemo2026!"))))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .status()
                                        .isOk())
                        .andReturn()
                        .getResponse();
        return json.readTree(response.getContentAsString()).get("token").asText();
    }

    @Test
    void realHttpOwnershipRolesSessionRevocationAndAdmissionContracts() throws Exception {
        String token = login("demo.client"), other = login("demo.client2"), key = key();
        var accepted =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/orders")
                                        .header("Authorization", "Bearer " + token)
                                        .header("Idempotency-Key", key)
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(buy("1"))))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .status()
                                        .isAccepted())
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .header()
                                        .exists("Location"))
                        .andReturn()
                        .getResponse();
        int id = json.readTree(accepted.getContentAsString()).get("orderId").asInt();
        for (String path :
                List.of(
                        "/api/orders/" + id,
                        "/api/orders/" + id + "/events",
                        "/api/orders/by-request/" + key)) {
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                            path)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isOk());
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                            path)
                                    .header("Authorization", "Bearer " + other))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isNotFound());
        }
        for (String username :
                List.of("demo.operations", "demo.compliance", "demo.analyst", "demo.finance")) {
            String staff = login(username);
            for (String path : List.of("/api/orders", "/api/orders/preview"))
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post(path)
                                        .header("Authorization", "Bearer " + staff)
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(buy("1"))))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .status()
                                        .isForbidden());
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                            "/api/workspace/client/order-events/stream")
                                    .header("Authorization", "Bearer " + staff))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isForbidden());
        }
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/orders")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", key())
                                .contentType("application/json")
                                .content(
                                        "{\"instrumentId\":"
                                                + instrument
                                                + ",\"side\":\"BUY\",\"quantity\":\"1\",\"orderType\":\"MARKET\",\"clientId\":999}"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isBadRequest());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/auth/logout")
                                .header("Authorization", "Bearer " + token))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isNoContent());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/users/me")
                                .header("Authorization", "Bearer " + token))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isUnauthorized());
        // Logout does not cancel a committed order.
        fill(id, "49", "50");
        assertEquals("FILLED", db.workerOrder(id).get("status"));
        reconciled();
    }

    @Test
    void releaseRollbackAndTransportDeadlineAreDurable() throws Exception {
        int id = id(submit(key(), buy("1")));
        var claim = tx.claim(id);
        tx.book(claim, null, "TIMEOUT");
        assertNull(db.fillFor(id));
        assertEquals("ACTIVE", db.reservationFor(id).get("state"));
        now = now.plusSeconds(121);
        var expired = tx.claim(id);
        assertNotNull(expired);
        doThrow(new IllegalStateException("release failure")).when(probe).after("release");
        assertThrows(IllegalStateException.class, () -> tx.book(expired, null, "TIMEOUT"));
        assertEquals("ACTIVE", db.reservationFor(id).get("state"));
        reset(probe);
        tx.book(expired, null, "TIMEOUT");
        assertEquals("RELEASED", db.reservationFor(id).get("state"));
        assertEquals("REJECTED", db.workerOrder(id).get("status"));
        reconciled();
    }

    @Test
    void nonUsdPolicyIsNotAnImplicitCurrencyConversion() throws Exception {
        sql(
                "INSERT INTO instruments(instrument_name,ticker,currency) VALUES('UK"
                        + " test','TEST-UK','GBP')");
        int unsupported =
                Integer.parseInt(
                        scalar("SELECT instrument_id FROM instruments WHERE ticker='TEST-UK'"));
        assertFalse(
                service.preview(client, new Command(unsupported, "BUY", "1", "MARKET")).eligible());
        assertEquals(
                "REJECTED",
                submit(key(), new Command(unsupported, "BUY", "1", "MARKET")).get("status"));
        reconciled();
    }

    @Test
    void concurrentFirstPositionFillsAndSellsSerializeWithoutLostUnits() throws Exception {
        int first = id(submit(key(), buy("5"))), second = id(submit(key(), buy("5")));
        var a = tx.claim(first);
        var b = tx.claim(second);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CyclicBarrier(2);
            var one =
                    pool.submit(
                            () -> {
                                start.await();
                                tx.book(a, quote(a.policy(), "49", "50"), null);
                                return true;
                            });
            var two =
                    pool.submit(
                            () -> {
                                start.await();
                                tx.book(b, quote(b.policy(), "49", "50"), null);
                                return true;
                            });
            assertTrue(one.get(20, TimeUnit.SECONDS));
            assertTrue(two.get(20, TimeUnit.SECONDS));
            equalDecimal(
                    "10",
                    "SELECT posted FROM client_positions WHERE client_id="
                            + client
                            + " AND instrument_id="
                            + instrument);
            var sellStart = new CyclicBarrier(2);
            Callable<Map<String, Object>> sell =
                    () -> {
                        sellStart.await();
                        return submit(key(), new Command(instrument, "SELL", "7", "MARKET"));
                    };
            var c = pool.submit(sell);
            var d = pool.submit(sell);
            assertEquals(
                    Set.of("ACCEPTED", "REJECTED"),
                    Set.of(
                            c.get(20, TimeUnit.SECONDS).get("status"),
                            d.get(20, TimeUnit.SECONDS).get("status")));
            equalDecimal(
                    "7",
                    "SELECT reserved FROM client_positions WHERE client_id="
                            + client
                            + " AND instrument_id="
                            + instrument);
            reconciled();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectedHistoryFiltersAndExactQuoteContract() throws Exception {
        String token = login("demo.client");
        submit(key(), new Command(instrument, "SELL", "1", "MARKET"));
        submit(key(), buy("1"));
        var response =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .get("/api/orders?rejectionStage=ADMISSION")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .status()
                                        .isOk())
                        .andReturn()
                        .getResponse();
        var body = json.readTree(response.getContentAsString());
        assertEquals(1, body.get("items").size());
        assertEquals("ADMISSION", body.get("items").get(0).get("rejectionStage").asText());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/orders?rejectionStage=INVALID")
                                .header("Authorization", "Bearer " + token))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isBadRequest());
        var preview =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/orders/preview")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(buy("1"))))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .status()
                                        .isOk())
                        .andReturn()
                        .getResponse();
        assertTrue(json.readTree(preview.getContentAsString()).get("price").isTextual());
    }

    @Test
    void registrationAndRepeatedOnboardingKeepConfiguredAccountsAtZero() throws Exception {
        var registered =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/auth/register/client")
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "username",
                                                                "new.client",
                                                                "password",
                                                                "RoleDemo2026!",
                                                                "email",
                                                                "new@example.test",
                                                                "displayName",
                                                                "Test Client",
                                                                "clientName",
                                                                "Test Client"))))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .status()
                                        .isCreated())
                        .andReturn()
                        .getResponse();
        int newClient = json.readTree(registered.getContentAsString()).get("clientId").asInt();
        assertEquals(1, db.balances(newClient).size());
        String token = login("new.client");
        var profile =
                new com.neueda.leap.dto.CompleteClientOnboardingRequestDto(
                        "Test Client",
                        null,
                        LocalDate.of(1990, 1, 1),
                        "Test address",
                        null,
                        "Test city",
                        "Test state",
                        "00000",
                        "US",
                        "EMPLOYED",
                        BigDecimal.ZERO,
                        "LOW",
                        "GROWTH",
                        "EMAIL",
                        true,
                        false);
        for (int repeat = 0; repeat < 2; repeat++)
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                            "/api/users/me/onboarding")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType("application/json")
                                    .content(json.writeValueAsString(profile)))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isOk());
        assertEquals(1, db.balances(newClient).size());
        equalDecimal(
                "0",
                "SELECT sum(posted+reserved) FROM client_cash_accounts WHERE client_id="
                        + newClient);
        assertEquals("0", scalar("SELECT count(*) FROM cash_journal WHERE client_id=" + newClient));
        // Real JWT expiry is enforced independently of logout.
        now = now.plusSeconds(3601);
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/users/me")
                                .header("Authorization", "Bearer " + token))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isUnauthorized());
    }

    @Test
    void ownedBankReferencesAndIdempotentInternalCashMovements() throws Exception {
        String token = login("demo.client"), other = login("demo.client2");
        String body =
                "{\"nickname\":\"Test"
                    + " bank\",\"routingNumber\":\"000000001\",\"accountNumber\":\"00001234\",\"defaultAccount\":true}";
        var created =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/workspace/client/banks")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .status()
                                        .isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var bank = json.readTree(created);
        String bankId = bank.get("bankId").asText();
        assertFalse(created.contains("00001234"));
        assertFalse(created.contains("000000001"));
        assertTrue(created.contains("1234"));
        assertFalse(scalar("SELECT encrypted_details FROM bank_references").contains("00001234"));
        String key = key(),
                deposit =
                        json.writeValueAsString(
                                Map.of(
                                        "bankId",
                                        bankId,
                                        "direction",
                                        "DEPOSIT",
                                        "amount",
                                        "100.00"));
        for (int repeat = 0; repeat < 2; repeat++)
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .post("/api/workspace/client/cash-movements")
                                    .header("Authorization", "Bearer " + token)
                                    .header("Idempotency-Key", key)
                                    .contentType("application/json")
                                    .content(deposit))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isOk())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.status")
                                    .value("SETTLED"));
        equalDecimal(
                "10100",
                "SELECT posted FROM client_cash_accounts WHERE client_id="
                        + client
                        + " AND currency='USD'");
        assertEquals("1", scalar("SELECT count(*) FROM cash_movements"));
        reconciled();
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/workspace/client/cash-movements")
                                .header("Authorization", "Bearer " + other)
                                .header("Idempotency-Key", key())
                                .contentType("application/json")
                                .content(deposit))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isNotFound());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/workspace/client/cash-movements")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", key)
                                .contentType("application/json")
                                .content(deposit.replace("100.00", "101.00")))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isConflict());
        int order = id(submit(key(), buy("200")));
        String withdrawal =
                json.writeValueAsString(
                        Map.of("bankId", bankId, "direction", "WITHDRAW", "amount", "101.00"));
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/workspace/client/cash-movements")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", key())
                                .contentType("application/json")
                                .content(withdrawal))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.status")
                                .value("REJECTED"));
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                        "/api/workspace/client/banks/" + bankId + "?version=1")
                                .header("Authorization", "Bearer " + token))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isConflict());
        fill(order, "49", "50");
        reconciled();
        assertEquals("2", scalar("SELECT count(*) FROM cash_movement_outbox"));
    }

    @Test
    void bankMetadataEditAdvancesOneVersionAndReceiptsKeepTheirSnapshot() throws Exception {
        var bank =
                banking.save(
                        client,
                        actor,
                        null,
                        new com.neueda.leap.dto.BankingDtos.BankRequest(
                                "First bank", "000000001", "00001234", true, null));
        String key = key();
        var receipt =
                banking.move(
                        client,
                        actor,
                        key,
                        new com.neueda.leap.dto.BankingDtos.MovementRequest(
                                bank.bankId(), "DEPOSIT", "10.00"));
        var changed =
                banking.save(
                        client,
                        actor,
                        bank.bankId(),
                        new com.neueda.leap.dto.BankingDtos.BankRequest(
                                "Renamed bank", null, null, true, bank.version()));
        assertEquals(bank.version() + 1, changed.version());
        assertEquals(receipt, banking.byRequest(client, key));
        assertTrue(receipt.bankSnapshot().contains("First bank"));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> banking.remove(client, actor, bank.bankId(), bank.version()));
        banking.remove(client, actor, bank.bankId(), changed.version());
        assertEquals(1, banking.banks(client).size());
        assertEquals(
                receipt,
                banking.move(
                        client,
                        actor,
                        key,
                        new com.neueda.leap.dto.BankingDtos.MovementRequest(
                                bank.bankId(), "DEPOSIT", "10")));
        reconciled();
    }

    @Test
    void everyCashMovementWriteRollsBackAndSameKeyRecoversOnce() throws Exception {
        var bank =
                banking.save(
                        client,
                        actor,
                        null,
                        new com.neueda.leap.dto.BankingDtos.BankRequest(
                                "Test bank", "000000001", "00001234", true, null));
        for (String direction : List.of("DEPOSIT", "WITHDRAW"))
            for (String point :
                    List.of(
                            "movement",
                            "movementReservation",
                            "movementCash",
                            "movementJournal",
                            "movementOutbox")) {
                String key = key();
                var command =
                        new com.neueda.leap.dto.BankingDtos.MovementRequest(
                                bank.bankId(), direction, "1.01");
                String before =
                        scalar(
                                "SELECT posted FROM client_cash_accounts WHERE client_id="
                                        + client
                                        + " AND currency='USD'");
                doThrow(new IllegalStateException("injected")).when(probe).after(point);
                assertThrows(
                        IllegalStateException.class,
                        () -> banking.move(client, actor, key, command));
                equalDecimal(
                        before,
                        "SELECT posted FROM client_cash_accounts WHERE client_id="
                                + client
                                + " AND currency='USD'");
                equalDecimal(
                        "0",
                        "SELECT reserved FROM client_cash_accounts WHERE client_id="
                                + client
                                + " AND currency='USD'");
                assertEquals(
                        "0",
                        scalar(
                                "SELECT count(*) FROM cash_movements WHERE request_key='"
                                        + key
                                        + "'"));
                reset(probe);
                var receipt = banking.move(client, actor, key, command);
                assertEquals(receipt, banking.move(client, actor, key, command));
                reconciled();
            }
    }

    @Test
    void concurrentFundingCapsAndBuyVersusWithdrawalUseTheSameCashLock() throws Exception {
        var bank =
                banking.save(
                        client,
                        actor,
                        null,
                        new com.neueda.leap.dto.BankingDtos.BankRequest(
                                "Test bank", "000000001", "00001234", true, null));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CyclicBarrier(2);
            var buy =
                    pool.submit(
                            () -> {
                                start.await();
                                return submit(key(), buy("150")).get("status");
                            });
            var withdraw =
                    pool.submit(
                            () -> {
                                start.await();
                                return banking.move(
                                                client,
                                                actor,
                                                key(),
                                                new com.neueda.leap.dto.BankingDtos.MovementRequest(
                                                        bank.bankId(), "WITHDRAW", "7500"))
                                        .status();
                            });
            String buyResult = buy.get(20, TimeUnit.SECONDS).toString(),
                    withdrawResult = withdraw.get(20, TimeUnit.SECONDS);
            assertTrue(
                    (buyResult.equals("ACCEPTED") && withdrawResult.equals("REJECTED"))
                            || (buyResult.equals("REJECTED") && withdrawResult.equals("SETTLED")));
            var deposits = new CyclicBarrier(2);
            Callable<String> deposit =
                    () -> {
                        deposits.await();
                        return banking.move(
                                        client,
                                        actor,
                                        key(),
                                        new com.neueda.leap.dto.BankingDtos.MovementRequest(
                                                bank.bankId(), "DEPOSIT", "6000"))
                                .status();
                    };
            var a = pool.submit(deposit);
            var b = pool.submit(deposit);
            assertEquals(
                    Set.of("SETTLED", "REJECTED"),
                    Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
            reconciled();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void operationsOwnInstrumentPolicyAndOldAdminPathCannotBypassIt() throws Exception {
        var response = new FauxnanceTradingQuotesTest().response();
        response.getData().setSymbol("MSFT");
        response.getMeta().setSymbol("MSFT");
        when(provider.getQuote("MSFT")).thenReturn(response);
        sql("INSERT INTO roles(role_name) VALUES('ADMIN') ON CONFLICT DO NOTHING");
        sql(
                "INSERT INTO users(username,email,password_hash,display_name,enabled) SELECT"
                        + " 'demo.admin','admin@test.invalid',password_hash,'Test admin',true FROM"
                        + " users WHERE username='demo.client'");
        sql(
                "INSERT INTO user_roles SELECT user_id,role_id FROM users CROSS JOIN roles WHERE"
                        + " username='demo.admin' AND role_name='ADMIN'");
        String token = login("demo.operations");
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/workspace/operations/instruments/test?symbol=MSFT")
                                .header("Authorization", "Bearer " + token))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.recognized")
                                .value(true));
        var body =
                Map.ofEntries(
                        Map.entry("ticker", "MSFT"),
                        Map.entry("name", "Microsoft"),
                        Map.entry("providerSymbol", "MSFT"),
                        Map.entry("venue", "NASDAQ"),
                        Map.entry("quantityStep", "1"),
                        Map.entry("maxQuantity", "1000"),
                        Map.entry("maxNotional", "100000"),
                        Map.entry("quoteMaxAgeSeconds", 30),
                        Map.entry("active", true),
                        Map.entry("buyAllowed", true),
                        Map.entry("sellAllowed", true),
                        Map.entry("reason", "Enable validated USD equity"),
                        Map.entry("revision", 0));
        String payload = json.writeValueAsString(body);
        var saved =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/workspace/operations/instruments")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType("application/json")
                                        .content(payload))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .status()
                                        .isCreated())
                        .andReturn()
                        .getResponse();
        int target = json.readTree(saved.getContentAsString()).get("instrumentId").asInt();
        for (String username :
                List.of(
                        "demo.client",
                        "demo.admin",
                        "demo.finance",
                        "demo.compliance",
                        "demo.analyst")) {
            String denied = login(username);
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .post("/api/workspace/operations/instruments")
                                    .header("Authorization", "Bearer " + denied)
                                    .contentType("application/json")
                                    .content(payload))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isForbidden());
        }
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/instruments")
                                .header("Authorization", "Bearer " + login("demo.admin"))
                                .contentType("application/json")
                                .content(payload))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isMethodNotAllowed());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                        "/api/workspace/operations/instruments/" + target)
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(payload))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isConflict());
        assertEquals(
                "1",
                scalar(
                        "SELECT count(*) FROM instrument_policy_audit WHERE instrument_id="
                                + target));
        sql(
                "INSERT INTO user_roles SELECT user_id,role_id FROM users CROSS JOIN roles WHERE"
                        + " username='demo.operations' AND role_name='CLIENT'");
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/workspace/operations/instruments")
                                .header("Authorization", "Bearer " + login("demo.operations")))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isForbidden());
    }

    @Test
    void savedInstrumentIsImmediatelyVisibleToOperationsAndEnabledClientCatalogs()
            throws Exception {
        var response = new FauxnanceTradingQuotesTest().response();
        response.getData().setSymbol("SPY");
        response.getMeta().setSymbol("SPY");
        when(provider.getQuote("SPY")).thenReturn(response);
        String operations = login("demo.operations");
        String customer = login("demo.client");
        var request =
                new com.neueda.leap.dto.InstrumentPolicyRequest(
                        "SPY",
                        "SPDR S&P 500 ETF Trust",
                        "SPY",
                        "NYSE Arca",
                        "1",
                        "1000",
                        "100000",
                        1200,
                        true,
                        true,
                        true,
                        "Add verified provider instrument",
                        0);
        var saved =
                mvc.perform(
                                post("/api/workspace/operations/instruments")
                                        .header("Authorization", "Bearer " + operations)
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse();
        int target = json.readTree(saved.getContentAsString()).get("instrumentId").asInt();
        mvc.perform(
                        get("/api/workspace/operations/instruments/" + target)
                                .header("Authorization", "Bearer " + operations))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("SPY"));
        mvc.perform(
                        get("/api/workspace/operations/instruments?search=SPY")
                                .header("Authorization", "Bearer " + operations))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instrumentId").value(target));
        mvc.perform(
                        get("/api/workspace/client/instruments?search=SPY")
                                .header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].instrumentId").value(target));
        var persisted = json.readTree(saved.getContentAsString());
        var edited =
                new com.neueda.leap.dto.InstrumentPolicyRequest(
                        "SPY",
                        "Updated fund name",
                        "SPY",
                        "NYSE Arca",
                        persisted.get("quantityStep").asText(),
                        persisted.get("maxQuantity").asText(),
                        persisted.get("maxNotional").asText(),
                        1200,
                        true,
                        false,
                        true,
                        "Retain exits while reviewing buys",
                        1);
        var excessivePrecision = json.valueToTree(edited);
        ((com.fasterxml.jackson.databind.node.ObjectNode) excessivePrecision)
                .put("quantityStep", "0.000000001000");
        mvc.perform(
                        put("/api/workspace/operations/instruments/" + target)
                                .header("Authorization", "Bearer " + operations)
                                .contentType("application/json")
                                .content(json.writeValueAsString(excessivePrecision)))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        put("/api/workspace/operations/instruments/" + target)
                                .header("Authorization", "Bearer " + operations)
                                .contentType("application/json")
                                .content(json.writeValueAsString(edited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));
        mvc.perform(
                        get("/api/workspace/client/instruments?search=SPY")
                                .header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].instrumentName").value("Updated fund name"));
        var halted =
                new com.neueda.leap.dto.InstrumentPolicyRequest(
                        "SPY",
                        "Updated fund name",
                        "SPY",
                        "NYSE Arca",
                        "0.5",
                        "1000",
                        "100000",
                        1200,
                        false,
                        false,
                        false,
                        "Temporarily halt this instrument",
                        2);
        mvc.perform(
                        put("/api/workspace/operations/instruments/" + target)
                                .header("Authorization", "Bearer " + operations)
                                .contentType("application/json")
                                .content(json.writeValueAsString(halted)))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/workspace/client/instruments?search=SPY")
                                .header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(
                        get("/api/workspace/operations/instruments/" + target)
                                .header("Authorization", "Bearer " + operations))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(
                        get("/api/workspace/operations/instruments/" + target)
                                .header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/workspace/operations/instruments/2147483647")
                                .header("Authorization", "Bearer " + operations))
                .andExpect(status().isNotFound());
    }

    @Test
    void operationsSellOnlyHaltRejectsPendingBuyButAllowsExit() throws Exception {
        int opening = id(submit(key(), buy("1")));
        fill(opening, "49", "50");
        int pending = id(submit(key(), buy("1")));
        var response = new FauxnanceTradingQuotesTest().response();
        response.getData().setSymbol("AAPL");
        response.getMeta().setSymbol("AAPL");
        when(provider.getQuote("AAPL")).thenReturn(response);
        long revision =
                Long.parseLong(
                        scalar(
                                "SELECT revision FROM instrument_trading_policies WHERE"
                                        + " instrument_id="
                                        + instrument));
        var policy =
                new com.neueda.leap.dto.InstrumentPolicyRequest(
                        "AAPL",
                        "Apple Inc.",
                        "AAPL",
                        "NASDAQ",
                        "1",
                        "1000",
                        "100000",
                        30,
                        true,
                        false,
                        true,
                        "Allow existing holders to exit",
                        revision);
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                        "/api/workspace/operations/instruments/" + instrument)
                                .header("Authorization", "Bearer " + login("demo.operations"))
                                .contentType("application/json")
                                .content(json.writeValueAsString(policy)))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());
        fill(pending, "49", "50");
        assertEquals("REJECTED", db.workerOrder(pending).get("status"));
        int exit = id(submit(key(), new Command(instrument, "SELL", "1", "MARKET")));
        fill(exit, "49", "50");
        equalDecimal(
                "0",
                "SELECT posted FROM client_positions WHERE client_id="
                        + client
                        + " AND instrument_id="
                        + instrument);
        equalDecimal(
                "0",
                "SELECT reserved FROM client_cash_accounts WHERE client_id="
                        + client
                        + " AND currency='USD'");
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/finance/reconciliations")
                                .header("Authorization", "Bearer " + login("demo.finance")))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.matched")
                                .value(true));
    }

    @Test
    void freshSecondDemoClientCanFundZeroAccountWithoutAnOpeningGrant() throws Exception {
        sql("DROP SCHEMA public CASCADE;CREATE SCHEMA public");
        script("database/fresh/001_schema.sql");
        script("database/fresh/002_reference.sql");
        script("database/demo/role_workspaces.sql");
        script("database/migrations/020_user_trade_access.sql");
        int second =
                Integer.parseInt(
                        scalar(
                                "SELECT client_id FROM clients JOIN users USING(user_id) WHERE"
                                        + " username='demo.client2'"));
        int user =
                Integer.parseInt(scalar("SELECT user_id FROM users WHERE username='demo.client2'"));
        assertEquals(
                "1",
                scalar(
                        "SELECT count(*) FROM client_cash_accounts WHERE client_id="
                                + second
                                + " AND currency='USD'"));
        equalDecimal(
                "0",
                "SELECT posted FROM client_cash_accounts WHERE client_id="
                        + second
                        + " AND currency='USD'");
        assertTrue(banking.banks(second).isEmpty());
        var bank =
                banking.save(
                        second,
                        user,
                        null,
                        new com.neueda.leap.dto.BankingDtos.BankRequest(
                                "Test reference", "000000001", "00005678", true, null));
        assertEquals(
                "SETTLED",
                banking.move(
                                second,
                                user,
                                key(),
                                new com.neueda.leap.dto.BankingDtos.MovementRequest(
                                        bank.bankId(), "DEPOSIT", "100"))
                        .status());
        script("database/demo/role_workspaces.sql");
        equalDecimal(
                "100",
                "SELECT posted FROM client_cash_accounts WHERE client_id="
                        + second
                        + " AND currency='USD'");
        assertEquals(
                "0",
                scalar(
                        "SELECT count(*) FROM cash_journal WHERE client_id="
                                + second
                                + " AND setup_reference IS NOT NULL"));
    }

    @Test
    void demoOpeningAndBankAreCreatedOnceAndNeverRefundSpentCash() throws Exception {
        sql("DROP SCHEMA public CASCADE;CREATE SCHEMA public");
        script("database/schema/001_tadpoles_schema.sql");
        script("database/migrations/003_workspace_roles.sql");
        script("database/migrations/004_client_simulation.sql");
        script("database/demo/role_workspaces.sql");
        script("database/migrations/005_client_banking.sql");
        script("database/migrations/006_trading_cutover.sql");
        script("database/migrations/007_trading_openings.sql");
        script("database/migrations/008_trading_controls.sql");
        script("database/migrations/009_quote_cache_age.sql");
        script("database/migrations/010_quote_cache_twenty_minutes.sql");
        script("database/migrations/021_quote_stale_after_thirty_minutes.sql");
        script("database/migrations/011_operations_workspace.sql");
        script("database/migrations/012_managed_support.sql");
        script("database/migrations/013_compliance_workspace.sql");
        script("database/migrations/014_compliance_context_scope.sql");
        script("database/migrations/015_compliance_scope_compatibility.sql");
        script("database/migrations/016_support_workflow.sql");
        script("database/migrations/017_compliance_referrals.sql");
        script("database/migrations/018_advisor_workspace.sql");
        script("database/migrations/019_instrument_exchange.sql");
        script("database/migrations/020_user_trade_access.sql");
        demoSetup.initialize();
        equalDecimal(
                "10000",
                "SELECT posted FROM client_cash_accounts WHERE client_id="
                        + client
                        + " AND currency='USD'");
        assertEquals(1, banking.banks(client).size());
        var bank = banking.banks(client).get(0);
        banking.move(
                client,
                actor,
                key(),
                new com.neueda.leap.dto.BankingDtos.MovementRequest(
                        bank.bankId(), "WITHDRAW", "123.45"));
        demoSetup.initialize();
        equalDecimal(
                "9876.55",
                "SELECT posted FROM client_cash_accounts WHERE client_id="
                        + client
                        + " AND currency='USD'");
        assertEquals(1, banking.banks(client).size());
        reconciled();
    }

    @Test
    void financeSummaryUsesSettledMovementsBookedFillsAndUtcDateBoundaries() throws Exception {
        var bankId = banking.banks(client).get(0).bankId();
        assertEquals(
                "SETTLED",
                banking.move(
                                client,
                                actor,
                                key(),
                                new com.neueda.leap.dto.BankingDtos.MovementRequest(
                                        bankId, "DEPOSIT", "125.25"))
                        .status());

        int buyOrder = id(submit(key(), buy("1")));
        fill(buyOrder, "50", "51");
        int sellOrder =
                id(submit(key(), new Command(instrument, "SELL", "1", "MARKET")));
        fill(sellOrder, "52", "53");

        assertEquals(
                "SETTLED",
                banking.move(
                                client,
                                actor,
                                key(),
                                new com.neueda.leap.dto.BankingDtos.MovementRequest(
                                        bankId, "WITHDRAW", "100.00"))
                        .status());
        assertEquals(
                "SETTLED",
                banking.move(
                                client,
                                actor,
                                key(),
                                new com.neueda.leap.dto.BankingDtos.MovementRequest(
                                        bankId, "WITHDRAW", "10000.00"))
                        .status());
        assertEquals(
                "REJECTED",
                banking.move(
                                client,
                                actor,
                                key(),
                                new com.neueda.leap.dto.BankingDtos.MovementRequest(
                                        bankId, "WITHDRAW", "30.00"))
                        .status());

        String token = login("demo.finance");
        mvc.perform(
                        get("/api/finance/summary")
                                .param("from", "2026-10-14")
                                .param("to", "2026-10-14")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.settledDeposits").value("125.25"))
                .andExpect(jsonPath("$.totals.settledWithdrawals").value("10100.00"))
                .andExpect(jsonPath("$.totals.grossBuyConsideration").value("51.00"))
                .andExpect(jsonPath("$.totals.grossSellConsideration").value("52.00"))
                .andExpect(jsonPath("$.totals.bookedFillCount").value(2))
                .andExpect(jsonPath("$.totals.fees").doesNotExist())
                .andExpect(jsonPath("$.clientId").doesNotExist())
                .andExpect(jsonPath("$.dataAsOf").isString())
                .andExpect(jsonPath("$.generatedAt").value("2026-10-14T14:00:00Z"));

        mvc.perform(
                        get("/api/finance/summary")
                                .param("from", "2026-10-01")
                                .param("to", "2026-10-01")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.settledDeposits").value("0.00"))
                .andExpect(jsonPath("$.totals.settledWithdrawals").value("0.00"))
                .andExpect(jsonPath("$.totals.grossBuyConsideration").value("0.00"))
                .andExpect(jsonPath("$.totals.grossSellConsideration").value("0.00"))
                .andExpect(jsonPath("$.totals.bookedFillCount").value(0));
    }

    @Test
    void financeReconciliationPersistsRealMismatchReferences() throws Exception {
        String token = login("demo.finance");
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/finance/reconciliations")
                                .header("Authorization", "Bearer " + token))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.matched")
                                .value(true));
        sql(
                "UPDATE client_cash_accounts SET posted=posted+1 WHERE client_id="
                        + client
                        + " AND currency='USD'");
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/finance/reconciliations")
                                .header("Authorization", "Bearer " + token))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.matched")
                                .value(false))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.exceptions[0].kind")
                                .value("CASH_JOURNAL"));
        assertEquals("2", scalar("SELECT count(*) FROM reconciliation_runs"));
    }

    @Test
    void reconciliationFindsFilledOrderWithoutFillEvidence() throws Exception {
        int order = id(submit(key(), buy("1")));
        // Deliberate owner-level corruption of disposable data, bypassing normal booking.
        sql(
                "BEGIN; SET LOCAL session_replication_role=replica; UPDATE client_trades SET"
                        + " status='FILLED' WHERE trade_id="
                        + order
                        + "; COMMIT");
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/finance/reconciliations")
                                .header("Authorization", "Bearer " + login("demo.finance")))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.matched")
                                .value(false))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.exceptions[?(@.kind == 'ORDER_FILL')].reference")
                                .value(org.hamcrest.Matchers.hasItem("order:" + order)));
    }

    @Test
    void startupMapsProviderIdentityWithoutOverwritingAnOperationsHalt() throws Exception {
        sql("INSERT INTO instruments(instrument_name,ticker,currency,asset_class,security_type,exchange)"
                + " VALUES('Microsoft Corporation','MSFT','USD','EQUITY','STOCK','NASDAQ')");
        var q = new FauxnanceTradingQuotesTest().response();
        q.getData().setSymbol("MSFT");
        q.getMeta().setSymbol("MSFT");
        when(provider.getQuote("MSFT")).thenReturn(q);
        catalogSetup.initialize();
        assertEquals(
                "1",
                scalar(
                        "SELECT count(*) FROM instrument_trading_policies WHERE"
                                + " provider_symbol='MSFT' AND enabled"));
        sql(
                "UPDATE instrument_trading_policies SET enabled=false,revision=revision+1 WHERE"
                        + " provider_symbol='MSFT'");
        catalogSetup.initialize();
        assertEquals(
                "0",
                scalar(
                        "SELECT count(*) FROM instrument_trading_policies WHERE"
                                + " provider_symbol='MSFT' AND enabled"));
    }

    @Test
    void startupRetainsDistinctBondAndEquityFundClassification() throws Exception {
        sql("INSERT INTO instruments(instrument_name,ticker,currency,asset_class,security_type,exchange)"
                + " VALUES ('Vanguard S&P 500 ETF','VOO','USD','ETF','ETF','NYSE Arca'),"
                + " ('iShares Core U.S. Aggregate Bond ETF','AGG','USD','Fixed Income','ETF','NYSE Arca')");
        for (String symbol : java.util.List.of("VOO", "AGG")) {
            var q = new FauxnanceTradingQuotesTest().response();
            q.getData().setSymbol(symbol);
            q.getMeta().setSymbol(symbol);
            when(provider.getQuote(symbol)).thenReturn(q);
        }
        catalogSetup.initialize();
        assertEquals(
                "Fixed Income", scalar("SELECT asset_class FROM instruments WHERE ticker='AGG'"));
        assertEquals("ETF", scalar("SELECT security_type FROM instruments WHERE ticker='AGG'"));
        assertEquals("ETF", scalar("SELECT asset_class FROM instruments WHERE ticker='VOO'"));
    }

    @Test
    void readinessSeparatesPermissionsWorkerLagAndRecordedReconciliation() throws Exception {
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/operations/readiness")
                                .header("Authorization", "Bearer " + login("demo.operations")))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.referenceDataReady")
                                .value(true))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.runtimePermissions.operationalAccess")
                                .value(true))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.runtimePermissions.evidenceProtected")
                                .value(false))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.pendingJobs")
                                .doesNotExist())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.cashMovementProcessing")
                                .value("SYNCHRONOUS"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.reconciliation.status")
                                .value("SCOPED_VIEW_REQUIRED"));
    }

    int operationsUser(String username) throws Exception {
        return Integer.parseInt(
                scalar("SELECT user_id FROM users WHERE username='" + username + "'"));
    }

    void operationsGrant(String username, int scope, String permission) throws Exception {
        int staff = operationsUser(username);
        int approver = operationsUser("demo.finance");
        sql(
                "INSERT INTO"
                    + " operations_access_grants(grant_id,staff_user_id,permission,client_id,account_reference,valid_from,valid_until,approved_by,approval_reference)"
                    + " VALUES ('"
                        + UUID.randomUUID()
                        + "',"
                        + staff
                        + ",'"
                        + permission
                        + "',"
                        + scope
                        + ",'CASE-ACCOUNT-"
                        + scope
                        + "','"
                        + now.minusSeconds(60)
                        + "','"
                        + now.plusSeconds(3600)
                        + "',"
                        + approver
                        + ",'TEST-APPROVAL')");
    }

    String operationsContext(String token) throws Exception {
        String response =
                mvc.perform(
                                post("/api/operations/access-contexts")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType("application/json")
                                        .content(
                                                "{\"purposeCode\":\"ORDER_REVIEW\",\"referenceType\":\"EXTERNAL\",\"caseReference\":\"TEST-CASE-123\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(response).get("contextId").asText();
    }

    @Test
    void operationsReadsOnlyGrantedAccountsAndAuditsBeforeDisclosure() throws Exception {
        int order = id(submit(key(), buy("2")));
        fill(order, "49", "50");
        int other =
                Integer.parseInt(
                        scalar(
                                "SELECT client_id FROM clients JOIN users USING(user_id) WHERE"
                                        + " username='demo.client2'"));
        int otherActor = operationsUser("demo.client2");
        int hidden = id(service.submit(other, otherActor, key(), buy("1")));
        operationsGrant("demo.operations", client, "ORDER_READ");
        operationsGrant("demo.operations", client, "EVIDENCE_READ");
        String token = login("demo.operations"), context = operationsContext(token);
        mvc.perform(
                        get("/api/operations/orders")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Operations-Context", context))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].orderId").value(order))
                .andExpect(jsonPath("$.hasMore").value(false));
        String body =
                mvc.perform(
                                get("/api/operations/orders/" + order)
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Operations-Context", context))
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.evidenceStatus").value("COMPLETE_FOR_INTERNAL_BOOKING"))
                        .andExpect(jsonPath("$.sections.postings.items.length()").value(2))
                        .andExpect(
                                jsonPath(
                                                "$.sections.decisions.items[0].values.decisionContext.schemaVersion")
                                        .value("1"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertFalse(body.contains("000000001"));
        assertFalse(body.contains("idempotency_key"));
        assertFalse(body.contains("claim_token"));
        for (String suffix : List.of("", "/quotes", "/attempts", "/postings"))
            mvc.perform(
                            get("/api/operations/orders/" + hidden + suffix)
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Operations-Context", context))
                    .andExpect(status().isNotFound());
        assertEquals(
                "4",
                scalar("SELECT count(*) FROM operations_access_events WHERE outcome='DENIED'"));
        assertEquals(
                "2",
                scalar(
                        "SELECT count(*) FROM operations_access_events WHERE action IN"
                                + " ('SEARCH','INVESTIGATE')"));
        sql(
                "CREATE FUNCTION fail_operations_audit() RETURNS trigger LANGUAGE plpgsql AS $$"
                    + " BEGIN RAISE EXCEPTION 'test audit failure'; END $$; CREATE TRIGGER"
                    + " fail_audit BEFORE INSERT ON operations_access_events FOR EACH ROW EXECUTE"
                    + " FUNCTION fail_operations_audit()");
        mvc.perform(
                        get("/api/operations/orders/" + order)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Operations-Context", context))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void operationsContextsGrantsAndMixedRolesFailClosed() throws Exception {
        int order = id(submit(key(), buy("1")));
        operationsGrant("demo.operations", client, "EVIDENCE_READ");
        String token = login("demo.operations"), context = operationsContext(token);
        mvc.perform(
                        get("/api/operations/orders/" + order)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/operations/orders/" + order)).andExpect(status().isUnauthorized());
        for (String user :
                List.of("demo.client", "demo.analyst", "demo.compliance", "demo.finance"))
            mvc.perform(
                            get("/api/operations/orders/" + order)
                                    .header("Authorization", "Bearer " + login(user))
                                    .header("X-Operations-Context", context))
                    .andExpect(status().isForbidden());
        sql(
                "INSERT INTO user_roles SELECT "
                        + operationsUser("demo.analyst")
                        + ",role_id FROM roles WHERE role_name='TRADING_OPS'");
        operationsGrant("demo.analyst", client, "EVIDENCE_READ");
        mvc.perform(
                        get("/api/operations/orders/" + order)
                                .header("Authorization", "Bearer " + login("demo.analyst"))
                                .header("X-Operations-Context", context))
                .andExpect(status().isForbidden());
        now = now.plusSeconds(601);
        mvc.perform(
                        get("/api/operations/orders/" + order)
                                .header("Authorization", "Bearer " + login("demo.operations"))
                                .header("X-Operations-Context", context))
                .andExpect(status().isForbidden());
        token = login("demo.operations");
        context = operationsContext(token);
        sql(
                "UPDATE operations_access_grants SET revoked_at='"
                        + now
                        + "' WHERE staff_user_id="
                        + operationsUser("demo.operations"));
        mvc.perform(
                        get("/api/operations/orders/" + order)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Operations-Context", context))
                .andExpect(status().isForbidden());
        sql(
                "INSERT INTO user_roles SELECT "
                        + operationsUser("demo.operations")
                        + ",role_id FROM roles WHERE role_name='CLIENT'");
        mvc.perform(
                        get("/api/operations/readiness")
                                .header("Authorization", "Bearer " + login("demo.operations")))
                .andExpect(status().isForbidden());
    }

    @Test
    void operationsPaginationBindsFiltersAndScopeAndUsesEqualTimestampTieBreak() throws Exception {
        for (int n = 0; n < 3; n++) submit(key(), buy("1"));
        operationsGrant("demo.operations", client, "ORDER_READ");
        String token = login("demo.operations"), context = operationsContext(token);
        String first =
                mvc.perform(
                                get("/api/operations/orders")
                                        .param("pageSize", "1")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Operations-Context", context))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.hasMore").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String cursor = json.readTree(first).get("nextCursor").asText();
        String second =
                mvc.perform(
                                get("/api/operations/orders")
                                        .param("pageSize", "1")
                                        .param("cursor", cursor)
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Operations-Context", context))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertNotEquals(
                json.readTree(first).at("/items/0/orderId"),
                json.readTree(second).at("/items/0/orderId"));
        mvc.perform(
                        get("/api/operations/orders")
                                .param("pageSize", "1")
                                .param("cursor", cursor)
                                .param("side", "SELL")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Operations-Context", context))
                .andExpect(status().isBadRequest());
        operationsGrant("demo.operations", client, "ORDER_READ");
        mvc.perform(
                        get("/api/operations/orders")
                                .param("pageSize", "1")
                                .param("cursor", cursor)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Operations-Context", context))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/operations/orders")
                                .param("accountReference", "%_")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Operations-Context", context))
                .andExpect(status().isBadRequest());
    }

    @Test
    void operationsExportIsExactAuditedIdempotentAndRevocable() throws Exception {
        int order = id(submit(key(), buy("1")));
        fill(order, "49", "50");
        operationsGrant("demo.operations", client, "EVIDENCE_READ");
        operationsGrant("demo.operations", client, "EVIDENCE_EXPORT");
        String token = login("demo.operations"), context = operationsContext(token), key = key();
        String body = "{\"orderId\":" + order + "}";
        String receipt =
                mvc.perform(
                                post("/api/operations/evidence-exports")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Operations-Context", context)
                                        .header("Idempotency-Key", key)
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String exportId = json.readTree(receipt).get("exportId").asText();
        mvc.perform(
                        post("/api/operations/evidence-exports")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Operations-Context", context)
                                .header("Idempotency-Key", key)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportId").value(exportId));
        byte[] bytes =
                mvc.perform(
                                get("/api/operations/evidence-exports/" + exportId + "/download")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Operations-Context", context))
                        .andExpect(status().isOk())
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .header()
                                        .string("Cache-Control", "no-store"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertEquals(
                json.readTree(receipt).get("digest").asText(),
                com.neueda.leap.service.OperationsAccessService.digest(bytes));
        var files = new HashMap<String, byte[]>();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
                files.put(entry.getName(), zip.readAllBytes());
        }
        var manifest = json.readTree(files.get("manifest.json"));
        for (var file : manifest.get("files"))
            assertEquals(
                    file.get("sha256").asText(),
                    com.neueda.leap.service.OperationsAccessService.digest(
                            files.get(file.get("path").asText())));
        assertEquals("1", scalar("SELECT count(*) FROM operations_evidence_exports"));
        sql(
                "UPDATE operations_access_grants SET revoked_at='"
                        + now
                        + "' WHERE permission='EVIDENCE_EXPORT'");
        mvc.perform(
                        get("/api/operations/evidence-exports/" + exportId + "/download")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Operations-Context", context))
                .andExpect(status().isForbidden());
        assertEquals("1", scalar("SELECT count(*) FROM order_fills"));
    }

    @Test
    void operationsReconciliationRedactsOtherAccountsAndKeepsFinanceAuthority() throws Exception {
        int order = id(submit(key(), buy("1")));
        operationsGrant("demo.operations", client, "RECONCILIATION_READ");
        String token = login("demo.operations"), context = operationsContext(token);
        mvc.perform(
                        get("/api/operations/reconciliations/latest")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Operations-Context", context))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
        sql("DELETE FROM order_execution_jobs WHERE order_id=" + order);
        mvc.perform(
                        post("/api/finance/reconciliations")
                                .header("Authorization", "Bearer " + login("demo.finance")))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/operations/reconciliations/latest")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Operations-Context", context))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].values.orderId").value(order));
        mvc.perform(post("/api/finance/reconciliations").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/operations/readiness").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliation.exceptions").doesNotExist())
                .andExpect(jsonPath("$.historicalExceptions").doesNotExist());
        assertEquals("1", scalar("SELECT count(*) FROM reconciliation_runs"));
    }

    String supportBody(Integer order, String subject) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("category", "ORDER_QUESTION");
        body.put("subject", subject);
        body.put("description", "Please explain the retained order receipt.");
        if (order != null) body.put("orderId", order);
        return json.writeValueAsString(body);
    }

    String createSupport(String token, String request, String body) throws Exception {
        return mvc.perform(
                        post("/api/support/cases")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", request)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("reference").value(org.hamcrest.Matchers.startsWith("CASE-")))
                .andExpect(jsonPath("status").value("OPEN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void supportCreationRecoversOneOwnedReceiptAndRejectsConflictingReplay() throws Exception {
        int order = id(submit(key(), buy("1")));
        String token = login("demo.client"),
                request = key(),
                body = supportBody(order, "Receipt question");
        String first = createSupport(token, request, body);
        assertEquals(first, createSupport(token, request, body));
        var receipt = json.readTree(first);
        assertEquals(order, receipt.path("orderId").asInt());
        mvc.perform(
                        get("/api/support/cases/by-request/" + request)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("reference").value(receipt.path("reference").asText()));
        mvc.perform(
                        post("/api/support/cases")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", request)
                                .contentType("application/json")
                                .content(supportBody(order, "Different question")))
                .andExpect(status().isConflict());
        assertEquals("1", scalar("SELECT count(*) FROM support_cases"));
        assertEquals("1", scalar("SELECT count(*) FROM support_case_events"));
    }

    @Test
    void supportOwnershipIncludesNestedOrderRecoverySearchAndUnknownFields() throws Exception {
        int order = id(submit(key(), buy("1")));
        String token = login("demo.client"), other = login("demo.client2"), request = key();
        String reference =
                json.readTree(createSupport(token, request, supportBody(order, "Order question")))
                        .path("reference")
                        .asText();
        mvc.perform(
                        get("/api/support/cases/" + reference)
                                .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/support/cases/by-request/" + request)
                                .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/support/cases")
                                .param("search", reference)
                                .header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("total").value(0));
        mvc.perform(
                        post("/api/support/cases")
                                .header("Authorization", "Bearer " + other)
                                .header("Idempotency-Key", key())
                                .contentType("application/json")
                                .content(supportBody(order, "Foreign question")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/support/cases")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", key())
                                .contentType("application/json")
                                .content(
                                        supportBody(null, "Account question")
                                                .replace("{", "{\"clientId\":999,")))
                .andExpect(status().isBadRequest());
        assertTrue(
                json.readTree(createSupport(token, key(), supportBody(null, "Account question")))
                        .path("orderId")
                        .isNull());
    }

    @Test
    void supportParallelCreationIsAtomicAndDoesNotChangeFinancialFacts() throws Exception {
        int order = id(submit(key(), buy("1")));
        String before =
                scalar("SELECT row_to_json(t)::text FROM client_trades t WHERE trade_id=" + order);
        String token = login("demo.client"),
                request = key(),
                body = supportBody(order, "Pending question");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> createSupport(token, request, body));
            var b = executor.submit(() -> createSupport(token, request, body));
            assertEquals(a.get(), b.get());
        } finally {
            executor.shutdownNow();
        }
        assertEquals("1", scalar("SELECT count(*) FROM support_cases"));
        assertEquals(
                before,
                scalar("SELECT row_to_json(t)::text FROM client_trades t WHERE trade_id=" + order));
        reconciled();
    }

    @Test
    void supportStaffQueueScopesBeforeCountAndPagingAndRevocation() throws Exception {
        String owner = login("demo.client"),
                other = login("demo.client2"),
                staff = login("demo.operations");
        String reference =
                json.readTree(createSupport(owner, key(), supportBody(null, "Account review")))
                        .path("reference")
                        .asText();
        createSupport(other, key(), supportBody(null, "Another account review"));
        operationsGrant("demo.operations", client, "CASE_READ");
        mvc.perform(
                        get("/api/operations/cases")
                                .header("Authorization", "Bearer " + staff)
                                .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("total").value(1))
                .andExpect(jsonPath("items[0].reference").value(reference));
        mvc.perform(
                        get("/api/operations/cases/" + reference)
                                .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("events[0].authorRole").value("CLIENT"));
        sql(
                "UPDATE operations_access_grants SET revoked_at='"
                        + now
                        + "' WHERE permission='CASE_READ'");
        mvc.perform(
                        get("/api/operations/cases/" + reference)
                                .header("Authorization", "Bearer " + staff))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/operations/cases").header("Authorization", "Bearer " + owner))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportAuditFailureRollsBackCaseAndFirstEvent() throws Exception {
        String token = login("demo.client");
        sql(
                "CREATE FUNCTION reject_case_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
                    + " IF NEW.action='CASE_CREATED' THEN RAISE EXCEPTION 'fixture'; END IF; RETURN"
                    + " NEW; END $$; CREATE TRIGGER test_case_audit BEFORE INSERT ON"
                    + " operations_access_events FOR EACH ROW EXECUTE FUNCTION"
                    + " reject_case_audit()");
        mvc.perform(
                        post("/api/support/cases")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", key())
                                .contentType("application/json")
                                .content(supportBody(null, "Account question")))
                .andExpect(status().isServiceUnavailable());
        assertEquals("0", scalar("SELECT count(*) FROM support_cases"));
        assertEquals("0", scalar("SELECT count(*) FROM support_case_events"));
    }

    String supportWrite(
            String token, String path, String request, long version, String body, String state)
            throws Exception {
        var content = new LinkedHashMap<String, Object>();
        content.put("expectedVersion", version);
        content.put("body", body);
        if (state != null) content.put("status", state);
        return mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", request)
                                .contentType("application/json")
                                .content(json.writeValueAsString(content)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void supportPublicLifecyclePrivateNotesAndRetryAreIndependentOfFinancialState()
            throws Exception {
        int order = id(submit(key(), buy("1")));
        fill(order, "49", "50");
        String before =
                scalar("SELECT row_to_json(t)::text FROM client_trades t WHERE trade_id=" + order);
        String owner = login("demo.client"), staff = login("demo.operations");
        operationsGrant("demo.operations", client, "CASE_READ");
        operationsGrant("demo.operations", client, "CASE_UPDATE");
        String ref =
                json.readTree(createSupport(owner, key(), supportBody(order, "Execution question")))
                        .path("reference")
                        .asText();
        String owned = "/api/support/cases/" + ref, ops = "/api/operations/cases/" + ref;
        String visible =
                mvc.perform(get(owned).header("Authorization", "Bearer " + owner))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        supportWrite(staff, ops + "/notes", key(), 1, "Private investigation details", null);
        assertEquals(
                visible,
                mvc.perform(get(owned).header("Authorization", "Bearer " + owner))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        mvc.perform(get(ops + "/notes").header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("items[0].body").value("Private investigation details"));
        String request = key();
        String reply =
                supportWrite(
                        staff,
                        ops + "/replies",
                        request,
                        1,
                        "Please clarify the price you expected.",
                        null);
        assertEquals(
                json.readTree(reply),
                json.readTree(
                        supportWrite(
                                staff,
                                ops + "/replies",
                                request,
                                1,
                                "Please clarify the price you expected.",
                                null)));
        supportWrite(
                staff,
                ops + "/transitions",
                key(),
                2,
                "Please provide the time of your observation.",
                "AWAITING_CLIENT");
        supportWrite(
                owner,
                owned + "/replies",
                key(),
                3,
                "I was looking at the earlier indicative quote.",
                null);
        mvc.perform(get(owned).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("status").value("IN_REVIEW"))
                .andExpect(jsonPath("version").value(4));
        supportWrite(
                staff,
                ops + "/transitions",
                key(),
                4,
                "The receipt retains the execution quote used for booking.",
                "RESOLVED");
        supportWrite(
                owner,
                owned + "/reopen",
                key(),
                5,
                "Please explain the reservation release as well.",
                null);
        mvc.perform(get(owned).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("status").value("IN_REVIEW"))
                .andExpect(jsonPath("totalEvents").value(6));
        assertEquals(
                before,
                scalar("SELECT row_to_json(t)::text FROM client_trades t WHERE trade_id=" + order));
        assertEquals("6", scalar("SELECT count(*) FROM support_case_events"));
        reconciled();
    }

    @Test
    void supportWritesRejectStaleVersionsAndForeignActorsWithoutPublicChanges() throws Exception {
        String owner = login("demo.client"),
                other = login("demo.client2"),
                staff = login("demo.operations");
        operationsGrant("demo.operations", client, "CASE_READ");
        operationsGrant("demo.operations", client, "CASE_UPDATE");
        String ref =
                json.readTree(createSupport(owner, key(), supportBody(null, "Account question")))
                        .path("reference")
                        .asText();
        String path = "/api/support/cases/" + ref + "/replies";
        String body = "{\"expectedVersion\":1,\"body\":\"Additional public information.\"}";
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + other)
                                .header("Idempotency-Key", key())
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isNotFound());
        supportWrite(owner, path, key(), 1, "Additional public information.", null);
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + owner)
                                .header("Idempotency-Key", key())
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/api/operations/cases/" + ref + "/transitions")
                                .header("Authorization", "Bearer " + staff)
                                .header("Idempotency-Key", key())
                                .contentType("application/json")
                                .content(
                                        "{\"expectedVersion\":2,\"status\":\"RESOLVED\",\"body\":\"\"}"))
                .andExpect(status().isBadRequest());
        assertEquals("2", scalar("SELECT count(*) FROM support_case_events"));
    }

    @Test
    void supportUpdatesAreOwnedDurableExplicitlyReadAndIgnorePrivateNotes() throws Exception {
        String owner = login("demo.client"),
                other = login("demo.client2"),
                staff = login("demo.operations");
        operationsGrant("demo.operations", client, "CASE_READ");
        operationsGrant("demo.operations", client, "CASE_UPDATE");
        String ref =
                json.readTree(createSupport(owner, key(), supportBody(null, "Account question")))
                        .path("reference")
                        .asText();
        String path = "/api/support/updates";
        String first =
                mvc.perform(get(path).header("Authorization", "Bearer " + owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("unread").value(1))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long cursor = json.readTree(first).path("cursor").asLong();
        assertTrue(cursor > 0);
        supportWrite(
                staff,
                "/api/operations/cases/" + ref + "/notes",
                key(),
                1,
                "Private investigation content.",
                null);
        assertEquals(
                first,
                mvc.perform(get(path).header("Authorization", "Bearer " + owner))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        mvc.perform(get(path).header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("unread").value(0))
                .andExpect(jsonPath("items").isEmpty());
        mvc.perform(
                        post(path + "/read")
                                .header("Authorization", "Bearer " + other)
                                .contentType("application/json")
                                .content("{\"cursor\":" + cursor + "}"))
                .andExpect(status().isNotFound());
        for (int i = 0; i < 2; i++)
            mvc.perform(
                            post(path + "/read")
                                    .header("Authorization", "Bearer " + owner)
                                    .contentType("application/json")
                                    .content("{\"cursor\":" + cursor + "}"))
                    .andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("unread").value(0));
        supportWrite(
                staff,
                "/api/operations/cases/" + ref + "/replies",
                key(),
                1,
                "A new public response for the client.",
                null);
        mvc.perform(
                        get(path)
                                .param("cursor", Long.toString(cursor))
                                .header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("unread").value(1))
                .andExpect(jsonPath("items.length()").value(1));
    }

    @Test
    void supportManagedPurposeConfinesEvidenceAndRechecksCaseGrant() throws Exception {
        int order = id(submit(key(), buy("1"))), unrelated = id(submit(key(), buy("1")));
        String owner = login("demo.client"), staff = login("demo.operations");
        operationsGrant("demo.operations", client, "CASE_READ");
        operationsGrant("demo.operations", client, "ORDER_READ");
        operationsGrant("demo.operations", client, "EVIDENCE_READ");
        String ref =
                json.readTree(createSupport(owner, key(), supportBody(order, "Order question")))
                        .path("reference")
                        .asText();
        String context =
                json.readTree(
                                mvc.perform(
                                                post("/api/operations/access-contexts")
                                                        .header("Authorization", "Bearer " + staff)
                                                        .contentType("application/json")
                                                        .content(
                                                                "{\"purposeCode\":\"ORDER_REVIEW\",\"referenceType\":\"MANAGED\",\"caseReference\":\""
                                                                        + ref
                                                                        + "\"}"))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("contextId")
                        .asText();
        mvc.perform(
                        get("/api/operations/orders/" + order)
                                .header("Authorization", "Bearer " + staff)
                                .header("X-Operations-Context", context))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/operations/orders/" + unrelated)
                                .header("Authorization", "Bearer " + staff)
                                .header("X-Operations-Context", context))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/operations/access-contexts")
                                .header("Authorization", "Bearer " + staff)
                                .contentType("application/json")
                                .content(
                                        "{\"purposeCode\":\"ORDER_REVIEW\",\"referenceType\":\"EXTERNAL\",\"caseReference\":\""
                                                + ref
                                                + "\"}"))
                .andExpect(status().isBadRequest());
        sql(
                "UPDATE operations_access_grants SET revoked_at='"
                        + now
                        + "' WHERE permission='CASE_READ'");
        mvc.perform(
                        get("/api/operations/orders/" + order)
                                .header("Authorization", "Bearer " + staff)
                                .header("X-Operations-Context", context))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportClientEvidenceAndSideControlsUseSafeRetainedFacts() throws Exception {
        int order = id(submit(key(), buy("1")));
        String owner = login("demo.client");
        mvc.perform(get("/api/orders/" + order).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("evidenceCompleteness").value("PENDING"))
                .andExpect(jsonPath("quotes[0].stage").value("ADMISSION"))
                .andExpect(jsonPath("quotes[0].applicableSide").value("ASK"))
                .andExpect(jsonPath("quotes[0].price").value("50"));
        sql(
                "UPDATE instrument_trading_policies SET buy_allowed=false WHERE instrument_id="
                        + instrument);
        mvc.perform(
                        get("/api/workspace/client/trading-availability")
                                .param("instrumentId", Integer.toString(instrument))
                                .header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("buyAllowed").value(false))
                .andExpect(jsonPath("sellAllowed").value(true))
                .andExpect(jsonPath("code").value("SELL_ONLY"));
    }

    @Test
    void supportWriteReceiptsRecoverByActorAndParallelUpdatesCannotOverwrite() throws Exception {
        String owner = login("demo.client"), other = login("demo.client2");
        String ref =
                json.readTree(createSupport(owner, key(), supportBody(null, "Account question")))
                        .path("reference")
                        .asText();
        String path = "/api/support/cases/" + ref, request = key();
        supportWrite(
                owner, path + "/replies", request, 1, "Additional information for the case.", null);
        mvc.perform(get(path + "/requests/" + request).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("version").value(2));
        mvc.perform(get(path + "/requests/" + request).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        var executor = Executors.newFixedThreadPool(2);
        Callable<Integer> write =
                () ->
                        mvc.perform(
                                        post(path + "/replies")
                                                .header("Authorization", "Bearer " + owner)
                                                .header("Idempotency-Key", key())
                                                .contentType("application/json")
                                                .content(
                                                        "{\"expectedVersion\":2,\"body\":\"Concurrent"
                                                            + " new public information.\"}"))
                                .andReturn()
                                .getResponse()
                                .getStatus();
        try {
            var a = executor.submit(write);
            var b = executor.submit(write);
            assertEquals(Set.of(200, 409), Set.of(a.get(), b.get()));
        } finally {
            executor.shutdownNow();
        }
        assertEquals("3", scalar("SELECT count(*) FROM support_case_events"));
    }

    @Test
    void supportExportAssociationRechecksBothResourcesAndDoesNotPublishBytes() throws Exception {
        int order = id(submit(key(), buy("1")));
        fill(order, "49", "50");
        String owner = login("demo.client"), staff = login("demo.operations");
        for (String permission :
                List.of("CASE_READ", "CASE_UPDATE", "EVIDENCE_READ", "EVIDENCE_EXPORT"))
            operationsGrant("demo.operations", client, permission);
        String ref =
                json.readTree(createSupport(owner, key(), supportBody(order, "Execution question")))
                        .path("reference")
                        .asText();
        String context =
                json.readTree(
                                mvc.perform(
                                                post("/api/operations/access-contexts")
                                                        .header("Authorization", "Bearer " + staff)
                                                        .contentType("application/json")
                                                        .content(
                                                                "{\"purposeCode\":\"ORDER_REVIEW\",\"referenceType\":\"MANAGED\",\"caseReference\":\""
                                                                        + ref
                                                                        + "\"}"))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("contextId")
                        .asText();
        var export =
                json.readTree(
                        mvc.perform(
                                        post("/api/operations/evidence-exports")
                                                .header("Authorization", "Bearer " + staff)
                                                .header("X-Operations-Context", context)
                                                .header("Idempotency-Key", key())
                                                .contentType("application/json")
                                                .content("{\"orderId\":" + order + "}"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String path = "/api/operations/cases/" + ref + "/exports",
                body = "{\"exportId\":\"" + export.path("exportId").asText() + "\"}";
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + staff)
                                .header("X-Operations-Context", context)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("digest").value(export.path("digest").asText()));
        String publicCase =
                mvc.perform(
                                get("/api/support/cases/" + ref)
                                        .header("Authorization", "Bearer " + owner))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertFalse(publicCase.contains(export.path("exportId").asText()));
        sql(
                "UPDATE operations_access_grants SET revoked_at='"
                        + now
                        + "' WHERE permission='EVIDENCE_EXPORT'");
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + staff)
                                .header("X-Operations-Context", context)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportAvailabilityDoesNotOfferAHaltedSide() throws Exception {
        String owner = login("demo.client");
        sql(
                "UPDATE instrument_trading_policies SET buy_allowed=false WHERE instrument_id="
                        + instrument);
        mvc.perform(
                        get("/api/workspace/client/trading-availability")
                                .param("instrumentId", Integer.toString(instrument))
                                .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("buyAllowed").value(false))
                .andExpect(jsonPath("sellAllowed").value(true))
                .andExpect(jsonPath("code").value("SELL_ONLY"));
    }

    @Test
    void supportProjectionCannotAdvanceWithoutHistoryAndReadinessReportsSchema() throws Exception {
        String owner = login("demo.client");
        createSupport(owner, key(), supportBody(null, "Account question"));
        assertThrows(
                SQLException.class,
                () -> sql("UPDATE support_cases SET version=version+1,status='RESOLVED'"));
        mvc.perform(
                        get("/api/operations/readiness")
                                .header("Authorization", "Bearer " + login("demo.operations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("supportSchemaReady").value(true));
    }

    @Test
    void supportAvailabilityCannotHideDisabledInstrumentBehindMissingBank() throws Exception {
        String owner = login("demo.client");
        sql("UPDATE bank_references SET active=false,is_default=false WHERE client_id=" + client);
        sql(
                "UPDATE instrument_trading_policies SET enabled=false WHERE instrument_id="
                        + instrument);
        mvc.perform(
                        get("/api/workspace/client/trading-availability")
                                .param("instrumentId", Integer.toString(instrument))
                                .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("buyAllowed").value(false))
                .andExpect(jsonPath("sellAllowed").value(false));
    }

    @Test
    void supportFullLifecycleLeavesPendingFilledAndRejectedFinancialSnapshotsUnchanged()
            throws Exception {
        int rejected = id(submit(key(), new Command(instrument, "SELL", "1", "MARKET")));
        int pending = id(submit(key(), buy("1")));
        int filled = id(submit(key(), buy("1")));
        fill(filled, "49", "50");
        String owner = login("demo.client"), staff = login("demo.operations");
        operationsGrant("demo.operations", client, "CASE_READ");
        operationsGrant("demo.operations", client, "CASE_UPDATE");
        var tables =
                List.of(
                        "client_trades",
                        "client_cash_accounts",
                        "client_positions",
                        "cash_journal",
                        "position_journal",
                        "order_fills",
                        "order_reservations",
                        "order_execution_jobs",
                        "order_events",
                        "order_quote_observations",
                        "order_rule_decisions");
        var before = new LinkedHashMap<String, String>();
        for (String table : tables)
            before.put(
                    table,
                    scalar(
                            "SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),"
                                    + " '[]'::jsonb)::text FROM "
                                    + table
                                    + " t"));
        for (int order : new int[] {pending, filled, rejected}) {
            String ref =
                    json.readTree(createSupport(owner, key(), supportBody(order, "Receipt review")))
                            .path("reference")
                            .asText();
            String publicPath = "/api/support/cases/" + ref,
                    staffPath = "/api/operations/cases/" + ref;
            supportWrite(
                    staff,
                    staffPath + "/replies",
                    key(),
                    1,
                    "The retained receipt is under review.",
                    null);
            supportWrite(
                    staff,
                    staffPath + "/transitions",
                    key(),
                    2,
                    "The public explanation is now available.",
                    "RESOLVED");
            supportWrite(
                    owner,
                    publicPath + "/reopen",
                    key(),
                    3,
                    "Please clarify one more receipt detail.",
                    null);
            for (String table : tables)
                assertEquals(
                        before.get(table),
                        scalar(
                                "SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),"
                                        + " '[]'::jsonb)::text FROM "
                                        + table
                                        + " t"),
                        table);
        }
    }
}
