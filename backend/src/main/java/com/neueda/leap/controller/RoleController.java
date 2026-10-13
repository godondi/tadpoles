package com.neueda.leap.controller;

import com.neueda.leap.domain.Role;
import com.neueda.leap.dto.RoleListResponseDto;
import com.neueda.leap.dto.RoleResponseDto;
import com.neueda.leap.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('ADMIN')")
    public RoleListResponseDto listRoles() {
        return RoleListResponseDto.fromEntities(roleService.listRoles());
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoleResponseDto getRole(@PathVariable Integer roleId) {
        Role role = roleService.getRole(roleId);
        return RoleResponseDto.fromEntity(role);
    }
}
