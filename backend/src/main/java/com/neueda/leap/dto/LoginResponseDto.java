package com.neueda.leap.dto;

public record LoginResponseDto(
        String token,
        String tokenType,
        long expiresIn
) {
}

