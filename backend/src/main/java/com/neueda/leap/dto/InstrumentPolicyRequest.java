package com.neueda.leap.dto;

/**
 * All economic values are exact decimal strings. USD units and immediate booking are fixed in this
 * release.
 */
public record InstrumentPolicyRequest(
        String ticker,
        String name,
        String providerSymbol,
        String venue,
        String quantityStep,
        String maxQuantity,
        String maxNotional,
        int quoteMaxAgeSeconds,
        boolean active,
        boolean buyAllowed,
        boolean sellAllowed,
        String reason,
        long revision) {}
