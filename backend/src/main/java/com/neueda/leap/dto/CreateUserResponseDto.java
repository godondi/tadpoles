package com.neueda.leap.dto;

public record CreateUserResponseDto(
        Integer userId,
        String username,
        String displayName,
        String role,
        Integer advisorId
) {
}

