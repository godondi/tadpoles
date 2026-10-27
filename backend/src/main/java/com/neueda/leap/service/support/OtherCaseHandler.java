package com.neueda.leap.service.support;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class OtherCaseHandler implements SupportCategoryHandler {
    public Definition definition() {
        return new Definition(
                "OTHER",
                List.of("GENERAL", "PRODUCT", "DUPLICATE", "NOT_REPRODUCED"),
                Set.of(
                        "reason",
                        "affectedArea",
                        "expectedBehavior",
                        "actualBehavior",
                        "occurredAt",
                        "steps",
                        "duplicateReference"),
                List.of(
                        new Task(
                                "TRIAGE_QUESTION",
                                "Record affected area and expected versus actual behavior"),
                        new Task(
                                "TRIAGE_REPRODUCTION",
                                "Record safe attempted reproduction steps and unavailable facts"),
                        new Task(
                                "TRIAGE_OWNER",
                                "Classify the issue and identify a responsible next owner"),
                        new Task(
                                "TRIAGE_OUTCOME",
                                "Record guidance, authorized duplicate or tracked product"
                                        + " referral")),
                List.of(
                        "GENERAL_GUIDANCE_PROVIDED",
                        "ISSUE_NOT_REPRODUCED",
                        "DUPLICATE_CASE_LINKED",
                        "REFERRED_TO_PRODUCT_SUPPORT"));
    }
}
