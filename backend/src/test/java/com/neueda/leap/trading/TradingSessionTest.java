package com.neueda.leap.trading;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TradingSessionTest {
    boolean scheduled(String at) {
        return TradingSession.isOpen("unknown", "NASDAQ", Instant.parse(at));
    }

    @Test
    void fridaySessionUsesEasternTimeAndExactBoundaries() {
        assertFalse(scheduled("2026-10-16T13:29:59Z"));
        assertTrue(scheduled("2026-10-16T13:30:00Z"));
        assertTrue(scheduled("2026-10-16T13:35:00Z"));
        assertTrue(scheduled("2026-10-16T19:59:59Z"));
        assertFalse(scheduled("2026-10-16T20:00:00Z"));
    }

    @Test
    void winterSessionAccountsForDaylightSavingTime() {
        assertFalse(scheduled("2026-11-06T13:35:00Z"));
        assertTrue(scheduled("2026-11-06T14:35:00Z"));
        assertTrue(scheduled("2026-11-06T20:59:59Z"));
        assertFalse(scheduled("2026-11-06T21:00:00Z"));
    }

    @Test
    void weekendsHolidaysAndEarlyClosesAreExcluded() {
        assertFalse(scheduled("2026-10-17T14:00:00Z"));
        assertFalse(scheduled("2026-04-03T14:00:00Z"));
        assertFalse(scheduled("2026-07-03T14:00:00Z"));
        assertFalse(scheduled("2026-11-26T15:00:00Z"));
        assertTrue(scheduled("2026-11-27T17:59:59Z"));
        assertFalse(scheduled("2026-11-27T18:00:00Z"));
        assertFalse(scheduled("2026-12-24T18:00:00Z"));
        assertFalse(scheduled("2026-12-25T15:00:00Z"));
    }

    @Test
    void fallbackRequiresAnExplicitUnknownStateAndAVerifiedVenueAndYear() {
        Instant now = Instant.parse("2026-10-16T13:35:00Z");
        for (String state : new String[] {"closed", "pre", "post", "halted", "OPEN", "", null})
            assertFalse(TradingSession.isOpen(state, "NYSE", now));
        assertFalse(TradingSession.isOpen("unknown", "LSE", now));
        assertFalse(TradingSession.isOpen("unknown", null, now));
        assertFalse(scheduled("2027-10-15T13:35:00Z"));
        assertTrue(TradingSession.isOpen("unknown", "NYSE Arca", now));
        assertTrue(TradingSession.isOpen("open", "NYSE", now));
    }
}
