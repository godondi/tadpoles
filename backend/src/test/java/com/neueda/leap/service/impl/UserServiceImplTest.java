package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Advisor;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.ClientProfile;
import com.neueda.leap.dto.ClientRegistrationRequestDto;
import com.neueda.leap.dto.ClientRegistrationResponseDto;
import com.neueda.leap.dto.CreateUserRequestDto;
import com.neueda.leap.dto.CreateUserResponseDto;
import com.neueda.leap.dto.SignupRequestDto;
import com.neueda.leap.mapper.AdvisorMapper;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.mapper.ClientProfileMapper;
import com.neueda.leap.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private AdvisorMapper advisorMapper;
    @Mock
    private ClientMapper clientMapper;
    @Mock
    private ClientProfileMapper clientProfileMapper;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, advisorMapper, clientMapper, clientProfileMapper);
    }

    @Test
    void registerClientCreatesUserRoleAndClientLink() {
        ClientRegistrationRequestDto request = new ClientRegistrationRequestDto(
                "client14",
                "ClientPassword123!",
                "client14@tadpoles.dev",
                "Client Fourteen",
                "Client Fourteen"
        );
        when(userMapper.findByUsername("client14")).thenReturn(null);
        when(userMapper.findByEmail("client14@tadpoles.dev")).thenReturn(null);
        when(userMapper.insertUser(any())).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0, AppUser.class);
            user.setUserId(14);
            return 1;
        });
        when(clientMapper.insertClient(any())).thenAnswer(invocation -> {
            Client client = invocation.getArgument(0, Client.class);
            client.setClientId(7);
            return 1;
        });

        ClientRegistrationResponseDto response = userService.registerClient(request);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(userMapper).insertUser(userCaptor.capture());
        verify(userMapper).assignRole(14, "CLIENT");
        verify(clientMapper).insertClient(clientCaptor.capture());
        verify(clientProfileMapper).insertClientProfile(any(ClientProfile.class));

        assertEquals(14, response.userId());
        assertEquals(7, response.clientId());
        assertEquals("CLIENT", response.role());
        assertEquals("client14", userCaptor.getValue().getUsername());
        assertNotNull(userCaptor.getValue().getPasswordHash());
        assertTrue(userCaptor.getValue().getPasswordHash().startsWith("$2"));
        assertEquals(Integer.valueOf(14), clientCaptor.getValue().getUserId());
        assertEquals("Client Fourteen", clientCaptor.getValue().getClientName());
        assertEquals(java.math.BigDecimal.ZERO, clientCaptor.getValue().getCashBalance());
    }

    @Test
    void registerClientRejectsDuplicateEmail() {
        ClientRegistrationRequestDto request = new ClientRegistrationRequestDto(
                "client14",
                "ClientPassword123!",
                "client14@tadpoles.dev",
                "Client Fourteen",
                "Client Fourteen"
        );
        AppUser existingUser = new AppUser();
        existingUser.setUserId(8);
        when(userMapper.findByUsername("client14")).thenReturn(null);
        when(userMapper.findByEmail("client14@tadpoles.dev")).thenReturn(existingUser);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> userService.registerClient(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Email already exists", exception.getReason());
    }

    @Test
    void registerUserCreatesAdvisorWhenRequested() {
        CreateUserRequestDto request = new CreateUserRequestDto(
                "advisor02",
                "AdvisorPassword123!",
                "advisor02@tadpoles.dev",
                "Advisor Two",
                "ADVISOR",
                "Advisor Two",
                true
        );
        when(userMapper.findByUsername("advisor02")).thenReturn(null);
        when(userMapper.findByEmail("advisor02@tadpoles.dev")).thenReturn(null);
        when(userMapper.insertUser(any())).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0, AppUser.class);
            user.setUserId(22);
            return 1;
        });
        when(advisorMapper.insertAdvisor(any())).thenAnswer(invocation -> {
            Advisor advisor = invocation.getArgument(0, Advisor.class);
            advisor.setAdvisorId(5);
            return 1;
        });

        CreateUserResponseDto response = userService.registerUser(request);

        verify(userMapper).assignRole(22, "ADVISOR");
        assertEquals(22, response.userId());
        assertEquals("ADVISOR", response.role());
        assertEquals(5, response.advisorId());
    }

    @Test
    void createClientAccountUsesNormalizedEmailAsUsernameAndCreatesPlaceholderProfile() {
        SignupRequestDto request = new SignupRequestDto(
                "  Jane.Doe@Example.com ",
                "StrongPassword123!",
                "Jane Doe"
        );
        when(userMapper.findByUsername("jane.doe@example.com")).thenReturn(null);
        when(userMapper.findByEmail("jane.doe@example.com")).thenReturn(null);
        when(userMapper.insertUser(any())).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0, AppUser.class);
            user.setUserId(24);
            return 1;
        });

        AppUser response = userService.createClientAccount(request);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        ArgumentCaptor<ClientProfile> profileCaptor = ArgumentCaptor.forClass(ClientProfile.class);
        verify(userMapper).insertUser(userCaptor.capture());
        verify(userMapper).assignRole(24, "CLIENT");
        verify(clientProfileMapper).insertClientProfile(profileCaptor.capture());

        assertEquals(24, response.getUserId());
        assertEquals("jane.doe@example.com", userCaptor.getValue().getUsername());
        assertEquals("jane.doe@example.com", userCaptor.getValue().getEmail());
        assertEquals(Integer.valueOf(24), profileCaptor.getValue().getUserId());
        assertEquals(false, profileCaptor.getValue().getOnboardingComplete());
    }

    @Test
    void createClientAccountRejectsWeakPassword() {
        SignupRequestDto request = new SignupRequestDto(
                "jane@example.com",
                "weakpass",
                "Jane Doe"
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> userService.createClientAccount(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
                "Password must be at least 8 characters and include uppercase, lowercase, number, and special character",
                exception.getReason()
        );
    }
}
