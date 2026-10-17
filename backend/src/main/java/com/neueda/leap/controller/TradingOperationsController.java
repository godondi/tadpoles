package com.neueda.leap.controller;

import static com.neueda.leap.trading.TradingData.map;

import com.neueda.leap.mapper.*;
import com.neueda.leap.service.*;
import com.neueda.leap.service.impl.ReconciliationService;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class TradingOperationsController {
    private final com.neueda.leap.trading.ExecutionWorker executionWorker;
    private final ReconciliationService reconciliation;
    private final AppUserService users;
    private final TradingMapper trading;
    private final ReconciliationMapper db;
    private final TradingSetupMapper setup;
    private final TradingService service;
    private final boolean worker;
    private final boolean provider;

    public TradingOperationsController(
            ReconciliationService reconciliation,
            AppUserService users,
            TradingMapper trading,
            ReconciliationMapper db,
            TradingService service,
            TradingSetupMapper setup,
            com.neueda.leap.trading.ExecutionWorker executionWorker,
            @Value("${trading.worker-enabled:false}") boolean worker,
            @Value("${fauxnance.api-key:}") String key) {
        this.executionWorker = executionWorker;
        this.setup = setup;
        this.reconciliation = reconciliation;
        this.users = users;
        this.trading = trading;
        this.db = db;
        this.service = service;
        this.worker = worker;
        this.provider = key != null && !key.isBlank();
    }

    @PostMapping("/api/finance/reconciliations")
    @PreAuthorize("hasRole('FINANCE') and !hasRole('CLIENT')")
    public Object reconcile(@AuthenticationPrincipal Jwt jwt) {
        return reconciliation.run(SecurityRoleSupport.resolveCurrentUser(jwt, users).getUserId());
    }

    @GetMapping("/api/finance/reconciliations")
    @PreAuthorize("hasRole('FINANCE') and !hasRole('CLIENT')")
    public Object latest() {
        return reconciliation.latest();
    }

    @GetMapping("/api/operations/readiness")
    @PreAuthorize("hasRole('TRADING_OPS') and !hasRole('CLIENT')")
    public Object readiness() {
        return map(
                "schemaReady",
                trading.schemaReady(),
                "referenceDataReady",
                db.referenceDataReady(),
                "runtimePermissions",
                map(
                        "operationalAccess",
                        db.operationalAccess(),
                        "evidenceProtected",
                        db.evidenceProtected()),
                "workerHealth",
                executionWorker.health(),
                "pendingJobs",
                db.pendingJobs(),
                "oldestPendingOrderAt",
                db.oldestPendingOrder(),
                "cashMovementProcessing",
                "SYNCHRONOUS",
                "pendingCashMovements",
                0,
                "reconciliation",
                reconciliation.latest(),
                "admissionEnabled",
                service.enabled(),
                "workerEnabled",
                worker,
                "providerConfigured",
                provider,
                "enabledPolicies",
                db.enabledPolicies(),
                "overdueJobs",
                db.overdueJobs(),
                "historicalExceptions",
                db.historicalExceptions(),
                "setupChecks",
                setup.checks(),
                "note",
                "Configuration readiness does not prove an executable live quote. Every order"
                        + " independently validates provider freshness and session.");
    }
}
