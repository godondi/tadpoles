package com.neueda.leap.exception;

public class AdvisorNotFoundException extends RuntimeException {
    public AdvisorNotFoundException(Integer id) {
        super("Advisor with id " + id + " was not found.");
    }
}
