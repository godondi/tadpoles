package com.neueda.leap.exception;

public class AppUserNotFoundException extends RuntimeException {
    public AppUserNotFoundException(Integer id) {
        super("User with id " + id + " was not found.");
    }

    public AppUserNotFoundException(String username) {
        super("User with username " + username + " was not found.");
    }
}
