package com.neueda.leap.service.impl;

import com.neueda.leap.domain.FauxnanceQuoteResponse;
import com.neueda.leap.dto.PortfolioValuationDto;
import com.neueda.leap.mapper.TradingMapper;
import com.neueda.leap.service.FauxnanceQuoteService;
import com.neueda.leap.trading.TradingModels.Policy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PortfolioValuationService {
    private final TradingMapper mapper;
    private final FauxnanceQuoteService provider;
    private final Clock clock;
    private final TransactionTemplate readSnapshot;

    public PortfolioValuationService(
            TradingMapper mapper,
            FauxnanceQuoteService provider,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.provider = provider;
        this.clock = clock;
        this.readSnapshot = new TransactionTemplate(transactions);
        this.readSnapshot.setReadOnly(true);
        this.readSnapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public PortfolioValuationDto value(int clientId) {
        Snapshot snapshot =
                Objects.requireNonNull(readSnapshot.execute(status -> snapshot(clientId)));

        // The consistent ledger read is finished before any external provider request starts.
        List<PricedHolding> quotes =
                snapshot.holdings().stream()
                        .map(holding -> new PricedHolding(holding, fetchQuote(holding.policy())))
                        .toList();
        Instant now = clock.instant();
        BigDecimal holdingsValue = BigDecimal.ZERO;
        Instant oldestQuote = null;
        List<String> unpriced = new ArrayList<>();
        for (PricedHolding priced : quotes) {
            Holding holding = priced.holding();
            FauxnanceQuoteResponse quote = priced.quote();
            if (!validQuote(holding, quote, now)) {
                unpriced.add(holding.ticker());
                continue;
            }
            holdingsValue = holdingsValue.add(holding.owned().multiply(quote.getData().getPrice()));
            Instant quoteTime = quote.getData().getAsOf().toInstant();
            Instant metadataTime = quote.getMeta().getAsOf().toInstant();
            Instant oldest = quoteTime.isBefore(metadataTime) ? quoteTime : metadataTime;
            if (oldestQuote == null || oldest.isBefore(oldestQuote)) {
                oldestQuote = oldest;
            }
        }

        boolean complete = snapshot.cash() != null && unpriced.isEmpty();
        return new PortfolioValuationDto(
                "USD",
                complete ? "COMPLETE" : "UNAVAILABLE",
                complete ? money(snapshot.cash().add(holdingsValue)) : null,
                unpriced.isEmpty() ? money(holdingsValue) : null,
                snapshot.cash() == null ? null : money(snapshot.cash()),
                snapshot.at(),
                oldestQuote,
                List.copyOf(unpriced));
    }

    private Snapshot snapshot(int clientId) {
        var balances = mapper.balances(clientId);
        BigDecimal cash =
                balances.isEmpty()
                        ? null
                        : new BigDecimal(balances.get(0).get("posted").toString());
        List<Holding> holdings =
                mapper.positions(clientId).stream()
                        .map(
                                row ->
                                        new Holding(
                                                row.get("ticker").toString(),
                                                row.get("currency").toString(),
                                                new BigDecimal(row.get("owned").toString()),
                                                mapper.policy(
                                                        ((Number) row.get("instrumentId"))
                                                                .intValue())))
                        .toList();
        return new Snapshot(cash, holdings, clock.instant());
    }

    private FauxnanceQuoteResponse fetchQuote(Policy policy) {
        if (policy == null
                || policy.providerSymbol() == null
                || policy.providerSymbol().isBlank()) {
            return null;
        }
        try {
            return provider.getQuote(policy.providerSymbol());
        } catch (RuntimeException unavailable) {
            // A failed price never hides committed cash or becomes a zero-value holding.
            return null;
        }
    }

    private boolean validQuote(Holding holding, FauxnanceQuoteResponse response, Instant now) {
        Policy policy = holding.policy();
        if (policy == null
                || response == null
                || response.getData() == null
                || response.getMeta() == null) {
            return false;
        }
        var quote = response.getData();
        var meta = response.getMeta();
        return "USD".equals(holding.currency())
                && "USD".equals(policy.currency())
                && "USD".equals(policy.priceUnit())
                && "USD".equals(policy.rawPriceUnit())
                && BigDecimal.ONE.equals(policy.priceMultiplier().stripTrailingZeros())
                && "USD".equals(quote.getCurrency())
                && Objects.equals(policy.providerSymbol(), quote.getSymbol())
                && Objects.equals(policy.providerSymbol(), meta.getSymbol())
                && quote.getPrice() != null
                && quote.getPrice().signum() > 0
                && meta.getSource() != null
                && !meta.getSource().isBlank()
                && Boolean.FALSE.equals(meta.getStale())
                && fresh(quote.getAsOf(), policy, now)
                && fresh(meta.getAsOf(), policy, now);
    }

    private boolean fresh(OffsetDateTime timestamp, Policy policy, Instant now) {
        if (timestamp == null) {
            return false;
        }
        Instant value = timestamp.toInstant();
        return !value.isBefore(now.minusSeconds(Math.min(1200, policy.quoteMaxAgeSeconds())))
                && !value.isAfter(now.plusSeconds(policy.futureSkewSeconds()));
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record Holding(String ticker, String currency, BigDecimal owned, Policy policy) {}

    private record Snapshot(BigDecimal cash, List<Holding> holdings, Instant at) {}

    private record PricedHolding(Holding holding, FauxnanceQuoteResponse quote) {}
}
