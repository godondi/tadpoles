package com.neueda.leap.service.support;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class HistoricalRecordHandler implements SupportCategoryHandler {
    public Definition definition() {
        return new Definition(
                "HISTORICAL_RECORD",
                List.of("PROVENANCE", "MISSING_FACTS", "ACCOUNTING", "GENERAL"),
                Set.of("reason", "exceptionId", "recordKind"),
                List.of(
                        new Task(
                                "HISTORY_SOURCE",
                                "Identify the original owned record and exception"),
                        new Task(
                                "HISTORY_PROVENANCE",
                                "Establish known and unknown currency, units and source"
                                        + " provenance"),
                        new Task(
                                "HISTORY_REVIEW",
                                "Retain Compliance or Finance review when required"),
                        new Task(
                                "HISTORY_LIMITATIONS",
                                "Explain uncertainty without releasing quarantined resources")),
                List.of(
                        "HISTORICAL_RECORD_EXPLAINED",
                        "INSUFFICIENT_RETAINED_EVIDENCE",
                        "REFERRED_FOR_ACCOUNTING_REVIEW"));
    }
}
