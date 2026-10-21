package com.neueda.leap.service;

import static com.neueda.leap.trading.TradingData.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.mapper.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SupportExportService {
    private final SupportCaseService cases;
    private final SupportCaseMapper db;
    private final OperationsAccessMapper contexts;
    private final OperationsAccessService access;
    private final OperationsExportService exports;

    public SupportExportService(
            SupportCaseService cases,
            SupportCaseMapper db,
            OperationsAccessMapper contexts,
            OperationsAccessService access,
            OperationsExportService exports) {
        this.cases = cases;
        this.db = db;
        this.contexts = contexts;
        this.access = access;
        this.exports = exports;
    }

    @Transactional
    public Object associate(String reference, UUID context, JsonNode body) {
        var p = cases.identity(true, "CASE_UPDATE");
        SupportCaseService.fields(body, Set.of("exportId"));
        UUID export;
        try {
            export = UUID.fromString(SupportCaseService.text(body, "exportId", 36, 36, false));
        } catch (IllegalArgumentException failure) {
            throw SupportCaseService.error(HttpStatus.BAD_REQUEST, "Invalid export reference");
        }
        p.put("reference", reference);
        var c = db.lock(p);
        if (c == null) cases.missing(p);
        var grant = access.require(context, "EVIDENCE_EXPORT");
        if (!access.has(grant, "CASE_READ", integer(c, "client_id"))) cases.missing(p);
        var binding = contexts.binding(context);
        if (!Objects.equals(c.get("case_id"), binding.get("managed_case_id"))) cases.missing(p);
        var receipt = exports.association(context, export);
        if (c.get("order_id") != null && !Objects.equals(c.get("order_id"), receipt.get("orderId")))
            cases.missing(p);
        p.putAll(map("id", c.get("case_id"), "export", export, "digest", receipt.get("digest")));
        db.associate(p);
        cases.audit(p, "CASE_EXPORT_ASSOCIATED", reference, 1);
        return receipt;
    }
}
