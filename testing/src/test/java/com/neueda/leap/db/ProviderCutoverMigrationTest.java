package com.neueda.leap.db;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProviderCutoverMigrationTest extends AbstractPostgresIntegrationTest {
    @Test
    void cutoverPreservesUnknownBalancesWithoutCreatingSpendableCashOrLegacyWriters()
            throws Exception {
        try (var c = newConnection();
                var s = c.createStatement()) {
            s.execute(
                    "INSERT INTO clients(client_name,cash_balance) VALUES('Historical"
                            + " client',123.45)");
        }
        for (String file :
                new String[] {
                    "004_client_simulation.sql",
                    "005_client_banking.sql",
                    "006_trading_cutover.sql",
                    "007_trading_openings.sql"
                }) executeSqlScript(Path.of("../database/migrations/" + file));
        try (var c = newConnection();
                var s = c.createStatement()) {
            var r =
                    s.executeQuery(
                            "SELECT count(*) FROM information_schema.columns WHERE"
                                    + " table_name='clients' AND column_name='financial_model'");
            r.next();
            assertEquals(
                    0, r.getInt(1), "There must not be selectable legacy/simulation account modes");
            r = s.executeQuery("SELECT cash_balance FROM clients");
            r.next();
            assertEquals("123.45", r.getBigDecimal(1).toPlainString());
            r = s.executeQuery("SELECT posted,reserved,currency FROM client_cash_accounts");
            assertTrue(r.next());
            assertEquals(0, r.getBigDecimal(1).signum());
            assertEquals(0, r.getBigDecimal(2).signum());
            assertEquals("USD", r.getString(3));
            r =
                    s.executeQuery(
                            "SELECT count(*) FROM financial_migration_exceptions WHERE"
                                    + " kind='UNKNOWN_CASH_CURRENCY'");
            r.next();
            assertEquals(1, r.getInt(1));
            assertThrows(
                    java.sql.SQLException.class,
                    () -> s.execute("UPDATE clients SET cash_balance=500"));
            assertThrows(
                    java.sql.SQLException.class,
                    () ->
                            s.execute(
                                    "INSERT INTO clients(client_name,cash_balance)"
                                            + " VALUES('Bypass',500)"));
        }
    }
}
