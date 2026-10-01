package com.neueda.leap.controller;

import com.neueda.leap.domain.Role;
import com.neueda.leap.dto.RoleListResponseDto;
import com.neueda.leap.dto.RoleResponseDto;
import com.neueda.leap.service.RoleService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public RoleListResponseDto listRoles(@AuthenticationPrincipal Jwt jwt) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN");
        return RoleListResponseDto.fromEntities(roleService.listRoles());
    }

    @GetMapping("/{roleId}")
    public RoleResponseDto getRole(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer roleId) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN");
        Role role = roleService.getRole(roleId);
        return RoleResponseDto.fromEntity(role);
    }
}

