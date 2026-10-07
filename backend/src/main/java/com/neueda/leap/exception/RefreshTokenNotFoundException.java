package com.neueda.leap.exception;

public class RefreshTokenNotFoundException extends RuntimeException {
    public RefreshTokenNotFoundException(Integer refreshTokenId) {
        super("Refresh token with id " + refreshTokenId + " was not found.");
    }
}

