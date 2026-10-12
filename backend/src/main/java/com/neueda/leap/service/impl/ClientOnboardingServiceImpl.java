package com.neueda.leap.service.impl;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.ClientProfile;
import com.neueda.leap.dto.ClientOnboardingResponseDto;
import com.neueda.leap.dto.CompleteClientOnboardingRequestDto;
import com.neueda.leap.mapper.ClientMapper;
import com.neueda.leap.mapper.ClientProfileMapper;
import com.neueda.leap.service.ClientOnboardingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientOnboardingServiceImpl implements ClientOnboardingService {
    private final ClientProfileMapper clientProfileMapper;
    private final ClientMapper clientMapper;

    public ClientOnboardingServiceImpl(ClientProfileMapper clientProfileMapper, ClientMapper clientMapper) {
        this.clientProfileMapper = clientProfileMapper;
        this.clientMapper = clientMapper;
    }

    @Override
    public ClientOnboardingResponseDto getCurrentProfile(AppUser currentUser) {
        validateCurrentUser(currentUser);

        Client client = clientMapper.getClientByUserId(currentUser.getUserId());
        ClientProfile profile = clientProfileMapper.findByUserId(currentUser.getUserId());
        return toResponse(currentUser, client, profile);
    }

    @Override
    @Transactional
    public ClientOnboardingResponseDto completeOnboarding(AppUser currentUser, CompleteClientOnboardingRequestDto request) {
        validateCurrentUser(currentUser);
        validateRequest(request);

        Client client = clientMapper.getClientByUserId(currentUser.getUserId());
        if (client == null) {
            client = new Client();
            client.setUserId(currentUser.getUserId());
            client.setClientName(normalizeRequired(request.clientName(), "Client name is required"));
            client.setCashBalance(BigDecimal.ZERO);
            clientMapper.insertClient(client);
        } else {
            client.setClientName(normalizeRequired(request.clientName(), "Client name is required"));
            clientMapper.updateClient(client);
        }

        ClientProfile profile = clientProfileMapper.findByUserId(currentUser.getUserId());
        if (profile == null) {
            profile = new ClientProfile();
            profile.setUserId(currentUser.getUserId());
            profile.setCreatedAt(LocalDateTime.now());
        }

        profile.setClientId(client.getClientId());
        profile.setPhone(normalizeOptional(request.phone()));
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setAddressLine1(normalizeRequired(request.addressLine1(), "Address line 1 is required"));
        profile.setAddressLine2(normalizeOptional(request.addressLine2()));
        profile.setCity(normalizeRequired(request.city(), "City is required"));
        profile.setState(normalizeRequired(request.state(), "State is required"));
        profile.setPostalCode(normalizeRequired(request.postalCode(), "Postal code is required"));
        profile.setCountry(normalizeRequired(request.country(), "Country is required"));
        profile.setEmploymentStatus(normalizeRequired(request.employmentStatus(), "Employment status is required"));
        profile.setNetWorth(request.netWorth());
        profile.setRiskTolerance(normalizeRequired(request.riskTolerance(), "Risk tolerance is required"));
        profile.setInvestmentObjective(normalizeRequired(request.investmentObjective(), "Investment objective is required"));
        profile.setPreferredContactMethod(normalizeRequired(request.preferredContactMethod(), "Preferred contact method is required"));
        profile.setPaperlessStatements(resolvePaperlessStatements(request.paperlessStatements()));
        profile.setMarketingOptIn(Boolean.TRUE.equals(request.marketingOptIn()));
        profile.setOnboardingComplete(true);
        profile.setUpdatedAt(LocalDateTime.now());

        if (profile.getClientProfileId() == null) {
            clientProfileMapper.insertClientProfile(profile);
        } else {
            clientProfileMapper.updateClientProfile(profile);
        }

        ClientProfile storedProfile = clientProfileMapper.findByUserId(currentUser.getUserId());
        return toResponse(currentUser, client, storedProfile);
    }

    private void validateCurrentUser(AppUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
        if (currentUser.getRoles() == null || currentUser.getRoles().stream().noneMatch("CLIENT"::equalsIgnoreCase)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client role required");
        }
    }

    private void validateRequest(CompleteClientOnboardingRequestDto request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Onboarding request is required");
        }
        normalizeRequired(request.clientName(), "Client name is required");
        normalizeRequired(request.addressLine1(), "Address line 1 is required");
        normalizeRequired(request.city(), "City is required");
        normalizeRequired(request.state(), "State is required");
        normalizeRequired(request.postalCode(), "Postal code is required");
        normalizeRequired(request.country(), "Country is required");
        normalizeRequired(request.employmentStatus(), "Employment status is required");
        normalizeRequired(request.riskTolerance(), "Risk tolerance is required");
        normalizeRequired(request.investmentObjective(), "Investment objective is required");
        normalizeRequired(request.preferredContactMethod(), "Preferred contact method is required");

        if (request.dateOfBirth() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date of birth is required");
        }
        if (!request.dateOfBirth().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date of birth must be in the past");
        }
        if (request.netWorth() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Net worth is required");
        }
        if (request.netWorth().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Net worth must be zero or greater");
        }
    }

    private ClientOnboardingResponseDto toResponse(AppUser currentUser, Client client, ClientProfile profile) {
        return new ClientOnboardingResponseDto(
                currentUser.getUserId(),
                profile != null && profile.getClientId() != null ? profile.getClientId() : client == null ? null : client.getClientId(),
                currentUser.getEmail(),
                currentUser.getDisplayName(),
                client == null ? null : client.getClientName(),
                profile == null ? null : profile.getPhone(),
                profile == null ? null : profile.getDateOfBirth(),
                profile == null ? null : profile.getAddressLine1(),
                profile == null ? null : profile.getAddressLine2(),
                profile == null ? null : profile.getCity(),
                profile == null ? null : profile.getState(),
                profile == null ? null : profile.getPostalCode(),
                profile == null ? null : profile.getCountry(),
                profile == null ? null : profile.getEmploymentStatus(),
                profile == null ? null : profile.getNetWorth(),
                profile == null ? null : profile.getRiskTolerance(),
                profile == null ? null : profile.getInvestmentObjective(),
                profile == null ? null : profile.getPreferredContactMethod(),
                profile == null || profile.getPaperlessStatements() == null ? true : profile.getPaperlessStatements(),
                profile != null && Boolean.TRUE.equals(profile.getMarketingOptIn()),
                profile != null && Boolean.TRUE.equals(profile.getOnboardingComplete())
        );
    }

    private Boolean resolvePaperlessStatements(Boolean paperlessStatements) {
        return paperlessStatements == null ? Boolean.TRUE : paperlessStatements;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}

