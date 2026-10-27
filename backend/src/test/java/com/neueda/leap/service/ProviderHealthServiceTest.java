package com.neueda.leap.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;

class ProviderHealthServiceTest {
    @Test
    void observationExpiresAndDoesNotConfuseTransportWithQuoteEligibility() {
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-10-18T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        var health = new ProviderHealthService(clock);
        assertEquals("UNKNOWN", health.health().get("status"));
        health.success(35);
        assertEquals("READY", health.health().get("status"));
        assertEquals(1L, health.health().get("successes"));
        assertEquals(35L, health.health().get("lastLatencyMillis"));
        when(clock.instant()).thenReturn(now.plusSeconds(121));
        assertEquals("UNKNOWN", health.health().get("status"));
    }

    @Test
    void failuresExposeSafeCategoriesAndRecoverWithoutRetainingProviderMessages() {
        var health = new ProviderHealthService(Clock.systemUTC());
        health.failure(
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "private provider detail"),
                10);
        assertEquals("CREDENTIALS_REJECTED", health.health().get("failureCategory"));
        assertEquals("DEGRADED", health.health().get("status"));
        assertFalse(health.health().toString().contains("private provider detail"));
        health.failure(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS), 20);
        assertEquals("RATE_LIMITED", health.health().get("failureCategory"));
        health.failure(new ResourceAccessException("private network address"), 30);
        assertEquals("TIMEOUT_OR_CONNECTION", health.health().get("failureCategory"));
        assertEquals(3L, health.health().get("failures"));
        health.success(15);
        assertEquals("READY", health.health().get("status"));
        assertNull(health.health().get("failureCategory"));
    }
}
