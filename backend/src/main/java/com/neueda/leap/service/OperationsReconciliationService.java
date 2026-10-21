package com.neueda.leap.service;

import com.neueda.leap.dto.OperationsDtos.*;
import com.neueda.leap.mapper.OperationsReconciliationMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.sql.Timestamp;
import java.util.*;

@Service
public class OperationsReconciliationService {
    private final OperationsAccessService access;
    private final OperationsReconciliationMapper db;
    private final OperationsOrderService evidence;

    public OperationsReconciliationService(
            OperationsAccessService access,
            OperationsReconciliationMapper db,
            OperationsOrderService evidence) {
        this.access = access;
        this.db = db;
        this.evidence = evidence;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public ReconciliationView read(UUID context, UUID id, int page) {
        var grant = access.require(context, "RECONCILIATION_READ");
        OperationsOrderService.integer(Integer.toString(page), 0, 1000);
        var run = db.run(id);
        if (run == null) {
            if (id != null) access.notFound(grant, id.toString());
            access.success(grant, "RECONCILIATION", "RUN", null, "", 0, null);
            return new ReconciliationView(
                    null, null, "UNAVAILABLE", "No stored run is available.", List.of(), false, 0);
        }
        UUID runId = (UUID) run.get("run_id");
        var p = grant.parameters();
        p.put("id", runId);
        p.put("offset", page * 100);
        var rows = db.page(p);
        var items = rows.stream().limit(100).map(evidence::evidence).toList();
        access.success(
                grant,
                "RECONCILIATION",
                "RUN",
                runId.toString(),
                Integer.toString(page),
                items.size(),
                null);
        return new ReconciliationView(
                runId,
                ((Timestamp) run.get("checked_at")).toInstant(),
                items.isEmpty() ? "NO_VISIBLE_FINDINGS" : "MISMATCH",
                "Only permitted account findings are shown. Unknown ownership and other scopes are"
                    + " excluded. This is internal-record consistency, not custody reconciliation."
                    + " A page with no findings does not certify the full run.",
                items,
                rows.size() > 100,
                page);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public EvidencePage migration(UUID context, int page) {
        var grant = access.require(context, "RECONCILIATION_READ");
        OperationsOrderService.integer(Integer.toString(page), 0, 1000);
        var p = grant.parameters();
        p.put("offset", page * 100);
        var rows = db.migration(p);
        access.success(
                grant,
                "MIGRATION_REVIEW",
                "EXCEPTIONS",
                null,
                Integer.toString(page),
                Math.min(rows.size(), 100),
                null);
        return new EvidencePage(
                rows.stream().limit(100).map(evidence::evidence).toList(), page, rows.size() > 100);
    }
}
