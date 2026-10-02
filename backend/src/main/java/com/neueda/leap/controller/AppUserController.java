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
import org.springframework.web.server.ResponseStatusException;

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
        return AppUserResponseDto.fromEntity(resolveCurrentUser(jwt));
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

    private AppUser resolveCurrentUser(Jwt jwt) {
        Integer userId = resolvePositiveIntegerClaim(jwt, "userId");
        if (userId != null) {
            return appUserService.getUser(userId);
        }

        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            return appUserService.getUserByUsername(subject.trim());
        }

        throw new ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Unable to resolve authenticated user"
        );
    }

    private Integer resolvePositiveIntegerClaim(Jwt jwt, String claimName) {
        Object claimValue = jwt.getClaims().get(claimName);
        if (claimValue instanceof Number number) {
            int value = number.intValue();
            return value > 0 ? value : null;
        }
        if (claimValue instanceof String stringValue && !stringValue.isBlank()) {
            try {
                int value = Integer.parseInt(stringValue.trim());
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
