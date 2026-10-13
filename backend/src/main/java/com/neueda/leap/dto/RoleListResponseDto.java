package com.neueda.leap.dto;

import com.neueda.leap.domain.Role;
import java.util.List;

public record RoleListResponseDto(
        List<RoleResponseDto> roles
) {
    public static RoleListResponseDto fromEntities(List<Role> roles) {
        return new RoleListResponseDto(
                roles.stream().map(RoleResponseDto::fromEntity).toList()
        );
    }
}

