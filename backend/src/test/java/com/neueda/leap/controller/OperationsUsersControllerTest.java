package com.neueda.leap.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.neueda.leap.dto.CreateUserRequestDto;
import com.neueda.leap.dto.CreateUserResponseDto;
import com.neueda.leap.mapper.OperationsUserMapper;
import com.neueda.leap.service.UserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OperationsUsersControllerTest {
    @Mock OperationsUserMapper users;
    @Mock UserService userService;
    OperationsUsersController controller;

    @BeforeEach void setUp() { controller = new OperationsUsersController(users, userService); }

    @Test void listsUsersUsingSearchText() {
        when(users.clients("alice")).thenReturn(List.of());
        assertTrue(controller.clients(" alice ").isEmpty());
        verify(users).clients("alice");
    }

    @Test void revokesIndefinitelyAndRestoresAccess() {
        when(users.revoke(eq(10), isNull(), eq("review"))).thenReturn(1);
        when(users.unrevoke(10)).thenReturn(1);
        assertEquals(true, controller.tradeAccess(10, new OperationsUsersController.AccessChange(true, null, null, "review"), null).get("updated"));
        controller.tradeAccess(10, new OperationsUsersController.AccessChange(false, null, null, null), null);
        verify(users).revoke(eq(10), isNull(), eq("review"));
        verify(users).unrevoke(10);
        verify(users).recordChange(10, "unknown", "REVOKE_INDEFINITE", null, "review");
        verify(users).recordChange(10, "unknown", "RESTORE", null, null);
    }

    @Test void calculatesTimedDurationsAndRejectsOverOneYear() {
        when(users.revoke(eq(10), any(Instant.class), isNull())).thenReturn(1);
        Instant before = Instant.now();
        controller.tradeAccess(10, new OperationsUsersController.AccessChange(true, 5, "DAYS", null), null);
        var until = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(users).revoke(eq(10), until.capture(), isNull());
        assertTrue(until.getValue().isAfter(before.plus(4, ChronoUnit.DAYS)));
        assertTrue(until.getValue().isBefore(before.plus(6, ChronoUnit.DAYS)));
        controller.tradeAccess(10, new OperationsUsersController.AccessChange(true, 1, "MONTHS", null), null);
        verify(users, times(2)).revoke(eq(10), until.capture(), isNull());
        assertTrue(until.getAllValues().get(2).isAfter(before.plus(27, ChronoUnit.DAYS)));
        assertThrows(ResponseStatusException.class, () -> controller.tradeAccess(10, new OperationsUsersController.AccessChange(true, 13, "MONTHS", null), null));
    }

    @Test void staffCreationAllowsRequestedRolesAndRejectsClientOrPrivilegedRoles() {
        var response = new CreateUserResponseDto(7, "analyst", "Analyst", "ANALYST", null);
        when(userService.registerUser(any(CreateUserRequestDto.class))).thenReturn(response);
        assertEquals(response, controller.createStaff(new CreateUserRequestDto("analyst", "StrongPass1!", "a@example.test", "Analyst", "ANALYST", null, true)));
        assertThrows(ResponseStatusException.class, () -> controller.createStaff(new CreateUserRequestDto("root", "StrongPass1!", "r@example.test", "Root", "ADMIN", null, true)));
    }

    @Test void staffCreationAllowsEachOperationsRole() {
        for (String role : List.of("ANALYST", "ADVISOR", "COMPLIANCE", "FINANCE")) {
            var request = new CreateUserRequestDto("staff_" + role.toLowerCase(), "StrongPass1!",
                    role.toLowerCase() + "@example.test", "Staff User", role,
                    role.equals("ADVISOR") ? "Advisor Name" : null, true);
            when(userService.registerUser(request))
                    .thenReturn(new CreateUserResponseDto(7, request.username(), request.displayName(), role, null));
            assertEquals(role, controller.createStaff(request).role());
        }
    }
}
