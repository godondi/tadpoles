package com.neueda.leap.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPostgresIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tadpoles")
            .withUsername("test")
            .withPassword("test");

    private static final String EXTERNAL_URL = System.getenv("ISOLATED_TEST_DB_URL");
    @BeforeAll
    static void startIsolatedDatabase() {
        if (EXTERNAL_URL == null) {
            POSTGRES.start();
        } else if (!EXTERNAL_URL.matches("jdbc:postgresql://[a-zA-Z0-9.-]+:5432/tadpoles_isolated_test")) {
            throw new IllegalArgumentException("External test URL must target dedicated tadpoles_isolated_test database");
        }
    }

    @BeforeEach
    void resetDatabase() throws SQLException, IOException {
        try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
        executeSqlScript(resolveSchemaPath());
    }

    protected Connection newConnection() throws SQLException {
        return DriverManager.getConnection(
                EXTERNAL_URL == null ? POSTGRES.getJdbcUrl() : EXTERNAL_URL,
                EXTERNAL_URL == null ? POSTGRES.getUsername() : "test",
                EXTERNAL_URL == null ? POSTGRES.getPassword() : "test"
        );
    }

    private static Path resolveSchemaPath() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("..", "database", "schema", "001_tadpoles_schema.sql"));
        candidates.add(Path.of("database", "schema", "001_tadpoles_schema.sql"));
        candidates.add(Path.of("..", "..", "database", "schema", "001_tadpoles_schema.sql"));

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalized)) {
                return normalized;
            }
        }

        throw new IllegalStateException("Unable to locate database/schema/001_tadpoles_schema.sql from test runtime.");
    }

    protected void executeSqlScript(Path sqlPath) throws IOException, SQLException {
        String sql = Files.readString(sqlPath);
        String[] statements = sql.split(";");

        try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
            for (String rawStatement : statements) {
                String trimmed = rawStatement.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }
}
