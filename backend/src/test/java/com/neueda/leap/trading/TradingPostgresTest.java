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
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "trading.admission-enabled=true",
            "trading.worker-enabled=false",
            "banking.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
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

    @Autowired
    org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired
    TradingService service;
    @Autowired
    TradingTransactions tx;
    @Autowired
    DemoAccountSetup demoSetup;
    @Autowired
    CatalogSetup catalogSetup;
    @Autowired
    TradingMapper db;
    @Autowired
    ObjectMapper json;
    @Autowired
    com.neueda.leap.service.BankingService banking;
    @Autowired
    PortfolioValuationService valuations;

    @MockBean
    Clock clock;
    @MockBean
    TradingQuotes quotes;
    @MockBean
    BookingProbe probe;
    @MockBean
    com.neueda.leap.service.FauxnanceQuoteService provider;
    @MockBean
    com.neueda.leap.service.FauxnanceDailyCandleService providerCandles;
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
        script("database/demo/role_workspaces.sql");
        script("database/migrations/005_client_banking.sql");
        script("database/migrations/006_trading_cutover.sql");
        script("database/migrations/007_trading_openings.sql");
        script("database/migrations/008_trading_controls.sql");
        script("database/migrations/009_quote_cache_age.sql");
        script("database/migrations/010_quote_cache_twenty_minutes.sql");
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
        fill(order, "50", "51");
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
                                .isForbidden());
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
                                .value(0))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.cashMovementProcessing")
                                .value("SYNCHRONOUS"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.reconciliation.runId")
                                .isEmpty());
    }
}
