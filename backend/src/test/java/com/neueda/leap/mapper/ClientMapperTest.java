package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.Client;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/schema.sql", "classpath:mapper/data.sql"})
class ClientMapperTest {
    @Autowired
    private ClientMapper clientMapper;

    @Test
    void getClientReturnsClientWithBalance() {
        Client client = clientMapper.getClient(7);

        assertNotNull(client);
        assertEquals("Alice Investor", client.getClientName());
        assertEquals(0, new BigDecimal("1200.50").compareTo(client.getCashBalance()));
    }

    @Test
    void listClientsReturnsRows() {
        List<Client> clients = clientMapper.listClients();
        assertEquals(1, clients.size());
    }

    @Test
    void insertClientCreatesNewRowWithGeneratedId() {
        Client client = new Client();
        client.setClientName("Bob Investor");
        client.setAdvisorId(3);
        client.setModelPortfolioId(5);
        client.setCreatedByUserId(1);
        client.setCashBalance(new BigDecimal("500.00"));

        int rows = clientMapper.insertClient(client);

        assertEquals(1, rows);
        assertNotNull(client.getClientId());
        Client stored = clientMapper.getClient(client.getClientId());
        assertEquals("Bob Investor", stored.getClientName());
    }

    @Test
    void updateClientUpdatesRequestedFields() {
        Client update = new Client();
        update.setClientId(7);
        update.setClientName("Alice Updated");
        update.setCashBalance(new BigDecimal("1400.00"));

        int rows = clientMapper.updateClient(update);

        assertEquals(1, rows);
        Client stored = clientMapper.getClient(7);
        assertEquals("Alice Updated", stored.getClientName());
        assertEquals(0, new BigDecimal("1400.00").compareTo(stored.getCashBalance()));
    }

    @Test
    void getClientBalanceReturnsValue() {
        BigDecimal balance = clientMapper.getClientBalance(7);
        assertEquals(0, new BigDecimal("1200.50").compareTo(balance));
    }
}

