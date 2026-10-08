package com.neueda.leap.service.impl;

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
import com.neueda.leap.service.UserService;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserServiceImpl implements UserService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    private final UserMapper userMapper;
    private final AdvisorMapper advisorMapper;
    private final ClientMapper clientMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserMapper userMapper,
            AdvisorMapper advisorMapper,
            ClientMapper clientMapper,
            ClientProfileMapper clientProfileMapper
    ) {
        this.userMapper = userMapper;
        this.advisorMapper = advisorMapper;
        this.clientMapper = clientMapper;
        this.clientProfileMapper = clientProfileMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    @Transactional
    public CreateUserResponseDto registerUser(CreateUserRequestDto request) {
        // Validate input
        validateRegistrationRequest(request);

        AppUser newUser = createUserRecord(
                request.username(),
                request.password(),
                request.email(),
                request.displayName(),
                request.enabled()
        );
        Integer userId = newUser.getUserId();

        String roleType = request.roleType().toUpperCase();
        userMapper.assignRole(userId, roleType);

        Integer advisorId = null;
        if ("ADVISOR".equals(roleType)) {
            Advisor advisor = new Advisor();
            advisor.setAdvisorName(request.advisorName());
            advisor.setUserId(userId);
            advisorMapper.insertAdvisor(advisor);
            advisorId = advisor.getAdvisorId();
        }

        return new CreateUserResponseDto(userId, request.username(), request.displayName(), roleType, advisorId);
    }

    @Override
    @Transactional
    public ClientRegistrationResponseDto registerClient(ClientRegistrationRequestDto request) {
        validateClientRegistrationRequest(request);

        AppUser newUser = createUserRecord(
                request.username(),
                request.password(),
                request.email(),
                request.displayName(),
                true
        );
        userMapper.assignRole(newUser.getUserId(), "CLIENT");

        Client client = new Client();
        client.setClientName(request.clientName().trim());
        client.setUserId(newUser.getUserId());
        clientMapper.insertClient(client);

        createOrUpdatePlaceholderClientProfile(newUser.getUserId(), client.getClientId());

        return new ClientRegistrationResponseDto(
                newUser.getUserId(),
                newUser.getUsername(),
                newUser.getDisplayName(),
                "CLIENT",
                client.getClientId()
        );
    }

    @Override
    @Transactional
    public AppUser createClientAccount(SignupRequestDto request) {
        validateSignupRequest(request);

        String normalizedEmail = normalizeEmail(request.email());
        AppUser newUser = createUserRecord(
                normalizedEmail,
                request.password(),
                normalizedEmail,
                request.displayName(),
                true
        );
        userMapper.assignRole(newUser.getUserId(), "CLIENT");
        createOrUpdatePlaceholderClientProfile(newUser.getUserId(), null);
        return newUser;
    }

    private void validateRegistrationRequest(CreateUserRequestDto request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User registration request is required");
        }
        validateSharedUserRegistrationFields(request.username(), request.password(), request.email(), request.displayName());
        if (request.roleType() == null || request.roleType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role type is required");
        }

        String roleType = request.roleType().toUpperCase();
        if (!isValidRole(roleType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role type");
        }

        if ("ADVISOR".equals(roleType)) {
            if (request.advisorName() == null || request.advisorName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Advisor name is required for ADVISOR role");
            }
        }
    }

    private void validateClientRegistrationRequest(ClientRegistrationRequestDto request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client registration request is required");
        }
        validateSharedUserRegistrationFields(request.username(), request.password(), request.email(), request.displayName());
        if (request.clientName() == null || request.clientName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client name is required");
        }
    }

    private void validateSignupRequest(SignupRequestDto request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signup request is required");
        }
        validateSharedUserRegistrationFields(request.email(), request.password(), request.email(), request.displayName());
    }

    private void validateSharedUserRegistrationFields(
            String username,
            String password,
            String email,
            String displayName
    ) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (password == null || !STRONG_PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and include uppercase, lowercase, number, and special character"
            );
        }
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be a valid email address");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");
        }
    }

    private AppUser createUserRecord(
            String username,
            String password,
            String email,
            String displayName,
            Boolean enabled
    ) {
        String normalizedUsername = username.trim();
        String normalizedEmail = normalizeEmail(email);
        String normalizedDisplayName = displayName.trim();

        if (userMapper.findByUsername(normalizedUsername) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (userMapper.findByEmail(normalizedEmail) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        String passwordHash = passwordEncoder.encode(password);

        AppUser newUser = new AppUser();
        newUser.setUsername(normalizedUsername);
        newUser.setEmail(normalizedEmail);
        newUser.setPasswordHash(passwordHash);
        newUser.setDisplayName(normalizedDisplayName);
        newUser.setEnabled(enabled != null ? enabled : true);
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setUpdatedAt(LocalDateTime.now());
        userMapper.insertUser(newUser);
        return newUser;
    }

    private void createOrUpdatePlaceholderClientProfile(Integer userId, Integer clientId) {
        ClientProfile existingProfile = clientProfileMapper.findByUserId(userId);
        if (existingProfile == null) {
            ClientProfile profile = new ClientProfile();
            profile.setUserId(userId);
            profile.setClientId(clientId);
            profile.setPaperlessStatements(true);
            profile.setMarketingOptIn(false);
            profile.setOnboardingComplete(false);
            profile.setCreatedAt(LocalDateTime.now());
            profile.setUpdatedAt(LocalDateTime.now());
            clientProfileMapper.insertClientProfile(profile);
            return;
        }

        existingProfile.setClientId(clientId);
        existingProfile.setPaperlessStatements(existingProfile.getPaperlessStatements() == null ? true : existingProfile.getPaperlessStatements());
        existingProfile.setMarketingOptIn(Boolean.TRUE.equals(existingProfile.getMarketingOptIn()));
        existingProfile.setOnboardingComplete(Boolean.TRUE.equals(existingProfile.getOnboardingComplete()) ? true : false);
        existingProfile.setUpdatedAt(LocalDateTime.now());
        clientProfileMapper.updateClientProfile(existingProfile);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isValidRole(String role) {
        return role.matches("ADMIN|AUDITOR|ANALYST|ADVISOR|CLIENT|COMPLIANCE|SUPPORT|OPERATIONS|REPORTING|GUEST");
    }
}
