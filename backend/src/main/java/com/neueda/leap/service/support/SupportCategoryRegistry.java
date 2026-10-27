package com.neueda.leap.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.exception.WorkflowException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class SupportCategoryRegistry {
    private final Map<String, SupportCategoryHandler.Definition> categories;
    private final boolean enabled;

    public SupportCategoryRegistry(
            List<SupportCategoryHandler> handlers,
            @Value("${support.workflow-enabled:false}") boolean enabled) {
        this.enabled = enabled;
        Map<String, SupportCategoryHandler.Definition> definitions = new TreeMap<>();
        for (var handler : handlers) {
            var definition = handler.definition();
            if (definitions.put(definition.category(), definition) != null)
                throw new IllegalStateException("Duplicate support category");
        }
        categories = Collections.unmodifiableMap(definitions);
    }

    public boolean enabled() {
        return enabled;
    }

    public SupportCategoryHandler.Definition definition(String category) {
        var result = categories.get(category);
        if (result == null) throw invalid("Unknown support category");
        return result;
    }

    public Map<String, Object> policy() {
        return Map.of(
                "intakeEnabled",
                enabled,
                "schemaVersion",
                1,
                "categories",
                categories.values().stream()
                        .map(d -> Map.of("code", d.category(), "reasons", d.reasons()))
                        .toList());
    }

    public Map<String, Object> intake(String category, JsonNode version, JsonNode input) {
        if (input == null && version == null) return Map.of();
        if (!enabled)
            throw new WorkflowException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "WORKFLOW_DISABLED",
                    "Structured support intake is not enabled");
        if (version == null
                || !version.isIntegralNumber()
                || !version.canConvertToInt()
                || version.intValue() != 1
                || input == null
                || !input.isObject())
            throw invalid("Intake requires schema version 1 and an object");
        var definition = definition(category);
        Map<String, Object> result = new TreeMap<>();
        input.fields()
                .forEachRemaining(
                        field -> {
                            String name = field.getKey();
                            JsonNode value = field.getValue();
                            if (!definition.intakeFields().contains(name))
                                throw invalid("Unsupported intake field");
                            if (name.equals("exceptionId")) {
                                if (!value.isIntegralNumber()
                                        || !value.canConvertToLong()
                                        || value.longValue() < 1)
                                    throw invalid("Invalid historical reference");
                                result.put(name, value.longValue());
                            } else if (Set.of("instrumentId", "relatedOrderId").contains(name)) {
                                if (!value.isIntegralNumber()
                                        || !value.canConvertToInt()
                                        || value.intValue() < 1)
                                    throw invalid("Invalid intake reference");
                                result.put(name, value.intValue());
                            } else {
                                if (!value.isTextual())
                                    throw invalid("Intake values must be text or typed references");
                                String text = value.textValue().strip();
                                int max =
                                        Set.of("steps", "expectedBehavior", "actualBehavior")
                                                        .contains(name)
                                                ? 2000
                                                : 500;
                                if (text.isEmpty()
                                        || text.length() > max
                                        || text.chars()
                                                .anyMatch(
                                                        c ->
                                                                Character.isISOControl(c)
                                                                        && c != '\n'
                                                                        && c != '\t'))
                                    throw invalid("Invalid intake text");
                                if (Set.of("observedAt", "occurredAt").contains(name)) {
                                    try {
                                        text = Instant.parse(text).toString();
                                    } catch (DateTimeParseException failure) {
                                        throw invalid("Use an ISO UTC observation time");
                                    }
                                }
                                if (name.equals("movementId") && !canonicalUuid(text))
                                    throw invalid("Invalid movement reference");
                                if (name.equals("side") && !Set.of("BUY", "SELL").contains(text))
                                    throw invalid("Invalid attempted side");
                                if (name.equals("duplicateReference")
                                        && !text.matches("CASE-[0-9a-f-]{36}"))
                                    throw invalid("Invalid duplicate case reference");
                                result.put(name, text);
                            }
                        });
        if (!definition.reasons().contains(result.get("reason")))
            throw invalid("Choose a category issue reason");
        return result;
    }

    private static boolean canonicalUuid(String text) {
        try {
            return UUID.fromString(text).toString().equalsIgnoreCase(text);
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private static WorkflowException invalid(String message) {
        return new WorkflowException(HttpStatus.BAD_REQUEST, "INVALID_INTAKE", message);
    }
}
