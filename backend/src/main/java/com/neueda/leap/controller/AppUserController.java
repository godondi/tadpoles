package com.neueda.leap.controller;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.AppUserListResponseDto;
import com.neueda.leap.dto.AppUserResponseDto;
import com.neueda.leap.dto.SetAppUserRolesRequestDto;
import com.neueda.leap.dto.UpdateAppUserRequestDto;
import com.neueda.leap.service.AppUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class AppUserController {
    private final AppUserService appUserService;

    public AppUserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public AppUserResponseDto getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return AppUserResponseDto.fromEntity(SecurityRoleSupport.resolveCurrentUser(jwt, appUserService));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AppUserListResponseDto listUsers() {
        return AppUserListResponseDto.fromEntities(appUserService.listUsers());
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AppUserResponseDto getUser(@PathVariable Integer userId) {
        AppUser user = appUserService.getUser(userId);
        return AppUserResponseDto.fromEntity(user);
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AppUserResponseDto updateUser(
            @PathVariable Integer userId,
            @RequestBody UpdateAppUserRequestDto request
    ) {
        AppUser user = appUserService.updateUser(userId, request);
        return AppUserResponseDto.fromEntity(user);
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public AppUserResponseDto setUserRoles(
            @PathVariable Integer userId,
            @RequestBody SetAppUserRolesRequestDto request
    ) {
        AppUser user = appUserService.setUserRoles(userId, request);
        return AppUserResponseDto.fromEntity(user);
    }
}
