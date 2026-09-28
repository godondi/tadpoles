package com.neueda.leap.exception;

public class ClientTradeNotFoundException extends RuntimeException {
    public ClientTradeNotFoundException(Integer clientId, Integer tradeId) {
        super("Trade with id " + tradeId + " was not found for client " + clientId + ".");
    }
}

