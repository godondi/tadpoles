package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.ClientProfile;
import com.neueda.leap.dto.ClientOnboardingResponseDto;
import com.neueda.leap.dto.CompleteClientOnboardingRequestDto;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.mapper.ClientProfileMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ClientOnboardingServiceImplTest {
    @Mock
    private ClientProfileMapper clientProfileMapper;
    @Mock
    private ClientMapper clientMapper;

    private ClientOnboardingServiceImpl clientOnboardingService;

    @BeforeEach
    void setUp() {
        clientOnboardingService = new ClientOnboardingServiceImpl(clientProfileMapper, clientMapper);
    }

    @Test
    void completeOnboardingCreatesClientAndStoresProfile() {
        AppUser currentUser = buildClientUser();
        CompleteClientOnboardingRequestDto request = buildRequest();

        when(clientMapper.getClientByUserId(14)).thenReturn(null);
        when(clientMapper.insertClient(any())).thenAnswer(invocation -> {
            Client client = invocation.getArgument(0, Client.class);
            client.setClientId(7);
            return 1;
        });
        when(clientProfileMapper.findByUserId(14)).thenReturn(null, storedProfile());

        ClientOnboardingResponseDto response = clientOnboardingService.completeOnboarding(currentUser, request);

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        ArgumentCaptor<ClientProfile> profileCaptor = ArgumentCaptor.forClass(ClientProfile.class);
        verify(clientMapper).insertClient(clientCaptor.capture());
        verify(clientProfileMapper).insertClientProfile(profileCaptor.capture());

        assertEquals(7, response.clientId());
        assertEquals("Client One Household", response.clientName());
        assertEquals(true, response.onboardingComplete());
        assertEquals(BigDecimal.ZERO, clientCaptor.getValue().getCashBalance());
        assertEquals(Integer.valueOf(7), profileCaptor.getValue().getClientId());
        assertEquals(new BigDecimal("250000.00"), profileCaptor.getValue().getNetWorth());
    }

    @Test
    void getCurrentProfileReturnsDefaultsWhenPlaceholderExists() {
        AppUser currentUser = buildClientUser();
        ClientProfile profile = new ClientProfile();
        profile.setUserId(14);
        profile.setOnboardingComplete(false);
        profile.setPaperlessStatements(true);
        profile.setMarketingOptIn(false);

        when(clientMapper.getClientByUserId(14)).thenReturn(null);
        when(clientProfileMapper.findByUserId(14)).thenReturn(profile);

        ClientOnboardingResponseDto response = clientOnboardingService.getCurrentProfile(currentUser);

        assertEquals(false, response.onboardingComplete());
        assertEquals(true, response.paperlessStatements());
    }

    @Test
    void completeOnboardingRejectsFutureDateOfBirth() {
        AppUser currentUser = buildClientUser();
        CompleteClientOnboardingRequestDto request = new CompleteClientOnboardingRequestDto(
                "Client One Household",
                "+1-555-222-1111",
                LocalDate.now().plusDays(1),
                "100 Main Street",
                null,
                "New York",
                "NY",
                "10001",
                "United States",
                "Employed",
                new BigDecimal("250000.00"),
                "Moderate",
                "Long-term growth",
                "Email",
                true,
                false
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> clientOnboardingService.completeOnboarding(currentUser, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Date of birth must be in the past", exception.getReason());
    }

    private AppUser buildClientUser() {
        AppUser user = new AppUser();
        user.setUserId(14);
        user.setUsername("client01@tadpoles.dev");
        user.setEmail("client01@tadpoles.dev");
        user.setDisplayName("Client One");
        user.setRoles(List.of("CLIENT"));
        return user;
    }

    private CompleteClientOnboardingRequestDto buildRequest() {
        return new CompleteClientOnboardingRequestDto(
                "Client One Household",
                "+1-555-222-1111",
                LocalDate.of(1992, 3, 15),
                "100 Main Street",
                "Unit 9",
                "New York",
                "NY",
                "10001",
                "United States",
                "Employed",
                new BigDecimal("250000.00"),
                "Moderate",
                "Long-term growth",
                "Email",
                true,
                false
        );
    }

    private ClientProfile storedProfile() {
        ClientProfile profile = new ClientProfile();
        profile.setClientProfileId(11);
        profile.setUserId(14);
        profile.setClientId(7);
        profile.setPhone("+1-555-222-1111");
        profile.setDateOfBirth(LocalDate.of(1992, 3, 15));
        profile.setAddressLine1("100 Main Street");
        profile.setAddressLine2("Unit 9");
        profile.setCity("New York");
        profile.setState("NY");
        profile.setPostalCode("10001");
        profile.setCountry("United States");
        profile.setEmploymentStatus("Employed");
        profile.setNetWorth(new BigDecimal("250000.00"));
        profile.setRiskTolerance("Moderate");
        profile.setInvestmentObjective("Long-term growth");
        profile.setPreferredContactMethod("Email");
        profile.setPaperlessStatements(true);
        profile.setMarketingOptIn(false);
        profile.setOnboardingComplete(true);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        return profile;
    }
}

