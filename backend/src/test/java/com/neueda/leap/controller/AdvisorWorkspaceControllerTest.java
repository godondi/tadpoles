package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.AdvisorWorkspaceDtos.*;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.mapper.AdvisorWorkspaceMapper;
import com.neueda.leap.mapper.AdvisorWorkspaceMapper.Scope;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.impl.AdvisorWorkspaceServiceImpl;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdvisorWorkspaceController.class, ClientAdvisoryController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AdvisorWorkspaceServiceImpl.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class AdvisorWorkspaceControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AppUserService users;
    @MockBean AdvisorWorkspaceMapper db;
    private final Scope scope = new Scope(11, 4, 7);
    private final Instant now = Instant.parse("2026-10-22T18:00:00Z");
    private final String base = "/api/workspace/advisor/clients/7";

    @BeforeEach
    void identities() {
    AppUser advisor = new AppUser();
    advisor.setUserId(11);
    // Do not trust even the linked advisor ID on the identity DTO; resolve it in SQL.
    advisor.setAdvisorId(999);
    when(users.getUserByUsername("advisor")).thenReturn(advisor);
    AppUser client = new AppUser();
    client.setUserId(21);
    client.setClientId(999);
    when(users.getUserByUsername("client")).thenReturn(client);
    when(db.advisorForUser(11)).thenReturn(4);
    }

    @Test
    void advisorClientsRouteExists() throws Exception {
    when(db.clients(new Scope(11, 4, 0), "", 20, 0)).thenReturn(List.of(
        new AdvisorClient(7, "Safe Name", "CLIENT-7", "ASSIGNED", now)));
    when(db.clientCount(new Scope(11, 4, 0), "")).thenReturn(1L);
    mvc.perform(get("/api/workspace/advisor/clients?advisorId=999")
            .with(jwtWithSubjectAndRoles("advisor", "ADVISOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].clientReference").value("CLIENT-7"))
        .andExpect(jsonPath("$.items[0].updatedAt").value("2026-10-22T18:00:00Z"))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.pageSize").value(20));
    verify(db).clients(new Scope(11, 4, 0), "", 20, 0);
    }

    @Test
    void anonymousAndWrongRoleAreRejectedAcrossAllAdvisorRoutes() throws Exception {
    for (String role : List.of("CLIENT", "ANALYST", "ADMIN", "FINANCE")) {
        var identity = jwtWithSubjectAndRoles("client", role);
        mvc.perform(get("/api/workspace/advisor/clients").with(identity)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/portfolio").with(identity)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/guidance").with(identity)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/recommendations").with(identity)).andExpect(status().isForbidden());
        mvc.perform(get("/api/workspace/advisor/instruments").with(identity)).andExpect(status().isForbidden());
        mvc.perform(put(base + "/guidance/42").with(identity).contentType("application/json")
            .content("{\"status\":\"HOLD\",\"expectedVersion\":null}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(base + "/recommendations").with(identity).contentType("application/json")
            .content("{\"instrumentId\":43,\"reasoning\":\"Review\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch(base + "/recommendations/9").with(identity).contentType("application/json")
            .content("{\"reasoning\":\"Review\",\"expectedVersion\":1}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete(base + "/recommendations/9").with(identity).contentType("application/json")
            .content("{\"expectedVersion\":1}"))
            .andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/workspace/advisor/clients")).andExpect(status().isUnauthorized());
    verifyNoInteractions(db);
    }

    @Test
    void validRoleWithoutPersistedAdvisorLinkIsForbidden() throws Exception {
    when(db.advisorForUser(11)).thenReturn(null);
    mvc.perform(get("/api/workspace/advisor/clients").with(jwtWithSubjectAndRoles("advisor", "ADVISOR")))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").exists());
    verify(db, never()).clients(any(), any(), anyInt(), anyInt());
    }

    @Test
    void unknownAndUnassignedClientsHaveSameNonDisclosingError() throws Exception {
    for (int id : List.of(88, 999)) {
        mvc.perform(get("/api/workspace/advisor/clients/" + id + "/portfolio")
                .with(jwtWithSubjectAndRoles("advisor", "ADVISOR")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("This advisory record is unavailable"));
    }
    }

    @Test
    void confirmedClearAndConflictUseExactWireShape() throws Exception {
    when(db.lockClient(scope)).thenReturn(7);
    when(db.held(scope, 42)).thenReturn(true);
    when(db.updateGuidance(scope, 42, "NONE", null, 3)).thenReturn(1);
    when(db.guidanceItem(scope, 42)).thenReturn(new HoldingGuidance(42, "NONE", null, "Advisor", 4, now));
    mvc.perform(put(base + "/guidance/42").with(jwtWithSubjectAndRoles("advisor", "ADVISOR"))
            .contentType("application/json")
            .content("{\"status\":\"NONE\",\"reasoning\":\" discarded \",\"expectedVersion\":3}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NONE"))
        .andExpect(jsonPath("$.reasoning").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.version").value(4)).andExpect(jsonPath("$.authorName").value("Advisor"));
    mvc.perform(put(base + "/guidance/42").with(jwtWithSubjectAndRoles("advisor", "ADVISOR"))
            .contentType("application/json").content("{\"status\":\"HOLD\",\"expectedVersion\":2}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    void recommendationCreatePatchAndJsonDeleteAreConfirmed() throws Exception {
    when(db.lockClient(scope)).thenReturn(7);
    when(db.approvedInstrument(scope, 43)).thenReturn(true);
    when(db.insertRecommendation(scope, 43, "Review")).thenReturn(9);
    Recommendation record = new Recommendation(9, 43, "NEW", "New Company", "Review", 1, now);
    when(db.recommendation(scope, 9)).thenReturn(record);
    mvc.perform(post(base + "/recommendations").with(jwtWithSubjectAndRoles("advisor", "ADVISOR"))
            .contentType("application/json").content("{\"instrumentId\":43,\"reasoning\":\" Review \"}"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.recommendationId").value(9))
        .andExpect(jsonPath("$.instrumentName").value("New Company"));
    when(db.updateRecommendation(scope, 9, "Review", 1)).thenReturn(1);
    mvc.perform(patch(base + "/recommendations/9").with(jwtWithSubjectAndRoles("advisor", "ADVISOR"))
            .contentType("application/json").content("{\"reasoning\":\" Review \",\"expectedVersion\":1}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.recommendationId").value(9));
    when(db.archiveRecommendation(scope, 9, 1)).thenReturn(1);
    mvc.perform(delete(base + "/recommendations/9").with(jwtWithSubjectAndRoles("advisor", "ADVISOR"))
            .contentType("application/json").content("{\"expectedVersion\":1}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.recommendationId").value(9))
        .andExpect(jsonPath("$.version").value(2));
    verify(db).archiveRecommendation(scope, 9, 1);
    }

    @Test
    void missingVersionsAndInvalidQueriesAreBadRequests() throws Exception {
    mvc.perform(delete(base + "/recommendations/9").with(jwtWithSubjectAndRoles("advisor", "ADVISOR"))
            .contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
    mvc.perform(patch(base + "/recommendations/9").with(jwtWithSubjectAndRoles("advisor", "ADVISOR"))
            .contentType("application/json").content("{\"reasoning\":\"Review\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/workspace/advisor/clients?page=2147483647&pageSize=100")
            .with(jwtWithSubjectAndRoles("advisor", "ADVISOR")))
        .andExpect(status().isBadRequest());
    verify(db, never()).lockClient(any());
    }

    @Test
    void fractionalOrStringIdsAndVersionsCannotBeCoercedIntoDifferentRecords() throws Exception {
    for (String instrument : List.of("43.5", "\"43\"", "true")) {
        mvc.perform(post(base + "/recommendations").with(jwtWithSubjectAndRoles("advisor", "ADVISOR"))
                .contentType("application/json")
                .content("{\"instrumentId\":" + instrument + ",\"reasoning\":\"Review\"}"))
            .andExpect(status().isBadRequest());
    }
    for (String version : List.of("1.5", "\"1\"", "true")) {
        mvc.perform(delete(base + "/recommendations/9").with(jwtWithSubjectAndRoles("advisor", "ADVISOR"))
                .contentType("application/json").content("{\"expectedVersion\":" + version + "}"))
            .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(db);
    }

    @Test
    void catalogUsesAdvisorRoleAndMetadataOnly() throws Exception {
    when(db.instruments(new Scope(11, 4, 0), "", 100, 0))
        .thenReturn(List.of(new Instrument(43, "NEW", "New Company", "USD", true)));
    mvc.perform(get("/api/workspace/advisor/instruments").with(jwtWithSubjectAndRoles("advisor", "ADVISOR")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].isActive").value(true))
        .andExpect(jsonPath("$.items[0].instrumentName").value("New Company"))
        .andExpect(jsonPath("$.items[0].price").doesNotExist());
    }

    @Test
    void clientReadsIgnoreSubmittedClientIdAndHaveNoVersions() throws Exception {
    when(db.clientForUser(21)).thenReturn(7);
    when(db.clientAdvisor(21)).thenReturn(new AssignedAdvisor(4, "Current Advisor"));
    when(db.clientGuidance(21)).thenReturn(List.of(new ClientGuidance(42, "HOLD", null, "Advisor", now)));
    when(db.clientRecommendations(21)).thenReturn(List.of(new ClientRecommendation(9, 43, "NEW", "New Company", "Review", now)));
    mvc.perform(get("/api/workspace/client/advisor?clientId=999").with(jwtWithSubjectAndRoles("client", "CLIENT")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.advisor.name").value("Current Advisor"))
        .andExpect(jsonPath("$.assignedAt").value(org.hamcrest.Matchers.nullValue()));
    mvc.perform(get("/api/workspace/client/advisory?clientId=999").with(jwtWithSubjectAndRoles("client", "CLIENT")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.guidance[0].version").doesNotExist())
        .andExpect(jsonPath("$.recommendations[0].version").doesNotExist())
        .andExpect(jsonPath("$.asOf").value("2026-10-22T18:00:00Z"))
        .andExpect(jsonPath("$.source").value("Committed advisory records"));
    verify(db).clientGuidance(21);
    verify(db, never()).clientGuidance(999);
    }

    @Test
    void clientReadBoundariesRequireClientRoleAndPersistedLink() throws Exception {
    for (String route : List.of("advisor", "advisory")) {
        mvc.perform(get("/api/workspace/client/" + route)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workspace/client/" + route).with(jwtWithSubjectAndRoles("advisor", "ADVISOR")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/workspace/client/" + route).with(jwtWithSubjectAndRoles("client", "CLIENT")))
            .andExpect(status().isForbidden());
    }
    verify(db, never()).clientGuidance(anyInt());
    }
}