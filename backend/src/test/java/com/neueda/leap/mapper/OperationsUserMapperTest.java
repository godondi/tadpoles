package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
class OperationsUserMapperTest {
    @Autowired private OperationsUserMapper mapper;
    @Autowired private TradingMapper trading;

    @Test
    @Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql", "classpath:mapper/operations_access_schema.sql"})
    void listsClientAccountsAlphabeticallyWithCashAndAccessState() {
        List<Map<String, Object>> clients = mapper.clients("");
        assertEquals(1, clients.size());
        Map<String, Object> client = clients.get(0);
        assertEquals("client01", field(client, "username"));
        assertEquals(7, ((Number) field(client, "client_id")).intValue());
        assertEquals(0, new BigDecimal(field(client, "balance").toString()).compareTo(new BigDecimal("880")));
        assertEquals(true, field(client, "revoked"));
        assertEquals(null, field(client, "revoked_until"));
    }

    @Test
    @Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql", "classpath:mapper/operations_access_schema.sql"})
    void filtersClientsByEmailOrName() {
        assertEquals(1, mapper.clients("alice investor").size());
        assertTrue(mapper.clients("missing").isEmpty());
    }

    @Test
    @Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql",
            "classpath:mapper/operations_access_schema.sql", "classpath:mapper/operations_pending_client.sql"})
    void listsRegisteredClientBeforeOnboardingWithoutTryingToParseMissingClientId() {
        List<Map<String, Object>> clients = mapper.clients("");
        assertEquals(2, clients.size());
        Map<String, Object> pending = clients.stream()
                .filter(row -> "pending.client".equals(field(row, "username")))
                .findFirst().orElseThrow();
        assertNull(field(pending, "client_id"));
        assertEquals("0", new BigDecimal(field(pending, "balance").toString()).toPlainString());
        assertEquals(false, field(pending, "revoked"));
    }

    @Test
    @Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql", "classpath:mapper/operations_access_schema.sql"})
    void tradingAdmissionDeniesIndefiniteRevocations() {
        assertEquals(false, trading.accountEnabled(7));
        assertEquals(true, trading.tradeAccessRevoked(7));
    }

    @Test
    @Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql", "classpath:mapper/operations_access_schema.sql", "classpath:mapper/operations_expired_access.sql"})
    void tradingAdmissionAllowsAnExpiredTimedRevocation() {
        assertEquals(true, trading.accountEnabled(7));
        assertEquals(false, trading.tradeAccessRevoked(7));
    }

    @Test
    @Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql", "classpath:mapper/operations_access_schema.sql", "classpath:mapper/operations_restored_access.sql"})
    void tradingAdmissionAllowsRestoredAccess() {
        assertEquals(true, trading.accountEnabled(7));
        assertEquals(false, trading.tradeAccessRevoked(7));
    }

    private Object field(Map<String, Object> row, String name) {
        return row.containsKey(name) ? row.get(name) : row.get(name.toUpperCase());
    }
}
