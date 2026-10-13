package com.neueda.leap.exception;

public class ClientHoldingNotFoundException extends RuntimeException {
    public ClientHoldingNotFoundException(Integer clientId, Integer holdingId) {
        super("Holding with id " + holdingId + " was not found for client " + clientId + ".");
    }
}

