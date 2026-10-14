package com.neueda.leap.db;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkspaceMigrationTest extends AbstractPostgresIntegrationTest {
    @Test void upgradeIsRepeatableAndPreservesLegacyAssignments() throws Exception {
        try (var connection=newConnection();var statement=connection.createStatement()) {
            statement.execute("INSERT INTO roles(role_name) VALUES ('OPERATIONS')");
            statement.execute("INSERT INTO users(username,password_hash,display_name) VALUES ('legacy','test','Legacy')");
            statement.execute("INSERT INTO user_roles SELECT user_id,role_id FROM users CROSS JOIN roles");
        }
        Path migration=Path.of("../database/migrations/003_workspace_roles.sql");
        executeSqlScript(migration); executeSqlScript(migration);
        try(var connection=newConnection();var statement=connection.createStatement()) {
            var rows=statement.executeQuery("SELECT COUNT(*) FROM roles WHERE role_name IN ('TRADING_OPS','FINANCE')"); rows.next();assertEquals(2,rows.getInt(1));
            rows=statement.executeQuery("SELECT role_name FROM user_roles JOIN roles USING(role_id)");rows.next();assertEquals("OPERATIONS",rows.getString(1));assertFalse(rows.next());
            rows=statement.executeQuery("SELECT COUNT(*) FROM pg_indexes WHERE indexname IN ('client_trades_client_date_id_idx','client_holdings_current_idx')");rows.next();assertEquals(2,rows.getInt(1));
        }
    }
    @Test void cleanInitializationAndMigrationRemainEquivalent() throws Exception {
        assertEquals(java.nio.file.Files.readString(Path.of("../database/schema/003_workspace_roles.sql")),java.nio.file.Files.readString(Path.of("../database/migrations/003_workspace_roles.sql")));
        executeSqlScript(Path.of("../database/schema/003_workspace_roles.sql"));
    }
}
