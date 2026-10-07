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
import com.neueda.leap.dto.ClientRegistrationRequestDto;
import com.neueda.leap.dto.ClientRegistrationResponseDto;
import com.neueda.leap.dto.CreateUserRequestDto;
import com.neueda.leap.dto.CreateUserResponseDto;
import com.neueda.leap.mapper.AdvisorMapper;
import com.neueda.leap.mapper.ClientMapper;
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

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, advisorMapper, clientMapper);
    }

    @Test
    void registerClientCreatesUserRoleAndClientLink() {
        ClientRegistrationRequestDto request = new ClientRegistrationRequestDto(
                "client14",
                "client-password",
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

        assertEquals(14, response.userId());
        assertEquals(7, response.clientId());
        assertEquals("CLIENT", response.role());
        assertEquals("client14", userCaptor.getValue().getUsername());
        assertNotNull(userCaptor.getValue().getPasswordHash());
        assertTrue(userCaptor.getValue().getPasswordHash().startsWith("$2"));
        assertEquals(Integer.valueOf(14), clientCaptor.getValue().getUserId());
        assertEquals("Client Fourteen", clientCaptor.getValue().getClientName());
    }

    @Test
    void registerClientRejectsDuplicateEmail() {
        ClientRegistrationRequestDto request = new ClientRegistrationRequestDto(
                "client14",
                "client-password",
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
                "advisor-password",
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
}
