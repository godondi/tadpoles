package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.ClientSubscription;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql"})
class ClientSubscriptionMapperTest {
    @Autowired
    private ClientSubscriptionMapper clientSubscriptionMapper;

    @Test
    void listClientSubscriptionsReturnsRows() {
        List<ClientSubscription> subscriptions = clientSubscriptionMapper.listClientSubscriptions(7);

        assertEquals(1, subscriptions.size());
        assertEquals(9, subscriptions.get(0).getSubscriptionId());
    }

    @Test
    void getClientSubscriptionReturnsSubscription() {
        ClientSubscription subscription = clientSubscriptionMapper.getClientSubscription(7, 9);

        assertNotNull(subscription);
        assertEquals(5, subscription.getModelPortfolioId());
        assertEquals("ACTIVE", subscription.getStatus());
    }

    @Test
    void insertClientSubscriptionCreatesNewRowWithGeneratedId() {
        ClientSubscription subscription = new ClientSubscription();
        subscription.setClientId(7);
        subscription.setModelPortfolioId(6);
        subscription.setSubscribedDate(LocalDate.of(2026, 9, 25));
        subscription.setEndedDate(null);
        subscription.setStatus("ACTIVE");
        subscription.setApprovedByUserId(1);

        int rows = clientSubscriptionMapper.insertClientSubscription(subscription);

        assertEquals(1, rows);
        assertNotNull(subscription.getSubscriptionId());
        ClientSubscription stored = clientSubscriptionMapper.getClientSubscription(7, subscription.getSubscriptionId());
        assertEquals(6, stored.getModelPortfolioId());
        assertEquals("ACTIVE", stored.getStatus());
    }
}
