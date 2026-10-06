package com.neueda.leap.dto;

public record ClientRegistrationResponseDto(
        Integer userId,
        String username,
        String displayName,
        String role,
        Integer clientId
) {
}
