package com.neueda.leap.service.support;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class AccountReviewHandler implements SupportCategoryHandler {
    public Definition definition() {
        return new Definition(
                "ACCOUNT_REVIEW",
                List.of("ELIGIBILITY", "CASH_BALANCE", "POSITION_AVAILABILITY", "SETUP"),
                Set.of("reason", "movementId"),
                List.of(
                        new Task(
                                "ACCOUNT_QUESTION",
                                "Establish eligibility, balance, position or setup question"),
                        new Task(
                                "ACCOUNT_RESOURCES",
                                "Distinguish posted, reserved and available resources"),
                        new Task(
                                "ACCOUNT_DEPENDENCIES",
                                "Inspect related pending activity and identify authorized next"
                                        + " owner"),
                        new Task(
                                "ACCOUNT_EXPLANATION",
                                "Record fresh diagnostics or explicit unavailable disposition")),
                List.of(
                        "EXPLAINED_BALANCE",
                        "CLIENT_SETUP_COMPLETED",
                        "ELIGIBILITY_REVIEW_COMPLETED",
                        "REFERRED_FOR_CORRECTION"));
    }
}
