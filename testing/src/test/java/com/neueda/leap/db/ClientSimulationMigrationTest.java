package com.neueda.leap.db;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ClientSimulationMigrationTest extends AbstractPostgresIntegrationTest {
    @Test
    void upgradePreservesLegacyAndDoesNotInventCurrencyOrAcceptance() throws Exception {
        try (var c = newConnection();
                var s = c.createStatement()) {
            s.execute(
                    "INSERT INTO users(username,password_hash,display_name)"
                        + " VALUES('legacy','hash','Legacy');INSERT INTO"
                        + " clients(client_name,cash_balance) VALUES('Legacy',123.45);INSERT INTO"
                        + " instruments(instrument_name,ticker,currency)"
                        + " VALUES('Legacy','LEG','GBP');INSERT INTO"
                        + " client_trades(client_id,instrument_id,trade_type,quantity,price,trade_date)"
                        + " VALUES(1,1,'BUY',2,3,'2020-01-01')");
        }
        executeSqlScript(Path.of("../database/migrations/004_client_simulation.sql"));
        try (var c = newConnection();
                var s = c.createStatement()) {
            var r =
                    s.executeQuery(
                            "SELECT record_kind,accepted_at,price,quantity FROM client_trades");
            r.next();
            assertEquals("LEGACY", r.getString(1));
            assertNull(r.getObject(2));
            assertEquals(0, r.getBigDecimal(3).compareTo(new java.math.BigDecimal("3")));
            assertEquals(0, r.getBigDecimal(4).compareTo(new java.math.BigDecimal("2")));
            r = s.executeQuery("SELECT count(*) FROM client_cash_accounts");
            r.next();
            assertEquals(0, r.getInt(1));
            r = s.executeQuery("SELECT financial_model,cash_balance FROM clients");
            r.next();
            assertEquals("LEGACY", r.getString(1));
            assertEquals("123.45", r.getBigDecimal(2).toPlainString());
        }
    }

    @Test
    void migratedAccountsRejectNegativeAvailability() throws Exception {
        executeSqlScript(Path.of("../database/migrations/004_client_simulation.sql"));
        try (var c = newConnection();
                var s = c.createStatement()) {
            s.execute("INSERT INTO clients(client_name) VALUES('test')");
            assertThrows(
                    SQLException.class,
                    () ->
                            s.execute(
                                    "INSERT INTO"
                                        + " client_cash_accounts(client_id,currency,posted,reserved)"
                                        + " VALUES(1,'USD',10,11)"));
            s.execute(
                    "INSERT INTO client_cash_accounts(client_id,currency,posted,reserved)"
                            + " VALUES(1,'USD',10,10)");
        }
    }

    @Test
    void optInSetupIsIdempotentAndDoesNotRefillSpentCash() throws Exception {
        executeSqlScript(Path.of("../database/migrations/003_workspace_roles.sql"));
        executeSqlScript(Path.of("../database/migrations/004_client_simulation.sql"));
        executeSqlScript(Path.of("../database/demo/role_workspaces.sql"));
        executeSqlScript(Path.of("../database/demo/simulation.sql"));
        try (var c = newConnection();
                var s = c.createStatement()) {
            s.execute("UPDATE client_cash_accounts SET posted=9000 WHERE currency='USD'");
        }
        executeSqlScript(Path.of("../database/demo/simulation.sql"));
        try (var c = newConnection();
                var s = c.createStatement()) {
            var r =
                    s.executeQuery(
                            "SELECT count(*),min(posted) FROM client_cash_accounts WHERE"
                                    + " currency='USD'");
            r.next();
            assertEquals(2, r.getInt(1));
            assertEquals(0, r.getBigDecimal(2).compareTo(new java.math.BigDecimal("9000")));
            r = s.executeQuery("SELECT count(*) FROM cash_journal");
            r.next();
            assertEquals(6, r.getInt(1));
        }
    }
}
