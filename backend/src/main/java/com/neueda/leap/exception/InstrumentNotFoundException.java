package com.neueda.leap.exception;

public class InstrumentNotFoundException extends RuntimeException {
    public InstrumentNotFoundException(Integer id) {
        super("Instrument with id " + id + " was not found.");
    }
}

