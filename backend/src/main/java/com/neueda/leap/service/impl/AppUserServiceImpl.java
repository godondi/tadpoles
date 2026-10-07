package com.neueda.leap.service.impl;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.SetAppUserRolesRequestDto;
import com.neueda.leap.dto.UpdateAppUserRequestDto;
import com.neueda.leap.exception.AppUserNotFoundException;
import com.neueda.leap.mapper.AppUserMapper;
import com.neueda.leap.service.AppUserService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AppUserServiceImpl implements AppUserService {
    private final AppUserMapper appUserMapper;

    public AppUserServiceImpl(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Override
    public AppUser getUser(Integer id) {
        validateId(id);

        AppUser user = appUserMapper.getUser(id);
        if (user == null) {
            throw new AppUserNotFoundException(id);
        }

        return enrichUser(user);
    }

    @Override
    public AppUser getUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        String normalizedUsername = username.trim();
        AppUser user = appUserMapper.getUserByUsername(normalizedUsername);
        if (user == null) {
            throw new AppUserNotFoundException(normalizedUsername);
        }

        return enrichUser(user);
    }

    @Override
    public List<AppUser> listUsers() {
        return appUserMapper.listUsers().stream().map(this::enrichUser).toList();
    }

    @Override
    public AppUser updateUser(Integer id, UpdateAppUserRequestDto request) {
        validateId(id);
        if (request == null) {
            throw new IllegalArgumentException("User update request is required.");
        }
        if (request.displayName() != null && request.displayName().isBlank()) {
            throw new IllegalArgumentException("Display name cannot be blank.");
        }
        if (request.email() != null && request.email().isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank.");
        }
        if (request.displayName() == null && request.email() == null && request.enabled() == null) {
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }

        AppUser update = new AppUser();
        update.setUserId(id);
        update.setDisplayName(request.displayName() == null ? null : request.displayName().trim());
        update.setEmail(request.email() == null ? null : request.email().trim());
        update.setEnabled(request.enabled());

        int rows = appUserMapper.updateUser(update);
        if (rows == 0) {
            throw new AppUserNotFoundException(id);
        }

        return getUser(id);
    }

    @Override
    public AppUser setUserRoles(Integer id, SetAppUserRolesRequestDto request) {
        validateId(id);
        if (request == null) {
            throw new IllegalArgumentException("User roles request is required.");
        }

        List<String> normalizedRoles = normalizeRoles(request.roles());
        getUser(id);

        Set<String> existingRoles = Set.copyOf(appUserMapper.listExistingRoleNames());
        List<String> unknownRoles = normalizedRoles.stream()
                .filter(role -> !existingRoles.contains(role))
                .toList();
        if (!unknownRoles.isEmpty()) {
            throw new IllegalArgumentException("Unknown roles: " + String.join(", ", unknownRoles));
        }

        appUserMapper.deleteUserRoles(id);
        for (String role : normalizedRoles) {
            int rows = appUserMapper.insertUserRole(id, role);
            if (rows == 0) {
                throw new IllegalArgumentException("Unknown role: " + role);
            }
        }

        return getUser(id);
    }

    private AppUser enrichUser(AppUser user) {
        user.setRoles(appUserMapper.listUserRoles(user.getUserId()));
        return user;
    }

    private void validateId(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("User id must be a positive integer.");
        }
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role must be provided.");
        }

        LinkedHashSet<String> normalizedRoles = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("Roles cannot be blank.");
            }
            normalizedRoles.add(role.trim().toUpperCase());
        }

        return new ArrayList<>(normalizedRoles);
    }
}
