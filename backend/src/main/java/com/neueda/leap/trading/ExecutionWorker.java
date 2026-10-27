package com.neueda.leap.trading;

import com.neueda.leap.mapper.TradingMapper;
import com.neueda.leap.trading.TradingModels.Quote;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class ExecutionWorker {
    private final TradingMapper db;
    private final TradingTransactions tx;
    private final TradingQuotes quotes;
    private final Clock clock;
    private final boolean enabled;
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(ExecutionWorker.class);

    public ExecutionWorker(
            TradingMapper db,
            TradingTransactions tx,
            TradingQuotes quotes,
            Clock clock,
            @Value("${trading.worker-enabled:true}") boolean enabled) {
        this.db = db;
        this.tx = tx;
        this.quotes = quotes;
        this.clock = clock;
        this.enabled = enabled;
    }

    private volatile java.time.Instant lastFailure;
    private volatile long failedJobs;
    private volatile java.time.Instant lastPoll;
    private volatile java.time.Instant lastCompletedPoll;

    public Object health() {
        return TradingData.map(
                "lastFailureAt",
                lastFailure,
                "failedJobs",
                failedJobs,
                "status",
                !enabled
                        ? "DISABLED"
                        : lastCompletedPoll == null
                                        || lastCompletedPoll.isBefore(
                                                clock.instant().minusSeconds(30))
                                ? "UNKNOWN"
                                : lastFailure != null
                                                && lastFailure.isAfter(
                                                        clock.instant().minusSeconds(30))
                                        ? "DEGRADED"
                                        : "READY",
                "scope",
                "This application instance",
                "enabled",
                enabled,
                "lastPollAt",
                lastPoll,
                "lastCompletedPollAt",
                lastCompletedPoll);
    }

    @Scheduled(fixedDelayString = "${trading.poll-ms:1000}")
    public void tick() {
        if (!enabled) return;
        lastPoll = clock.instant();
        for (int id : db.candidates(clock.instant())) {
            try {
                var claim = tx.claim(id);
                if (claim == null) continue;
                Quote q = null;
                String failure = null;
                try {
                    q = quotes.quote(claim.policy());
                } catch (RuntimeException unavailable) {
                    failure = "QUOTE_TRANSPORT";
                }
                tx.book(claim, q, failure);
            } catch (RuntimeException error) {
                lastFailure = clock.instant();
                failedJobs++;
                LOG.error(
                        "Execution job {} will recover after its lease expires ({})",
                        id,
                        error.getClass().getSimpleName());
            }
        }
        lastCompletedPoll = clock.instant();
    }
}
