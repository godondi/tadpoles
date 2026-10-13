package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.ClientProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql"})
class ClientProfileMapperTest {
    @Autowired
    private ClientProfileMapper clientProfileMapper;

    @Test
    void findByUserIdReturnsStoredProfile() {
        ClientProfile profile = clientProfileMapper.findByUserId(14);

        assertNotNull(profile);
        assertEquals(7, profile.getClientId());
        assertEquals("New York", profile.getCity());
        assertEquals(true, profile.getOnboardingComplete());
    }

    @Test
    void insertClientProfileCreatesNewRowWithGeneratedId() {
        ClientProfile profile = new ClientProfile();
        profile.setUserId(4);
        profile.setPhone("+1-555-444-0000");
        profile.setDateOfBirth(LocalDate.of(1990, 1, 1));
        profile.setAddressLine1("400 Advisor Lane");
        profile.setCity("Boston");
        profile.setState("MA");
        profile.setPostalCode("02108");
        profile.setCountry("United States");
        profile.setEmploymentStatus("Employed");
        profile.setNetWorth(new BigDecimal("100000.00"));
        profile.setRiskTolerance("Moderate");
        profile.setInvestmentObjective("Growth");
        profile.setPreferredContactMethod("Email");
        profile.setPaperlessStatements(true);
        profile.setMarketingOptIn(false);
        profile.setOnboardingComplete(false);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());

        int rows = clientProfileMapper.insertClientProfile(profile);

        assertEquals(1, rows);
        assertNotNull(profile.getClientProfileId());
        ClientProfile stored = clientProfileMapper.findByUserId(4);
        assertEquals("Boston", stored.getCity());
        assertEquals(false, stored.getOnboardingComplete());
    }

    @Test
    void updateClientProfilePersistsLatestValues() {
        ClientProfile profile = clientProfileMapper.findByUserId(14);
        profile.setCity("Brooklyn");
        profile.setMarketingOptIn(true);
        profile.setNetWorth(new BigDecimal("300000.00"));

        int rows = clientProfileMapper.updateClientProfile(profile);

        assertEquals(1, rows);
        ClientProfile stored = clientProfileMapper.findByUserId(14);
        assertEquals("Brooklyn", stored.getCity());
        assertEquals(true, stored.getMarketingOptIn());
        assertEquals(0, new BigDecimal("300000.00").compareTo(stored.getNetWorth()));
    }
}

