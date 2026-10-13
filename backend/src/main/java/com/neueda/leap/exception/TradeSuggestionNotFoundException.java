package com.neueda.leap.exception;

public class TradeSuggestionNotFoundException extends RuntimeException {
    public TradeSuggestionNotFoundException(Integer suggestionId) {
        super("Trade suggestion with id " + suggestionId + " was not found.");
    }
}

