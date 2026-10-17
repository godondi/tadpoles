package com.neueda.leap.trading;

import com.neueda.leap.service.FauxnanceQuoteService;
import com.neueda.leap.trading.TradingModels.*;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class FauxnanceTradingQuotes implements TradingQuotes {
    private final FauxnanceQuoteService provider;
    private final Clock clock;

    public FauxnanceTradingQuotes(FauxnanceQuoteService provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public Quote quote(Policy policy) {
        if (policy == null || policy.providerSymbol() == null) return null;
        var response = provider.getQuote(policy.providerSymbol());
        if (response == null || response.getData() == null || response.getMeta() == null)
            return null;
        var data = response.getData();
        var meta = response.getMeta();
        if (!policy.providerSymbol().equals(data.getSymbol())
                || !policy.providerSymbol().equals(meta.getSymbol()))
            throw new IllegalArgumentException(
                    "Provider symbol metadata does not match the configured instrument");
        // USD is the only supported provider price unit. No implicit currency conversion.
        return new Quote(
                java.util.UUID.randomUUID(),
                policy.instrumentId(),
                data.getSymbol(),
                data.getCurrency(),
                data.getCurrency(),
                data.getCurrency(),
                data.getBid(),
                data.getAsk(),
                data.getBid(),
                data.getAsk(),
                data.getAsOf() == null ? null : data.getAsOf().toInstant(),
                clock.instant(),
                meta.getSource(),
                meta.getDisclaimer(),
                meta.getSpreadSource(),
                data.getMarketState(),
                meta.getStale(),
                meta.getAsOf() == null ? null : meta.getAsOf().toInstant());
    }
}
