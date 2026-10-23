package com.neueda.leap.service.impl;

import com.neueda.leap.dto.FinanceSummaryResponseDto;
import com.neueda.leap.mapper.FinanceSummaryMapper;
import com.neueda.leap.service.FinanceSummaryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinanceSummaryServiceImpl implements FinanceSummaryService {
    private static final int MAX_RANGE_DAYS = 366;
    private static final String DEFINITION_VERSION = "finance-summary-v1";
    private static final String SOURCE = "primary-postgresql-ledger";
    private static final List<String> LIMITATIONS =
            List.of(
                    "All-client USD aggregates; no account identifiers or transaction rows are returned.",
                    "Settled movement totals use recorded_at; booked fill totals use booked_at.",
                    "Internal totals are not a full general ledger and do not establish external bank transfer, exchange execution, or custody.");

    private final FinanceSummaryMapper mapper;
    private final Clock clock;

    public FinanceSummaryServiceImpl(FinanceSummaryMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FinanceSummaryResponseDto summary(LocalDate from, LocalDate to) {
        validateRange(from, to);
        Instant fromInclusive = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Instant dataAsOf;
        FinanceSummaryMapper.Aggregate aggregate;
        try {
            // This first read establishes the repeatable-read snapshot for the aggregates below.
            dataAsOf = mapper.transactionTimestamp();
            aggregate = mapper.aggregate(fromInclusive, toExclusive);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
        if (dataAsOf == null || aggregate == null) {
            throw unavailable(null);
        }
        requireAggregateValues(aggregate);

        return new FinanceSummaryResponseDto(
                DEFINITION_VERSION,
                "CURRENT",
                "USD",
                new FinanceSummaryResponseDto.Period(from, to, "UTC"),
                dataAsOf,
                clock.instant(),
                DEFINITION_VERSION,
                SOURCE,
                LIMITATIONS,
                new FinanceSummaryResponseDto.Totals(
                    money(aggregate.settledDeposits()),
                    money(aggregate.settledWithdrawals()),
                    money(aggregate.grossBuyConsideration()),
                    money(aggregate.grossSellConsideration()),
                        aggregate.bookedFillCount()));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both from and to UTC dates are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The from date must not be after the to date");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("The requested reporting range cannot exceed 366 days");
        }
        LocalDate todayUtc = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        if (to.isAfter(todayUtc)) {
            throw new IllegalArgumentException("The to date must not be after the current UTC date");
        }
    }

    private void requireAggregateValues(FinanceSummaryMapper.Aggregate aggregate) {
        Objects.requireNonNull(aggregate.settledDeposits(), "settledDeposits");
        Objects.requireNonNull(aggregate.settledWithdrawals(), "settledWithdrawals");
        Objects.requireNonNull(aggregate.grossBuyConsideration(), "grossBuyConsideration");
        Objects.requireNonNull(aggregate.grossSellConsideration(), "grossSellConsideration");
    }

    private String money(java.math.BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private ResponseStatusException unavailable(Throwable cause) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The primary Finance reporting source is unavailable.",
                cause);
    }
}
