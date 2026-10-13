package com.neueda.leap.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.domain.FauxnanceQuoteResponse;
import com.neueda.leap.service.FauxnanceQuoteService;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class FauxnanceQuoteServiceImpl implements FauxnanceQuoteService {
    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String DEFAULT_DISCLAIMER = "Educational data. Not for investment use.";
    private static final String DEFAULT_SPREAD_SOURCE = "modelled";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    @Autowired
    public FauxnanceQuoteServiceImpl(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${fauxnance.base-url:${FAUX_SERVER_URL:https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1}}")
            String baseUrl,
            @Value("${fauxnance.api-key:${FAUX_API_KEY:}}") String apiKey,
            @Value("${fauxnance.connect-timeout-seconds:10}") long connectTimeoutSeconds,
            @Value("${fauxnance.read-timeout-seconds:20}") long readTimeoutSeconds
    ) {
        this(
                restTemplateBuilder
                        .setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
                        .build(),
                objectMapper,
                baseUrl,
                apiKey
        );
    }

    FauxnanceQuoteServiceImpl(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public FauxnanceQuoteResponse getQuote(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fauxnance API key is not configured.");
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("quotes", normalizedSymbol)
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(API_KEY_HEADER, apiKey);

        try {
            ResponseEntity<FauxnanceQuoteResponse> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    FauxnanceQuoteResponse.class
            );

            FauxnanceQuoteResponse body = response.getBody();
            if (body == null || body.getData() == null || body.getMeta() == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Fauxnance returned an empty quote response.");
            }

            if (!StringUtils.hasText(body.getMeta().getDisclaimer())) {
                body.getMeta().setDisclaimer(DEFAULT_DISCLAIMER);
            }

            if (!StringUtils.hasText(body.getMeta().getSpreadSource())) {
                body.getMeta().setSpreadSource(DEFAULT_SPREAD_SOURCE);
            }

            if (body.getMeta().getStale() == null) {
                body.getMeta().setStale(false);
            }

            return body;
        } catch (HttpStatusCodeException exception) {
            throw translateUpstreamException(exception);
        }
    }

    private String normalizeSymbol(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            throw new IllegalArgumentException("Symbol is required.");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private RuntimeException translateUpstreamException(HttpStatusCodeException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolvedStatus = status == null ? HttpStatus.SERVICE_UNAVAILABLE : status;
        String message = extractErrorMessage(exception.getResponseBodyAsString(), resolvedStatus.getReasonPhrase());

        if (resolvedStatus == HttpStatus.BAD_REQUEST) {
            return new IllegalArgumentException(message);
        }

        return new ResponseStatusException(resolvedStatus, message, exception);
    }

    private String extractErrorMessage(String responseBody, String fallbackMessage) {
        if (!StringUtils.hasText(responseBody)) {
            return fallbackMessage;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode messageNode = root.path("error").path("message");
            if (messageNode.isTextual() && StringUtils.hasText(messageNode.asText())) {
                return messageNode.asText();
            }
        } catch (Exception ignored) {
            // Fall back to the HTTP status text when the upstream payload cannot be parsed.
        }

        return fallbackMessage;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Fauxnance base URL is required.");
        }

        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

