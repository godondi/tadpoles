package com.neueda.leap.dto;

public record ClientRegistrationRequestDto(
        String username,
        String password,
        String email,
        String displayName,
        String clientName
) {
}
