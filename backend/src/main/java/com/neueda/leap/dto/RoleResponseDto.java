package com.neueda.leap.dto;

import com.neueda.leap.domain.Role;

public record RoleResponseDto(
        Integer roleId,
        String roleName
) {
    public static RoleResponseDto fromEntity(Role role) {
        return new RoleResponseDto(
                role.getRoleId(),
                role.getRoleName()
        );
    }
}

