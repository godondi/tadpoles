package com.neueda.leap.controller;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.AppUserListResponseDto;
import com.neueda.leap.dto.AppUserResponseDto;
import com.neueda.leap.dto.SetAppUserRolesRequestDto;
import com.neueda.leap.dto.UpdateAppUserRequestDto;
import com.neueda.leap.service.AppUserService;
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
    public AppUserResponseDto getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        SecurityRoleSupport.requireAuthenticated(jwt);
        return AppUserResponseDto.fromEntity(resolveCurrentUser(jwt));
    }

    @GetMapping
    public AppUserListResponseDto listUsers(@AuthenticationPrincipal Jwt jwt) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN");
        return AppUserListResponseDto.fromEntities(appUserService.listUsers());
    }

    @GetMapping("/{userId}")
    public AppUserResponseDto getUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer userId
    ) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN");
        AppUser user = appUserService.getUser(userId);
        return AppUserResponseDto.fromEntity(user);
    }

    @PatchMapping("/{userId}")
    public AppUserResponseDto updateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer userId,
            @RequestBody UpdateAppUserRequestDto request
    ) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN");
        AppUser user = appUserService.updateUser(userId, request);
        return AppUserResponseDto.fromEntity(user);
    }

    @PutMapping("/{userId}/roles")
    public AppUserResponseDto setUserRoles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer userId,
            @RequestBody SetAppUserRolesRequestDto request
    ) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN");
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
