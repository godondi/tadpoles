package com.neueda.leap.trading;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * Scheduled session fallback for internal bookings when Fauxnance has no asset-specific session
 * status. U.S. equity hours use the verified 2026 NYSE/Nasdaq calendar. FX follows the Sunday 17:00
 * through Friday 17:00 Eastern weekly session; absent and exchange-specific closed/pre/post states
 * do not describe the OTC FX session, but explicit halts still reject. Crypto is continuously
 * open. This is not a live market-status feed.
 */
public final class TradingSession {
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final Set<String> FX_NON_SESSION_STATES = Set.of("unknown", "closed", "pre", "post");
    // `venue` is seeded from provider exchange labels (for example, `NYSE ARCA`). Compare
    // normalized labels so casing from the catalog cannot disable the scheduled fallback.
    private static final Set<String> VENUES = Set.of("NYSE", "NASDAQ", "NYSE ARCA", "US");
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
        return isOpen(providerState, venue, null, now);
    }

    public static boolean isOpen(
            String providerState, String venue, String assetClass, Instant now) {
        String normalizedAsset =
                assetClass == null
                        ? ""
                        : assetClass
                                .toUpperCase(java.util.Locale.ROOT)
                                .replaceAll("[^A-Z0-9]", "");
        if ("open".equals(providerState)) return true;
        if (now == null) return false;
        if (Set.of("FX", "FXPAIR", "FOREIGNEXCHANGE").contains(normalizedAsset)) {
            if (providerState == null
                    || providerState.isBlank()
                    || FX_NON_SESSION_STATES.contains(providerState))
                return isFxOpen(now);
            return false;
        }
        if (!"unknown".equals(providerState)) return false;
        if (Set.of("CRYPTO", "CRYPTOCURRENCY").contains(normalizedAsset)) return true;
        return isUsEquityOpen(venue, now);
    }

    public static boolean usesScheduledSession(String providerState, String assetClass) {
        if ("unknown".equals(providerState)) return true;
        if (assetClass == null) return false;
        String normalizedAsset =
                assetClass
                        .toUpperCase(java.util.Locale.ROOT)
                        .replaceAll("[^A-Z0-9]", "");
        return Set.of("FX", "FXPAIR", "FOREIGNEXCHANGE").contains(normalizedAsset)
                && (providerState == null
                        || providerState.isBlank()
                        || FX_NON_SESSION_STATES.contains(providerState));
    }

    private static boolean isFxOpen(Instant now) {
        var local = now.atZone(EASTERN);
        return switch (local.getDayOfWeek()) {
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY -> true;
            case FRIDAY -> local.toLocalTime().isBefore(LocalTime.of(17, 0));
            case SUNDAY -> !local.toLocalTime().isBefore(LocalTime.of(17, 0));
            case SATURDAY -> false;
        };
    }

    private static boolean isUsEquityOpen(String venue, Instant now) {
        if (venue == null
                || !VENUES.contains(venue.trim().toUpperCase(java.util.Locale.ROOT))) return false;
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
