package com.neueda.leap.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaConstraintValidationTest extends AbstractPostgresIntegrationTest {

    @Test
    void advisorsConstraintsAreEnforced() throws SQLException {
        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO advisors (adviosr_name) VALUES (?)",
                (Object) null
        ));

        executeUpdate("INSERT INTO advisors (advisor_id, adviosr_name) VALUES (?, ?)", 1, "Alice Advisor");
        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO advisors (advisor_id, adviosr_name) VALUES (?, ?)",
                1, "Duplicate Advisor Id"
        ));
    }

    @Test
    void modelPortfoliosConstraintsAreEnforced() throws SQLException {
        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO model_portfolios (model_name) VALUES (?)",
                (Object) null
        ));

        executeUpdate("INSERT INTO model_portfolios (model_portfolio_id, model_name) VALUES (?, ?)", 1, "Growth");
        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO model_portfolios (model_portfolio_id, model_name) VALUES (?, ?)",
                1, "Conservative"
        ));
    }

    @Test
    void clientsConstraintsAreEnforced() throws SQLException {
        executeUpdate("INSERT INTO advisors (advisor_id, adviosr_name) VALUES (?, ?)", 1, "Alice Advisor");
        executeUpdate("INSERT INTO model_portfolios (model_portfolio_id, model_name) VALUES (?, ?)", 1, "Growth");

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO clients (client_name, advisor_id, model_portfolio_id) VALUES (?, ?, ?)",
                null, 1, 1
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO clients (client_name, advisor_id, model_portfolio_id) VALUES (?, ?, ?)",
                "Bad FK", 999, 1
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO clients (client_name, advisor_id, model_portfolio_id) VALUES (?, ?, ?)",
                "Bad Portfolio FK", 1, 999
        ));
    }

    @Test
    void instrumentsConstraintsAreEnforced() throws SQLException {
        executeUpdate(
                "INSERT INTO instruments (instrument_id, instrument_name, ticker, currency) VALUES (?, ?, ?, ?)",
                1, "Apple", "AAPL", "USD"
        );

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO instruments (instrument_name, ticker, currency) VALUES (?, ?, ?)",
                null, "MSFT", "USD"
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO instruments (instrument_name, ticker, currency) VALUES (?, ?, ?)",
                "Microsoft", null, "USD"
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO instruments (instrument_name, ticker, currency) VALUES (?, ?, ?)",
                "Vanguard", "VXUS", null
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO instruments (instrument_name, ticker, currency) VALUES (?, ?, ?)",
                "Apple", "AAP2", "USD"
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO instruments (instrument_name, ticker, currency) VALUES (?, ?, ?)",
                "Apple 2", "AAPL", "USD"
        ));
    }

    @Test
    void modelPortfolioHoldingsConstraintsAreEnforced() throws SQLException {
        executeUpdate("INSERT INTO model_portfolios (model_portfolio_id, model_name) VALUES (?, ?)", 1, "Growth");
        executeUpdate(
                "INSERT INTO instruments (instrument_id, instrument_name, ticker, currency) VALUES (?, ?, ?, ?)",
                1, "Apple", "AAPL", "USD"
        );

        executeUpdate(
                "INSERT INTO model_portfolio_holdings (model_portfolio_id, instrument_id, target_weight_pct) VALUES (?, ?, ?)",
                1, 1, 50.00
        );

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO model_portfolio_holdings (model_portfolio_id, instrument_id, target_weight_pct) VALUES (?, ?, ?)",
                1, 1, 30.00
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO model_portfolio_holdings (model_portfolio_id, instrument_id, target_weight_pct) VALUES (?, ?, ?)",
                999, 1, 40.00
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO model_portfolio_holdings (model_portfolio_id, instrument_id, target_weight_pct) VALUES (?, ?, ?)",
                1, 999, 40.00
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO model_portfolio_holdings (model_portfolio_id, instrument_id, target_weight_pct) VALUES (?, ?, ?)",
                1, 1, -0.01
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO model_portfolio_holdings (model_portfolio_id, instrument_id, target_weight_pct) VALUES (?, ?, ?)",
                1, 1, 100.01
        ));
    }

    @Test
    void clientSubscriptionsConstraintsAreEnforced() throws SQLException {
        executeUpdate("INSERT INTO advisors (advisor_id, adviosr_name) VALUES (?, ?)", 1, "Alice Advisor");
        executeUpdate("INSERT INTO model_portfolios (model_portfolio_id, model_name) VALUES (?, ?)", 1, "Growth");
        executeUpdate(
                "INSERT INTO clients (client_id, client_name, advisor_id, model_portfolio_id) VALUES (?, ?, ?, ?)",
                1, "Client A", 1, 1
        );

        executeUpdate(
                "INSERT INTO client_subscriptions (client_id, model_portfolio_id, subscribed_date) VALUES (?, ?, ?)",
                1, 1, LocalDate.of(2026, 1, 1)
        );

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_subscriptions (client_id, model_portfolio_id, subscribed_date) VALUES (?, ?, ?)",
                1, 1, LocalDate.of(2026, 1, 2)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_subscriptions (client_id, model_portfolio_id, subscribed_date) VALUES (?, ?, ?)",
                999, 1, LocalDate.of(2026, 1, 2)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_subscriptions (client_id, model_portfolio_id, subscribed_date) VALUES (?, ?, ?)",
                1, 999, LocalDate.of(2026, 1, 2)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_subscriptions (client_id, model_portfolio_id, subscribed_date) VALUES (?, ?, ?)",
                1, 1, null
        ));
    }

    @Test
    void clientHoldingsConstraintsAreEnforced() throws SQLException {
        executeUpdate("INSERT INTO advisors (advisor_id, adviosr_name) VALUES (?, ?)", 1, "Alice Advisor");
        executeUpdate("INSERT INTO model_portfolios (model_portfolio_id, model_name) VALUES (?, ?)", 1, "Growth");
        executeUpdate(
                "INSERT INTO clients (client_id, client_name, advisor_id, model_portfolio_id) VALUES (?, ?, ?, ?)",
                1, "Client A", 1, 1
        );
        executeUpdate(
                "INSERT INTO instruments (instrument_id, instrument_name, ticker, currency) VALUES (?, ?, ?, ?)",
                1, "Apple", "AAPL", "USD"
        );

        executeUpdate(
                "INSERT INTO client_holdings (client_id, instrument_id, quantity, as_of_date) VALUES (?, ?, ?, ?)",
                1, 1, 10, LocalDate.of(2026, 2, 1)
        );

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_holdings (client_id, instrument_id, quantity, as_of_date) VALUES (?, ?, ?, ?)",
                1, 1, 20, LocalDate.of(2026, 2, 2)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_holdings (client_id, instrument_id, quantity, as_of_date) VALUES (?, ?, ?, ?)",
                1, 1, 0, LocalDate.of(2026, 2, 2)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_holdings (client_id, instrument_id, quantity, as_of_date) VALUES (?, ?, ?, ?)",
                999, 1, 10, LocalDate.of(2026, 2, 2)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_holdings (client_id, instrument_id, quantity, as_of_date) VALUES (?, ?, ?, ?)",
                1, 999, 10, LocalDate.of(2026, 2, 2)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_holdings (client_id, instrument_id, quantity, as_of_date) VALUES (?, ?, ?, ?)",
                1, 1, 10, null
        ));
    }

    @Test
    void clientTradesConstraintsAreEnforced() throws SQLException {
        executeUpdate("INSERT INTO advisors (advisor_id, adviosr_name) VALUES (?, ?)", 1, "Alice Advisor");
        executeUpdate("INSERT INTO model_portfolios (model_portfolio_id, model_name) VALUES (?, ?)", 1, "Growth");
        executeUpdate(
                "INSERT INTO clients (client_id, client_name, advisor_id, model_portfolio_id) VALUES (?, ?, ?, ?)",
                1, "Client A", 1, 1
        );
        executeUpdate(
                "INSERT INTO instruments (instrument_id, instrument_name, ticker, currency) VALUES (?, ?, ?, ?)",
                1, "Apple", "AAPL", "USD"
        );

        executeUpdate(
                "INSERT INTO client_trades (client_id, instrument_id, trade_type, quantity, price, trade_date) VALUES (?, ?, ?, ?, ?, ?)",
                1, 1, "BUY", 10, 200.00, LocalDate.of(2026, 3, 1)
        );

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_trades (client_id, instrument_id, trade_type, quantity, price, trade_date) VALUES (?, ?, ?, ?, ?, ?)",
                1, 1, "HOLD", 10, 200.00, LocalDate.of(2026, 3, 1)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_trades (client_id, instrument_id, trade_type, quantity, price, trade_date) VALUES (?, ?, ?, ?, ?, ?)",
                1, 1, "SELL", 0, 200.00, LocalDate.of(2026, 3, 1)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_trades (client_id, instrument_id, trade_type, quantity, price, trade_date) VALUES (?, ?, ?, ?, ?, ?)",
                1, 1, "SELL", 10, 0.00, LocalDate.of(2026, 3, 1)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_trades (client_id, instrument_id, trade_type, quantity, price, trade_date) VALUES (?, ?, ?, ?, ?, ?)",
                999, 1, "SELL", 10, 1.00, LocalDate.of(2026, 3, 1)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_trades (client_id, instrument_id, trade_type, quantity, price, trade_date) VALUES (?, ?, ?, ?, ?, ?)",
                1, 999, "SELL", 10, 1.00, LocalDate.of(2026, 3, 1)
        ));

        assertThrows(SQLException.class, () -> executeUpdate(
                "INSERT INTO client_trades (client_id, instrument_id, trade_type, quantity, price, trade_date) VALUES (?, ?, ?, ?, ?, ?)",
                1, 1, "SELL", 10, 1.00, null
        ));
    }

    private void executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        }
    }
}
