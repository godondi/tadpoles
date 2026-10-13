package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.Advisor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/schema.sql", "classpath:mapper/data.sql"})
class AdvisorMapperTest {
    @Autowired
    private AdvisorMapper advisorMapper;

    @Test
    void getAdvisorReturnsAdvisor() {
        Advisor advisor = advisorMapper.getAdvisor(3);

        assertNotNull(advisor);
        assertEquals("Advisor One", advisor.getAdvisorName());
        assertEquals(1, advisor.getUserId());
    }

    @Test
    void listAdvisorsReturnsRows() {
        List<Advisor> advisors = advisorMapper.listAdvisors();
        assertEquals(1, advisors.size());
    }

    @Test
    void insertAdvisorCreatesNewRowWithGeneratedId() {
        Advisor advisor = new Advisor();
        advisor.setAdvisorName("Advisor Two");

        int rows = advisorMapper.insertAdvisor(advisor);

        assertEquals(1, rows);
        assertNotNull(advisor.getAdvisorId());
        Advisor stored = advisorMapper.getAdvisor(advisor.getAdvisorId());
        assertEquals("Advisor Two", stored.getAdvisorName());
    }

    @Test
    void updateAdvisorUpdatesRequestedFields() {
        Advisor update = new Advisor();
        update.setAdvisorId(3);
        update.setAdvisorName("Advisor Updated");

        int rows = advisorMapper.updateAdvisor(update);

        assertEquals(1, rows);
        Advisor stored = advisorMapper.getAdvisor(3);
        assertEquals("Advisor Updated", stored.getAdvisorName());
    }
}
