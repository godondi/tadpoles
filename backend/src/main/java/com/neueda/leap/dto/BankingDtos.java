package com.neueda.leap.dto;

import java.time.Instant;
import java.util.UUID;

public final class BankingDtos {
    private BankingDtos() {}

    public record BankRequest(
            String nickname,
            String routingNumber,
            String accountNumber,
            Boolean defaultAccount,
            Long version) {}

    public record BankReference(
            UUID bankId,
            String nickname,
            String maskedAccount,
            boolean defaultAccount,
            long version) {}

    public record MovementRequest(UUID bankId, String direction, String amount) {}

    public record Movement(
            UUID movementId,
            UUID bankId,
            String direction,
            String amount,
            String currency,
            String status,
            String reason,
            String bankSnapshot,
            Instant recordedAt) {}
}
