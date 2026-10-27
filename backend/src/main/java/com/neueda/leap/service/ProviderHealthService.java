package com.neueda.leap.service;

import static com.neueda.leap.trading.TradingData.map;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Map;

/**
 * Fixed-size, process-local telemetry from ordinary provider traffic; never probes on dashboard
 * refresh.
 */
@Service
public class ProviderHealthService {
    private final Clock clock;
    private final Instant startedAt;
    private Instant lastSuccess, lastFailure, lastObserved;
    private long successes, failures, latencyMillis;
    private String failureCategory;

    public ProviderHealthService(Clock clock) {
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    public synchronized void success(long latency) {
        successes++;
        lastSuccess = clock.instant();
        lastObserved = lastSuccess;
        latencyMillis = latency;
        failureCategory = null;
    }

    public synchronized void failure(RuntimeException failure, long latency) {
        failures++;
        lastFailure = clock.instant();
        lastObserved = lastFailure;
        latencyMillis = latency;
        failureCategory = "TRANSPORT_UNAVAILABLE";
        if (failure instanceof org.springframework.web.client.HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            failureCategory =
                    status == 401 || status == 403
                            ? "CREDENTIALS_REJECTED"
                            : status == 429 ? "RATE_LIMITED" : "PROVIDER_ERROR";
        } else if (failure instanceof org.springframework.web.client.ResourceAccessException)
            failureCategory = "TIMEOUT_OR_CONNECTION";
    }

    public synchronized Map<String, Object> health() {
        String status =
                lastObserved == null || lastObserved.isBefore(clock.instant().minusSeconds(120))
                        ? "UNKNOWN"
                        : failureCategory == null ? "READY" : "DEGRADED";
        return map(
                "status",
                status,
                "scope",
                "This application instance; normal traffic only",
                "observationStartedAt",
                startedAt,
                "lastObservedAt",
                lastObserved,
                "lastSuccessAt",
                lastSuccess,
                "lastFailureAt",
                lastFailure,
                "successes",
                successes,
                "failures",
                failures,
                "lastLatencyMillis",
                latencyMillis,
                "failureCategory",
                failureCategory,
                "note",
                "Transport success does not establish quote eligibility. No recent traffic is"
                        + " UNKNOWN.");
    }
}
