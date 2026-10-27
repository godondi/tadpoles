package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Advisor;
import com.neueda.leap.dto.CompleteClientOnboardingRequestDto;
import com.neueda.leap.dto.CreateUserRequestDto;
import com.neueda.leap.dto.SignupRequestDto;
import com.neueda.leap.service.impl.ClientOnboardingServiceImpl;
import com.neueda.leap.service.impl.UserServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/schema.sql", "classpath:mapper/data.sql"})
class AdvisorMapperTest {
    @Autowired
    private AdvisorMapper advisorMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ClientProfileMapper clientProfileMapper;
    @Autowired
    private ClientMapper clientMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void assignsClientToTheOnlyAdvisor() {
        assertEquals(3, advisorMapper.findLeastAssignedAdvisorId());
    }

    @Test
    void whenEveryAdvisorHasTheSameClientCountLowestIdWins() {
        Advisor second = addAdvisor(100, "Advisor Two");
        Advisor third = addAdvisor(101, "Advisor Three");

        // The fixture advisor (ID 3) starts with one client; give every other advisor one too.
        addClients(second.getAdvisorId(), 1);
        addClients(third.getAdvisorId(), 1);

        assertEquals(3, advisorMapper.findLeastAssignedAdvisorId());
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

    @Test
    void whenLoadsAreFourThreeAndTwoAssignsToAdvisorWithTwo() {
        Advisor fourth = addAdvisor(100, "Advisor Four");
        Advisor fifth = addAdvisor(101, "Advisor Five");

        addClients(3, 4);
        addClients(fourth.getAdvisorId(), 3);
        addClients(fifth.getAdvisorId(), 2);
        assertEquals(fifth.getAdvisorId(), advisorMapper.findLeastAssignedAdvisorId());
    }

    @Test
    void newlyCreatedStaffAdvisorWinsTieAgainstDemoAdvisor() {
        jdbcTemplate.update(
                "INSERT INTO users (user_id, username, email, password_hash, display_name, enabled,"
                        + " created_at, updated_at) VALUES (8, 'demo.advisor',"
                        + " 'demo.advisor@example.test', 'fixture', 'Demo Advisor', TRUE,"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (8, 1)");
        jdbcTemplate.update(
                "INSERT INTO users (user_id, username, email, password_hash, display_name, enabled,"
                        + " created_at, updated_at) VALUES (9, 'new.advisor',"
                        + " 'new.advisor@example.test', 'fixture', 'New Advisor', TRUE,"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (9, 1)");

        Advisor demo = new Advisor();
        demo.setAdvisorName("Demo Advisor");
        demo.setUserId(8);
        advisorMapper.insertAdvisor(demo);

        Advisor createdByOperations = new Advisor();
        createdByOperations.setAdvisorName("New Advisor");
        createdByOperations.setUserId(9);
        advisorMapper.insertAdvisor(createdByOperations);

        assertEquals(createdByOperations.getAdvisorId(), advisorMapper.findLeastAssignedAdvisorId());
    }

    @Test
    void operationsCreatedAdvisorReceivesNewlyOnboardedSignupClient() {
        jdbcTemplate.update(
                "INSERT INTO users (user_id, username, email, password_hash, display_name, enabled,"
                        + " created_at, updated_at) VALUES (8, 'demo.advisor',"
                        + " 'demo.advisor@example.test', 'fixture', 'Demo Advisor', TRUE,"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (8, 1)");
        Advisor demo = new Advisor();
        demo.setAdvisorName("Demo Advisor");
        demo.setUserId(8);
        advisorMapper.insertAdvisor(demo);

        var accounts = new UserServiceImpl(
                userMapper, advisorMapper, clientMapper, clientProfileMapper);
        var createdAdvisor = accounts.registerUser(new CreateUserRequestDto(
                "staff.advisor", "StrongPassword123!", "staff.advisor@example.test",
                "Staff Advisor", "ADVISOR", "Staff Advisor", true));
        var registered = accounts.createClientAccount(new SignupRequestDto(
                "new.client@example.test", "StrongPassword123!", "New Client"));
        registered.setRoles(List.of("CLIENT"));

        var assignmentMapper = mock(AdvisorMapper.class);
        when(assignmentMapper.findLeastAssignedAdvisorId())
                .thenReturn(advisorMapper.findLeastAssignedAdvisorId());
        var onboarding = new ClientOnboardingServiceImpl(
                clientProfileMapper, clientMapper, assignmentMapper);
        onboarding.completeOnboarding(registered, new CompleteClientOnboardingRequestDto(
                "New Client", "2125550100", LocalDate.of(1990, 1, 1), "1 Main Street", null,
                "New York", "NY", "10001", "United States", "Employed",
                new BigDecimal("50000"), "Moderate", "Growth", "Email", false, false));

        assertEquals(createdAdvisor.advisorId(),
                clientMapper.getClientByUserId(registered.getUserId()).getAdvisorId());
    }

    @Test
    void whenLoadsAreFourThreeAndThreeChoosesLowerIdAmongAdvisorsWithThree() {
        Advisor fourth = addAdvisor(100, "Advisor Four");
        Advisor fifth = addAdvisor(101, "Advisor Five");
        addClients(3, 4);
        addClients(fourth.getAdvisorId(), 3);
        addClients(fifth.getAdvisorId(), 3);

        assertEquals(fourth.getAdvisorId(), advisorMapper.findLeastAssignedAdvisorId());
    }

    private Advisor addAdvisor(Integer advisorId, String name) {
        int userId = advisorId + 1000;
        jdbcTemplate.update(
                "INSERT INTO users (user_id, username, email, password_hash, display_name, enabled,"
                        + " created_at, updated_at) VALUES (?, ?, ?, 'fixture', ?, TRUE,"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId,
                "advisor." + advisorId,
                "advisor." + advisorId + "@example.test",
                name);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, 1)", userId);
        jdbcTemplate.update(
                "INSERT INTO advisors (advisor_id, advisor_name, user_id) VALUES (?, ?, ?)",
                advisorId,
                name,
                userId);
        Advisor advisor = new Advisor();
        advisor.setAdvisorId(advisorId);
        advisor.setAdvisorName(name);
        advisor.setUserId(userId);
        return advisor;
    }

    private void addClients(Integer advisorId, int count) {
        for (int i = 0; i < count; i++) {
            Integer clientId = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(client_id), 10000) + 1 FROM clients", Integer.class);
            jdbcTemplate.update(
                    "INSERT INTO clients (client_id, client_name, advisor_id, cash_balance) VALUES (?, ?, ?, ?)",
                    clientId,
                    "Assigned " + advisorId + " " + i,
                    advisorId,
                    BigDecimal.ZERO);
        }
    }
}
