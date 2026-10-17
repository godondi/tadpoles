package com.neueda.leap.service.impl;

import static com.neueda.leap.trading.TradingData.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.mapper.ReconciliationMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReconciliationService {
    private final ReconciliationMapper db;
    private final ObjectMapper json;
    private final Clock clock;

    public ReconciliationService(ReconciliationMapper db, ObjectMapper json, Clock clock) {
        this.db = db;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Object run(int actor) {
        var exceptions = db.exceptions();
        var id = UUID.randomUUID();
        var now = clock.instant();
        var report =
                map(
                        "runId",
                        id,
                        "checkedAt",
                        now,
                        "matched",
                        exceptions.isEmpty(),
                        "exceptions",
                        exceptions,
                        "historicalExceptions",
                        db.historicalExceptions(),
                        "scope",
                        "Internal cash, units, holds, fills, movement postings and event outbox;"
                                + " not an external custody reconciliation");
        try {
            db.save(
                    map(
                            "id",
                            id,
                            "actor",
                            actor,
                            "now",
                            now,
                            "matched",
                            exceptions.isEmpty(),
                            "report",
                            json.writeValueAsString(report)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return report;
    }

    public Object latest() {
        String value = db.latest();
        if (value == null)
            return map(
                    "runId",
                    null,
                    "message",
                    "Run a reconciliation to inspect a consistent snapshot");
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
