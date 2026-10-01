package com.neueda.leap.exception;

public class ClientSubscriptionNotFoundException extends RuntimeException {
    public ClientSubscriptionNotFoundException(Integer clientId, Integer subscriptionId) {
        super("Subscription with id " + subscriptionId + " for client " + clientId + " was not found.");
    }
}
