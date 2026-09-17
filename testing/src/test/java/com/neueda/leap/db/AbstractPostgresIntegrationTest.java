package com.neueda.leap.db;

import org.junit.jupiter.api.BeforeEach;
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

@Testcontainers
public abstract class AbstractPostgresIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tadpoles")
            .withUsername("test")
            .withPassword("test");

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
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    private static Path resolveSchemaPath() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("..", "database", "schema", "tadpoles_schema.sql"));
        candidates.add(Path.of("database", "schema", "tadpoles_schema.sql"));
        candidates.add(Path.of("..", "..", "database", "schema", "tadpoles_schema.sql"));

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalized)) {
                return normalized;
            }
        }

        throw new IllegalStateException("Unable to locate database/schema/tadpoles_schema.sql from test runtime.");
    }

    private void executeSqlScript(Path sqlPath) throws IOException, SQLException {
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
