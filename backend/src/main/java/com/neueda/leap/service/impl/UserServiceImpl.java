package com.neueda.leap.service.impl;

import com.neueda.leap.domain.Advisor;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.CreateUserRequestDto;
import com.neueda.leap.dto.CreateUserResponseDto;
import com.neueda.leap.mapper.AdvisorMapper;
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
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, AdvisorMapper advisorMapper) {
        this.userMapper = userMapper;
        this.advisorMapper = advisorMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    @Transactional
    public CreateUserResponseDto registerUser(CreateUserRequestDto request) {
        // Validate input
        validateRegistrationRequest(request);

        // Check if username already exists
        if (userMapper.findByUsername(request.username()) != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        // Hash password
        String passwordHash = passwordEncoder.encode(request.password());

        // Create AppUser
        AppUser newUser = new AppUser();
        newUser.setUsername(request.username());
        newUser.setEmail(request.email());
        newUser.setPasswordHash(passwordHash);
        newUser.setDisplayName(request.displayName());
        newUser.setEnabled(request.enabled() != null ? request.enabled() : true);
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setUpdatedAt(LocalDateTime.now());

        // Insert user
        userMapper.insertUser(newUser);
        Integer userId = newUser.getUserId();

        // Assign role
        String roleType = request.roleType().toUpperCase();
        userMapper.assignRole(userId, roleType);

        // If advisor, create advisor record
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

    private void validateRegistrationRequest(CreateUserRequestDto request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");
        }
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

    private boolean isValidRole(String role) {
        return role.matches("ADMIN|AUDITOR|ANALYST|ADVISOR|CLIENT|COMPLIANCE|SUPPORT|OPERATIONS|REPORTING|GUEST");
    }
}

