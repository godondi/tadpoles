package com.neueda.leap.dto;

public record UpdateAppUserRequestDto(
        String displayName,
        String email,
        Boolean enabled
) {
}
