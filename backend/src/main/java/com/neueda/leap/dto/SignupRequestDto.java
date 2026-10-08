package com.neueda.leap.dto;

public record SignupRequestDto(
        String email,
        String password,
        String displayName
) {
}

