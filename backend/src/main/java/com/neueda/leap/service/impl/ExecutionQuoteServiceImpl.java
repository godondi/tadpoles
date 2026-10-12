package com.neueda.leap.service.impl;

import com.neueda.leap.domain.FauxnanceQuote;
import com.neueda.leap.domain.FauxnanceQuoteResponse;
import com.neueda.leap.domain.Instrument;
import com.neueda.leap.service.ExecutionQuoteService;
import com.neueda.leap.service.FauxnanceQuoteService;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ExecutionQuoteServiceImpl implements ExecutionQuoteService {
    private static final Set<String> EQUITY_ALIASES = Set.of(
            "EQUITY", "EQUITIES", "STOCK", "SHARE", "COMMON STOCK", "ORDINARY SHARE"
    );
    private static final Set<String> FX_ALIASES = Set.of(
            "FX", "FOREX", "FOREIGN EXCHANGE", "CURRENCY", "CURRENCY PAIR"
    );
    private static final Set<String> CRYPTO_ALIASES = Set.of(
            "CRYPTO", "CRYPTOCURRENCY", "DIGITAL ASSET", "TOKEN", "COIN"
    );

    private final FauxnanceQuoteService fauxnanceQuoteService;

    public ExecutionQuoteServiceImpl(FauxnanceQuoteService fauxnanceQuoteService) {
        this.fauxnanceQuoteService = fauxnanceQuoteService;
    }

    @Override
    public ExecutionQuote getExecutionQuote(Instrument instrument) {
        if (instrument == null) {
            throw new IllegalArgumentException("Instrument is required to price the trade.");
        }
        if (instrument.getTicker() == null || instrument.getTicker().isBlank()) {
            throw new IllegalArgumentException("Instrument ticker is required to price the trade.");
        }

        String supportedClass = resolveSupportedLaunchClass(instrument);
        FauxnanceQuoteResponse quoteResponse = fauxnanceQuoteService.getQuote(instrument.getTicker());
        if (quoteResponse.getMeta() == null || Boolean.TRUE.equals(quoteResponse.getMeta().getStale())) {
            throw new IllegalArgumentException("Current market quote is unavailable for execution.");
        }

        FauxnanceQuote quote = quoteResponse.getData();
        if (quote == null) {
            throw new IllegalArgumentException("Current market quote is unavailable for execution.");
        }

        return new ExecutionQuote(
                quote.getSymbol() == null || quote.getSymbol().isBlank() ? instrument.getTicker().trim().toUpperCase(Locale.ROOT)
                        : quote.getSymbol().trim().toUpperCase(Locale.ROOT),
                supportedClass,
                quote.getBid(),
                quote.getAsk(),
                Boolean.TRUE.equals(quoteResponse.getMeta().getStale())
        );
    }

    private String resolveSupportedLaunchClass(Instrument instrument) {
        String assetClass = normalizeValue(instrument.getAssetClass());
        String securityType = normalizeValue(instrument.getSecurityType());

        if (EQUITY_ALIASES.contains(assetClass) || EQUITY_ALIASES.contains(securityType)) {
            return "EQUITY";
        }
        if (FX_ALIASES.contains(assetClass) || FX_ALIASES.contains(securityType)) {
            return "FX";
        }
        if (CRYPTO_ALIASES.contains(assetClass) || CRYPTO_ALIASES.contains(securityType)) {
            return "CRYPTO";
        }

        throw new IllegalArgumentException("Instrument asset class is not supported for launch pricing.");
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
