package com.neueda.leap.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Aggregate-only primary-ledger report. Contains no account identifiers or transaction rows. */
public record FinanceSummaryResponseDto(
        String schemaVersion,
        String status,
        String currency,
        Period period,
        Instant dataAsOf,
        Instant generatedAt,
        String definitionVersion,
        String source,
        List<String> limitations,
        Totals totals) {

    public record Period(LocalDate from, LocalDate to, String timeZone) {}

    public record Totals(
            String settledDeposits,
            String settledWithdrawals,
            String grossBuyConsideration,
            String grossSellConsideration,
            long bookedFillCount) {}
}
