package com.neueda.leap.service.support;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class OrderQuestionHandler implements SupportCategoryHandler {
    public Definition definition() {
        return new Definition(
                "ORDER_QUESTION",
                List.of("PENDING", "REJECTED", "PRICING", "DUPLICATE", "POSTING", "MISSING_ORDER"),
                Set.of("reason", "relatedOrderId"),
                List.of(
                        new Task(
                                "ORDER_INSTRUCTION",
                                "Verify the owned instruction and admission/execution state"),
                        new Task(
                                "ORDER_EVIDENCE",
                                "Inspect reason-specific retained quote, decision, attempts,"
                                        + " release or postings"),
                        new Task(
                                "ORDER_DECISION",
                                "Record missing facts and Compliance, Finance or incident"
                                        + " dependencies"),
                        new Task(
                                "ORDER_FOLLOW_UP",
                                "Record evidence-based explanation and responsible next owner")),
                List.of(
                        "EXPLAINED_ORDER_STATE",
                        "EXPLAINED_REJECTION",
                        "EXPLAINED_PRICING",
                        "DUPLICATE_NOT_CONFIRMED",
                        "REFERRED_FOR_CORRECTION"));
    }
}
