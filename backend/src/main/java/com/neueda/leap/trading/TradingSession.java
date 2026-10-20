package com.neueda.leap.trading;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * Scheduled core session fallback for internal bookings when Fauxnance reports "unknown". Never
 * rewrites the provider observation or overrides an explicit closed/pre/post state. Calendar
 * sources: https://www.nyse.com/trade/hours-calendars and
 * https://www.nasdaqtrader.com/trader.aspx?id=Calendar (2026 schedule). Extend the verified
 * calendar before enabling fallback for another year. Unscheduled closures require an Operations
 * halt; this is not a live exchange-status feed.
 */
public final class TradingSession {
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final Set<String> VENUES = Set.of("NYSE", "NASDAQ", "NYSE Arca");
    private static final Set<LocalDate> HOLIDAYS =
            Set.of(
                    LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-19"),
                    LocalDate.parse("2026-02-16"), LocalDate.parse("2026-04-03"),
                    LocalDate.parse("2026-05-25"), LocalDate.parse("2026-06-19"),
                    LocalDate.parse("2026-07-03"), LocalDate.parse("2026-09-07"),
                    LocalDate.parse("2026-11-26"), LocalDate.parse("2026-12-25"));
    private static final Set<LocalDate> EARLY_CLOSES =
            Set.of(LocalDate.parse("2026-11-27"), LocalDate.parse("2026-12-24"));

    private TradingSession() {}

    public static boolean isOpen(String providerState, String venue, Instant now) {
        if ("open".equals(providerState)) return true;
        if (!"unknown".equals(providerState)
                || venue == null
                || !VENUES.contains(venue)
                || now == null) return false;
        var local = now.atZone(EASTERN);
        var date = local.toLocalDate();
        if (date.getYear() != 2026
                || local.getDayOfWeek() == DayOfWeek.SATURDAY
                || local.getDayOfWeek() == DayOfWeek.SUNDAY
                || HOLIDAYS.contains(date)) return false;
        var close = EARLY_CLOSES.contains(date) ? LocalTime.of(13, 0) : LocalTime.of(16, 0);
        return !local.toLocalTime().isBefore(LocalTime.of(9, 30))
                && local.toLocalTime().isBefore(close);
    }
}
