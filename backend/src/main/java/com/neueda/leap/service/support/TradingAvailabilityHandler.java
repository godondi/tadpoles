package com.neueda.leap.service.support;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class TradingAvailabilityHandler implements SupportCategoryHandler {
    public Definition definition() {
        return new Definition(
                "TRADING_AVAILABILITY",
                List.of("CANNOT_BUY", "CANNOT_SELL", "ACCOUNT_WIDE", "PROVIDER", "SESSION"),
                Set.of("reason", "instrumentId", "side", "observedAt", "reportedReason"),
                List.of(
                        new Task(
                                "AVAILABILITY_ACCOUNT",
                                "Check account prerequisites independently of instrument controls"),
                        new Task(
                                "AVAILABILITY_CONTROLS",
                                "Check active, enabled, buy and sell controls and session"),
                        new Task(
                                "AVAILABILITY_PROVIDER",
                                "Distinguish provider transport, quote freshness and retained past"
                                        + " observations"),
                        new Task(
                                "AVAILABILITY_RECOVERY",
                                "Recheck the relevant condition before claiming recovery")),
                List.of(
                        "PREREQUISITE_EXPLAINED",
                        "EXPECTED_CONTROL_CONFIRMED",
                        "SERVICE_RECOVERED",
                        "POLICY_CHANGE_VERIFIED"));
    }
}
