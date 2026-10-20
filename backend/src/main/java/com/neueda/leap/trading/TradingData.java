package com.neueda.leap.trading;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class TradingData {
    private TradingData() {}

    public static BigDecimal amount(Map<String, Object> row, String key) {
        return row == null || row.get(key) == null
                ? BigDecimal.ZERO
                : new BigDecimal(row.get(key).toString());
    }

    public static int integer(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }

    public static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    public static String string(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : row.get(key).toString();
    }

    public static Instant instant(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return null;
        if (v instanceof java.sql.Timestamp t) return t.toInstant();
        if (v instanceof OffsetDateTime t) return t.toInstant();
        if (v instanceof Instant t) return t;
        throw new IllegalStateException("Unexpected database timestamp type");
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
