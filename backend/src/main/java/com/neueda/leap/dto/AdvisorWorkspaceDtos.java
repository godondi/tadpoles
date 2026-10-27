package com.neueda.leap.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

/** Workspace-only wire records; quantities remain exact decimal strings. */
public final class AdvisorWorkspaceDtos {
    private AdvisorWorkspaceDtos() {}

    public record Page<T>(List<T> items, int page, int pageSize, long total) {}
    public record AdvisorClient(int clientId, String displayName, String clientReference,
                                String assignmentStatus, Instant updatedAt) {}
    public record Position(int instrumentId, String ticker, String name, String currency,
                           String owned, String reserved, String available, Instant updatedAt) {}
    public record Portfolio(int clientId, String clientName, String clientReference,
                            List<Position> currentPositions, Instant retrievedAt) {}
    public record HoldingGuidance(int instrumentId, String status, String reasoning,
                                  String authorName, long version, Instant updatedAt) {}
    public record GuidanceResponse(List<HoldingGuidance> items, Instant asOf) {}
        public record GuidanceRequest(String status, String reasoning,
            @JsonDeserialize(using = StrictVersion.class) Long expectedVersion) {}
    public record Instrument(int instrumentId, String ticker, String instrumentName,
                             String currency, boolean isActive) {}
    public record Recommendation(int recommendationId, int instrumentId, String ticker,
                                 String instrumentName, String reasoning, long version,
                                 Instant updatedAt) {}
    public record RecommendationsResponse(List<Recommendation> items, Instant asOf) {}
        public record RecommendationRequest(
            @JsonDeserialize(using = StrictId.class) Integer instrumentId, String reasoning) {}
        public record RecommendationUpdateRequest(String reasoning,
            @JsonDeserialize(using = StrictVersion.class) Long expectedVersion) {}
        public record ArchiveRequest(@JsonDeserialize(using = StrictVersion.class) Long expectedVersion) {}
    public record ArchiveResponse(int recommendationId, long version) {}
    public record AssignedAdvisor(int advisorId, String name) {}
    public record ClientAssignment(String assignmentStatus, AssignedAdvisor advisor,
                                   Instant assignedAt) {}
    public record ClientGuidance(int instrumentId, String status, String reasoning,
                                 String authorName, Instant updatedAt) {}
    public record ClientRecommendation(int recommendationId, int instrumentId, String ticker,
                                       String instrumentName, String reasoning, Instant updatedAt) {}
    public record ClientAdvisory(List<ClientGuidance> guidance,
                                 List<ClientRecommendation> recommendations,
                                 Instant asOf, String source) {}

    /** Do not inherit Jackson's default fractional/string-to-integer coercion for record IDs. */
    public static final class StrictId extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }

    public static final class StrictVersion extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Long) context.handleUnexpectedToken(Long.class, parser);
            }
            return parser.getLongValue();
        }
    }
}