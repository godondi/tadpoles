package com.neueda.leap.service.impl;

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
import com.neueda.leap.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final AdvisorMapper advisorMapper;
    private final ClientMapper clientMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, AdvisorMapper advisorMapper, ClientMapper clientMapper) {
        this.userMapper = userMapper;
        this.advisorMapper = advisorMapper;
        this.clientMapper = clientMapper;
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

        return new ClientRegistrationResponseDto(
                newUser.getUserId(),
                newUser.getUsername(),
                newUser.getDisplayName(),
                "CLIENT",
                client.getClientId()
        );
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

    private void validateSharedUserRegistrationFields(
            String username,
            String password,
            String email,
            String displayName
    ) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
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
        String normalizedEmail = email.trim();
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

    private boolean isValidRole(String role) {
        return role.matches("ADMIN|AUDITOR|ANALYST|ADVISOR|CLIENT|COMPLIANCE|SUPPORT|OPERATIONS|REPORTING|GUEST");
    }
}
