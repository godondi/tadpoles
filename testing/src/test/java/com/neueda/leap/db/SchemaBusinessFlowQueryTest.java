package com.neueda.leap.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaBusinessFlowQueryTest extends AbstractPostgresIntegrationTest {

    @Test
    void realisticDataProducesExpectedClientAdvisorPortfolioRows() throws SQLException {
        seedRealisticData();

        List<String> rows = new ArrayList<>();
        String sql = """
                SELECT c.client_name, a.adviosr_name, mp.model_name
                FROM clients c
                JOIN advisors a ON c.advisor_id = a.advisor_id
                JOIN model_portfolios mp ON c.model_portfolio_id = mp.model_portfolio_id
                ORDER BY c.client_name
                """;

        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
            }
        }

        assertEquals(
                List.of(
                        "Carol Client|Alice Advisor|Conservative",
                        "Dan Client|Bob Advisor|Growth"
                ),
                rows
        );
    }

    @Test
    void realisticDataProducesExpectedHoldingsAggregation() throws SQLException {
        seedRealisticData();

        List<String> rows = new ArrayList<>();
        String sql = """
                SELECT c.client_name, SUM(ch.quantity) AS total_units
                FROM client_holdings ch
                JOIN clients c ON ch.client_id = c.client_id
                GROUP BY c.client_name
                ORDER BY c.client_name
                """;

        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(rs.getString("client_name") + "|" + rs.getInt("total_units"));
            }
        }

        assertEquals(
                List.of(
                        "Carol Client|70",
                        "Dan Client|100"
                ),
                rows
        );
    }

    @Test
    void realisticDataProducesExpectedTradeValueByType() throws SQLException {
        seedRealisticData();

        List<String> rows = new ArrayList<>();
        String sql = """
                SELECT trade_type, ROUND(SUM(quantity * price), 2) AS gross_value
                FROM client_trades
                GROUP BY trade_type
                ORDER BY trade_type
                """;

        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(rs.getString("trade_type") + "|" + rs.getBigDecimal("gross_value"));
            }
        }

        assertEquals(
                List.of(
                        "BUY|6325.00",
                        "SELL|5125.00"
                ),
                rows
        );
    }

    private void seedRealisticData() throws SQLException {
        execute("INSERT INTO advisors (advisor_id, adviosr_name) VALUES (1, 'Alice Advisor'), (2, 'Bob Advisor')");
        execute("INSERT INTO model_portfolios (model_portfolio_id, model_name) VALUES (1, 'Conservative'), (2, 'Growth')");
        execute(
                "INSERT INTO clients (client_id, client_name, advisor_id, model_portfolio_id) VALUES " +
                        "(1, 'Carol Client', 1, 1), " +
                        "(2, 'Dan Client', 2, 2)"
        );
        execute(
                "INSERT INTO instruments (instrument_id, instrument_name, ticker, currency) VALUES " +
                        "(1, 'Apple Inc', 'AAPL', 'USD'), " +
                        "(2, 'Vanguard Bond ETF', 'BND', 'USD'), " +
                        "(3, 'Vanguard International ETF', 'VXUS', 'USD')"
        );
        execute(
                "INSERT INTO model_portfolio_holdings (model_portfolio_id, instrument_id, target_weight_pct) VALUES " +
                        "(1, 2, 60.00), (1, 3, 40.00), (2, 1, 50.00), (2, 3, 50.00)"
        );
        execute(
                "INSERT INTO client_subscriptions (client_id, model_portfolio_id, subscribed_date) VALUES " +
                        "(1, 1, DATE '2026-01-05'), (2, 2, DATE '2026-01-06')"
        );
        execute(
                "INSERT INTO client_holdings (client_id, instrument_id, quantity, as_of_date) VALUES " +
                        "(1, 2, 50, DATE '2026-04-01'), (1, 3, 20, DATE '2026-04-01'), " +
                        "(2, 1, 40, DATE '2026-04-01'), (2, 3, 60, DATE '2026-04-01')"
        );
        execute(
                "INSERT INTO client_trades (client_id, instrument_id, trade_type, quantity, price, trade_date) VALUES " +
                        "(1, 2, 'BUY', 25, 100.00, DATE '2026-03-10'), " +
                        "(1, 3, 'BUY', 15, 95.00, DATE '2026-03-11'), " +
                        "(2, 1, 'SELL', 10, 205.00, DATE '2026-03-12'), " +
                        "(2, 3, 'SELL', 30, 102.50, DATE '2026-03-13'), " +
                        "(2, 1, 'BUY', 12, 200.00, DATE '2026-03-14')"
        );
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
