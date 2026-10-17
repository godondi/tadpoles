package com.neueda.leap.service.impl;

import static com.neueda.leap.trading.TradingData.*;
import static com.neueda.leap.trading.TradingRules.decimal;

import com.neueda.leap.mapper.TradingMapper;
import com.neueda.leap.trading.DisplayRates;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DisplayCurrencyService {
    private final TradingMapper db;
    private final RestTemplate http;
    private final Clock clock;
    private final String url;
    private String cached;
    private Instant fetched;

    public DisplayCurrencyService(
            TradingMapper db,
            RestTemplateBuilder builder,
            Clock clock,
            @Value(
                            "${display.ecb-url:https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml}")
                    String url) {
        this.db = db;
        this.clock = clock;
        this.url = url;
        this.http =
                builder.setConnectTimeout(Duration.ofSeconds(3))
                        .setReadTimeout(Duration.ofSeconds(5))
                        .build();
    }

    public Object estimate(int client, String currency) {
        if (!Set.of("USD", "GBP", "INR", "EUR").contains(currency))
            throw new IllegalArgumentException("Unsupported display currency");
        var cash = db.cash(client, "USD");
        if (cash == null || !Boolean.TRUE.equals(cash.get("available_for_trading")))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "USD account is unavailable for display conversion");
        var usd = amount(cash, "posted").subtract(amount(cash, "reserved"));
        if (currency.equals("USD"))
            return map(
                    "currency",
                    "USD",
                    "usd",
                    decimal(usd),
                    "estimatedValue",
                    decimal(usd),
                    "checkedAt",
                    clock.instant());
        try {
            var estimate =
                    DisplayRates.convert(
                            rates(),
                            currency,
                            usd,
                            LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
            return map(
                    "currency",
                    currency,
                    "usd",
                    decimal(usd),
                    "estimatedValue",
                    estimate.amount().toPlainString(),
                    "targetPerUsd",
                    decimal(estimate.rate()),
                    "rateDate",
                    estimate.rateDate(),
                    "checkedAt",
                    clock.instant(),
                    "source",
                    "ECB euro foreign exchange reference rates",
                    "sourceUrl",
                    "https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html",
                    "note",
                    "Available cash estimate only; not position valuation or a conversion of funds."
                            + " All trading and withdrawals remain USD.");
        } catch (RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Display estimate unavailable: reference rates could not be verified. Your USD"
                            + " balance is unchanged.");
        }
    }

    private synchronized String rates() {
        if (url.isBlank()) throw new IllegalStateException();
        if (cached == null
                || fetched == null
                || fetched.isBefore(clock.instant().minusSeconds(300))) {
            cached = http.getForObject(url, String.class);
            fetched = clock.instant();
        }
        return cached;
    }
}
