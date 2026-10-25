package com.neueda.leap.service;

import com.neueda.leap.dto.AdvisorWorkspaceDtos.*;

/** All identity arguments are verified application user IDs, never submitted advisor/client IDs. */
public interface AdvisorWorkspaceService {
    Page<AdvisorClient> clients(int userId, int page, int pageSize, String search);
    Portfolio portfolio(int userId, int clientId);
    GuidanceResponse guidance(int userId, int clientId);
    HoldingGuidance saveGuidance(int userId, int clientId, int instrumentId, GuidanceRequest request);
    RecommendationsResponse recommendations(int userId, int clientId);
    Recommendation createRecommendation(int userId, int clientId, RecommendationRequest request);
    Recommendation updateRecommendation(int userId, int clientId, int recommendationId,
                                         RecommendationUpdateRequest request);
    ArchiveResponse archiveRecommendation(int userId, int clientId, int recommendationId,
                                          ArchiveRequest request);
    Page<Instrument> instruments(int userId, int page, int pageSize, String search);
    ClientAssignment clientAdvisor(int userId);
    ClientAdvisory clientAdvisory(int userId);
}