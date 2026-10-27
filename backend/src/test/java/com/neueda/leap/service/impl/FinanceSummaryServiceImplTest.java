package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.neueda.leap.mapper.FinanceSummaryMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.server.ResponseStatusException;

class FinanceSummaryServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-22T18:01:00Z");
    private final FinanceSummaryMapper mapper = mock(FinanceSummaryMapper.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FinanceSummaryServiceImpl service = new FinanceSummaryServiceImpl(mapper, clock);

    @Test
    void mapsExactAggregateValuesAndInclusiveUtcDaysToHalfOpenInstants() {
        Instant snapshot = Instant.parse("2026-10-22T18:00:00Z");
        when(mapper.transactionTimestamp()).thenReturn(snapshot);
        when(mapper.aggregate(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-23T00:00:00Z")))
                .thenReturn(
                        new FinanceSummaryMapper.Aggregate(
                                new BigDecimal("100.00"),
                                new BigDecimal("25.00"),
                                new BigDecimal("51.00"),
                                new BigDecimal("0.00"),
                                1));

        var report = service.summary(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-22"));

        assertEquals("finance-summary-v1", report.schemaVersion());
        assertEquals("CURRENT", report.status());
        assertEquals("USD", report.currency());
        assertEquals("UTC", report.period().timeZone());
        assertEquals(snapshot, report.dataAsOf());
        assertEquals(NOW, report.generatedAt());
        assertEquals("finance-summary-v1", report.definitionVersion());
        assertEquals("primary-postgresql-ledger", report.source());
        assertEquals("100.00", report.totals().settledDeposits());
        assertEquals("25.00", report.totals().settledWithdrawals());
        assertEquals("51.00", report.totals().grossBuyConsideration());
        assertEquals("0.00", report.totals().grossSellConsideration());
        assertEquals(1, report.totals().bookedFillCount());
        assertTrue(report.limitations().stream().anyMatch(value -> value.contains("not a full general ledger")));
        verify(mapper).aggregate(
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-23T00:00:00Z"));
    }

    @Test
    void acceptsExactly366InclusiveDaysAndRejectsLongerFutureOrInvertedRangesBeforeQuerying() {
        when(mapper.transactionTimestamp()).thenReturn(NOW);
        when(mapper.aggregate(any(), any()))
                .thenReturn(
                        new FinanceSummaryMapper.Aggregate(
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0));

        LocalDate firstDay = LocalDate.parse("2025-10-22");
        LocalDate lastDay = LocalDate.parse("2026-10-22");
        assertEquals(366, ChronoUnit.DAYS.between(firstDay, lastDay) + 1);
        var validRange = service.summary(firstDay, lastDay).period();
        assertEquals(firstDay, validRange.from());
        assertEquals(lastDay, validRange.to());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.summary(LocalDate.parse("2025-10-21"), LocalDate.parse("2026-10-22")));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.summary(LocalDate.parse("2026-10-23"), LocalDate.parse("2026-10-23")));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.summary(LocalDate.parse("2026-10-23"), LocalDate.parse("2026-10-22")));
        verify(mapper, times(1)).aggregate(any(), any());
    }

    @Test
    void failsUnavailableRatherThanReturningZeroWhenPrimarySourceCannotBeRead() {
        when(mapper.transactionTimestamp()).thenThrow(new DataAccessResourceFailureException("database unavailable"));

        ResponseStatusException error =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.summary(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-22")));

        assertEquals(503, error.getStatusCode().value());
        verify(mapper, never()).aggregate(any(), any());
    }

    @Test
    void treatsMissingSnapshotOrAggregateAsUnavailableInsteadOfAnEmptyReport() {
        when(mapper.transactionTimestamp()).thenReturn(null);
        ResponseStatusException missingSnapshot =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.summary(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-22")));
        assertEquals(503, missingSnapshot.getStatusCode().value());

        when(mapper.transactionTimestamp()).thenReturn(NOW);
        when(mapper.aggregate(any(), any())).thenReturn(null);
        ResponseStatusException missingAggregate =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.summary(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-22")));
        assertEquals(503, missingAggregate.getStatusCode().value());
    }

        @Test
        void rejectsMoneyValuesThatWouldRequireRoundingInsteadOfChangingTheSourceValue() {
                when(mapper.transactionTimestamp()).thenReturn(NOW);
                when(mapper.aggregate(any(), any()))
                                .thenReturn(
                                                new FinanceSummaryMapper.Aggregate(
                                                                new BigDecimal("1.001"),
                                                                BigDecimal.ZERO,
                                                                BigDecimal.ZERO,
                                                                BigDecimal.ZERO,
                                                                0));
                assertThrows(
                                ArithmeticException.class,
                                () -> service.summary(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-22")));
        }
}
