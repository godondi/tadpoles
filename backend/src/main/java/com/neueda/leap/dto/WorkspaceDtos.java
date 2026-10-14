package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class WorkspaceDtos {
    private WorkspaceDtos() { }
    public record Page<T>(List<T> items, int page, int pageSize, long total) { }
    public record Position(Integer holdingId, Integer instrumentId, String ticker, String name,
            String currency, BigDecimal quantity, LocalDate asOfDate) { }
    public record Portfolio(BigDecimal postedCash, String currency, BigDecimal availableCash,
            List<Position> positions, OffsetDateTime retrievedAt, String limitation) { }
    public record Order(Integer orderId, Integer instrumentId, String ticker, String instrumentName,
            String currency, String side, BigDecimal quantity, BigDecimal recordedPrice,
            LocalDate tradeDate, String status, LocalDateTime executedAt, String reason) { }
    public record OrderDetail(Order order, String evidenceCompleteness, String explanation) { }
    public record TradingAvailability(boolean enabled, String reason) { }
}
