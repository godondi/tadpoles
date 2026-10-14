package com.neueda.leap.controller;
import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.WorkspaceDtos.*;
import com.neueda.leap.service.*;
import com.neueda.leap.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static com.neueda.leap.support.TestSecurityUtils.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
@WebMvcTest(WorkspaceClientController.class)
@Import({SecurityConfig.class,GlobalExceptionHandler.class})
@TestPropertySource(properties="jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class WorkspaceClientControllerTest {
    @Autowired MockMvc mvc;
    @MockBean WorkspaceReadService reads;
    @MockBean AppUserService users;
    @Test void ownHistoryUsesPersistedIdentityNotSubmittedClientId() throws Exception {
        AppUser user=new AppUser(); user.setClientId(7);
        when(users.getUserByUsername("client")).thenReturn(user);
        when(reads.orders(7,0,20,null,null,null,null,null)).thenReturn(new Page<>(java.util.List.of(),0,20,0));
        mvc.perform(get("/api/orders?clientId=999").with(jwtWithSubjectAndRoles("client","CLIENT","FINANCE")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        verify(reads).orders(7,0,20,null,null,null,null,null);
    }
    @Test void submissionIsDisabledAndNeverCallsBooking() throws Exception {
        mvc.perform(post("/api/orders").with(jwtWithRoles("CLIENT")).contentType("application/json").content("{}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Trading is not enabled. No order has been accepted or recorded."));
        verifyNoInteractions(reads);
    }
    @Test void staffCannotReadClientWorkspaceAndAnonymousMustAuthenticate() throws Exception {
        for (String role: java.util.List.of("FINANCE","ANALYST","COMPLIANCE","TRADING_OPS","ADMIN"))
            mvc.perform(get("/api/orders").with(jwtWithRoles(role))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reads);
    }
}
