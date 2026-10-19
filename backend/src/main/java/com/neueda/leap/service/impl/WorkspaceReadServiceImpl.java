package com.neueda.leap.service.impl;

import com.neueda.leap.dto.InstrumentResponseDto;
import com.neueda.leap.dto.WorkspaceDtos.*;
import com.neueda.leap.mapper.WorkspaceReadMapper;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.WorkspaceReadService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(
        readOnly = true,
        isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class WorkspaceReadServiceImpl implements WorkspaceReadService {
    private final WorkspaceReadMapper mapper;
    private final ClientService clients;

    public WorkspaceReadServiceImpl(WorkspaceReadMapper mapper, ClientService clients) {
        this.mapper = mapper;
        this.clients = clients;
    }

    @Override
    public Portfolio portfolio(int clientId) {
        return new Portfolio(
                null,
                "USD",
                null,
                java.util.List.of(),
                OffsetDateTime.now(),
                "Your account currency and amount available to trade are not yet confirmed. These"
                    + " holding records may be historical and do not confirm what you currently own"
                    + " or can sell.");
    }

    @Override
    public Page<Order> orders(
            int clientId,
            int page,
            int pageSize,
            String status,
            String side,
            Integer instrumentId,
            LocalDate from,
            LocalDate to) {
        return orders(clientId, page, pageSize, status, side, instrumentId, from, to, null);
    }

    @Override
    public Page<Order> orders(
            int clientId,
            int page,
            int pageSize,
            String status,
            String side,
            Integer instrumentId,
            LocalDate from,
            LocalDate to,
            String rejectionStage) {
        validatePage(page, pageSize);
        if (rejectionStage != null && !Set.of("ADMISSION", "EXECUTION").contains(rejectionStage))
            throw new IllegalArgumentException("Unknown rejection stage");
        if (status != null
                && !Set.of(
                                "PENDING",
                                "APPROVED",
                                "REJECTED",
                                "EXECUTED",
                                "ACCEPTED",
                                "EXECUTING",
                                "FILLED")
                        .contains(status))
            throw new IllegalArgumentException("Unknown order status");
        if (side != null && !Set.of("BUY", "SELL").contains(side))
            throw new IllegalArgumentException("Unknown order side");
        if (instrumentId != null && instrumentId < 1)
            throw new IllegalArgumentException("Instrument ID must be positive");
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("Start date must precede end date");
        if (rejectionStage != null)
            return new Page<>(
                    mapper.ordersByRejection(
                            clientId,
                            status,
                            side,
                            instrumentId,
                            from,
                            to,
                            rejectionStage,
                            pageSize,
                            page * pageSize),
                    page,
                    pageSize,
                    mapper.rejectedCount(
                            clientId, status, side, instrumentId, from, to, rejectionStage));
        return new Page<>(
                mapper.orders(
                        clientId, status, side, instrumentId, from, to, pageSize, page * pageSize),
                page,
                pageSize,
                mapper.orderCount(clientId, status, side, instrumentId, from, to));
    }

    @Override
    public OrderDetail order(int clientId, int orderId) {
        if (orderId < 1) throw new IllegalArgumentException("Order ID must be positive");
        Order order = mapper.order(clientId, orderId);
        if (order == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        return new OrderDetail(
                order,
                "INCOMPLETE_LEGACY",
                "This older order record does not contain a full acceptance or booking history. The"
                    + " saved price alone does not confirm a fill. The recorded execution time has"
                    + " no time zone.");
    }

    @Override
    public Page<InstrumentResponseDto> instruments(
            int page, int pageSize, String search, String assetClass) {
        validatePage(page, pageSize);
        if (search.length() > 100)
            throw new IllegalArgumentException("Search must be 100 characters or fewer");
        String pattern =
                "%"
                        + search.trim()
                                .toLowerCase(java.util.Locale.ROOT)
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
        return new Page<>(
                mapper.instruments(pattern, assetClass, pageSize, page * pageSize).stream()
                        .map(InstrumentResponseDto::fromEntity)
                        .toList(),
                page,
                pageSize,
                mapper.instrumentCount(pattern, assetClass));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Page must be 0–100000 and pageSize 1–100");
    }
}
