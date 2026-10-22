package com.neueda.leap.service;

import com.neueda.leap.dto.OperationsDtos.*;
import com.neueda.leap.mapper.OperationsOrderMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
public class OperationsPolicyHistoryService {
    private static final Set<String> FIELDS =
            Set.of(
                    "ticker",
                    "name",
                    "currency",
                    "active",
                    "providerSymbol",
                    "venue",
                    "quantityStep",
                    "maxQuantity",
                    "maxNotional",
                    "quoteMaxAgeSeconds",
                    "buyAllowed",
                    "sellAllowed",
                    "revision");
    private final OperationsAccessService access;
    private final OperationsOrderMapper db;
    private final OperationsOrderService evidence;

    public OperationsPolicyHistoryService(
            OperationsAccessService access,
            OperationsOrderMapper db,
            OperationsOrderService evidence) {
        this.access = access;
        this.db = db;
        this.evidence = evidence;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public EvidencePage history(int id, int page) {
        access.actor();
        OperationsOrderService.integer(Integer.toString(id), 1, Integer.MAX_VALUE);
        OperationsOrderService.integer(Integer.toString(page), 0, 1000);
        var rows = db.history(id, 101, page * 100);
        var items =
                rows.stream()
                        .limit(100)
                        .map(
                                row -> {
                                    var copy = new LinkedHashMap<>(row);
                                    copy.put(
                                            "beforeValues",
                                            evidence.parseAllowed(
                                                    copy.get("beforeValues"), FIELDS));
                                    copy.put(
                                            "afterValues",
                                            evidence.parseAllowed(copy.get("afterValues"), FIELDS));
                                    return evidence.evidence(copy);
                                })
                        .toList();
        return new EvidencePage(items, page, rows.size() > 100);
    }
}
