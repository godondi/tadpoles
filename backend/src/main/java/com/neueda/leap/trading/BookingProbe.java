package com.neueda.leap.trading;

/**
 * Test-only failure injection through dependency substitution; production has no failure switch.
 */
@FunctionalInterface
public interface BookingProbe {
    void after(String write);
}
