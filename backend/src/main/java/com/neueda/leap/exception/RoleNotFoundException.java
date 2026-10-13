package com.neueda.leap.exception;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(Integer id) {
        super("Role with id " + id + " was not found.");
    }
}

