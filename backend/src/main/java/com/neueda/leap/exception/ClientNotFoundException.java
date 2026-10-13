package com.neueda.leap.exception;

public class ClientNotFoundException extends RuntimeException {
    public ClientNotFoundException(Integer id) {
        super("Client with id " + id + " was not found.");
    }
}

