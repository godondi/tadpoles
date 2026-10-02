package com.neueda.leap.dto;

public record CreateUserRequestDto(
        String username,
        String password,
        String email,
        String displayName,
        String roleType,
        String advisorName,
        Boolean enabled
) {
}

