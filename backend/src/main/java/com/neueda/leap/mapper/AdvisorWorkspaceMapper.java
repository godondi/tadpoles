package com.neueda.leap.mapper;

import com.neueda.leap.dto.AdvisorWorkspaceDtos.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** Every advisor query rechecks the persisted enabled identity, role and assignment. */
@Mapper
public interface AdvisorWorkspaceMapper {
    record Scope(int userId, int advisorId, int clientId) {}

    String ADVISOR_ROLE = " EXISTS (SELECT 1 FROM user_roles ur JOIN roles r USING(role_id)"
            + " WHERE ur.user_id=actor.user_id AND r.role_name='ADVISOR') ";
    String JOINS = " FROM clients c JOIN advisors a ON a.advisor_id=c.advisor_id"
            + " JOIN users actor ON actor.user_id=a.user_id AND actor.enabled ";
    String WHERE = " WHERE actor.user_id=#{scope.userId} AND a.advisor_id=#{scope.advisorId} AND "
            + ADVISOR_ROLE;
    String CLIENT = " AND c.client_id=#{scope.clientId} ";
    String IDENTITY = "SELECT c.client_id AS clientId, COALESCE(cu.display_name,c.client_name) AS displayName,"
            + " 'CLIENT-' || c.client_id AS clientReference, 'ASSIGNED' AS assignmentStatus,"
            + " COALESCE(cu.updated_at,c.created_at) AT TIME ZONE 'UTC' AS updatedAt ";
    String SEARCH = " AND (strpos(lower(COALESCE(cu.display_name,c.client_name)),#{search})>0"
            + " OR strpos(lower('CLIENT-' || c.client_id),#{search})>0) ";
    String CATALOG = " i.is_active AND i.currency='USD' AND i.ticker NOT LIKE 'SIM-%'"
            + " AND i.ticker NOT IN ('CASH','USD','GBP','INR','EUR') ";
    String CATALOG_SCOPE = " EXISTS (SELECT 1 FROM advisors a JOIN users actor ON actor.user_id=a.user_id"
            + " WHERE actor.enabled AND actor.user_id=#{scope.userId} AND a.advisor_id=#{scope.advisorId}"
            + " AND " + ADVISOR_ROLE + ") ";
    String GUIDANCE_COLUMNS = "g.instrument_id AS instrumentId,g.status,g.reasoning,"
            + " author.advisor_name AS authorName,g.version,g.updated_at AS updatedAt ";
    String GUIDANCE_JOINS = " JOIN advisor_guidance g ON g.client_id=c.client_id"
            + " LEFT JOIN advisors author ON author.advisor_id=g.author_advisor_id ";
    String REC_COLUMNS = "rec.recommendation_id AS recommendationId,rec.instrument_id AS instrumentId,"
            + " i.ticker,i.instrument_name AS instrumentName,rec.reasoning,rec.version,rec.updated_at AS updatedAt ";
    String REC_JOINS = " JOIN advisor_recommendations rec ON rec.client_id=c.client_id"
            + " JOIN instruments i ON i.instrument_id=rec.instrument_id ";
    String NOT_HELD = " NOT EXISTS (SELECT 1 FROM client_positions p WHERE p.client_id=c.client_id"
            + " AND p.instrument_id=rec.instrument_id AND p.posted>0) ";

    @Select("SELECT a.advisor_id FROM advisors a JOIN users actor ON actor.user_id=a.user_id"
            + " WHERE actor.enabled AND actor.user_id=#{userId} AND " + ADVISOR_ROLE)
    Integer advisorForUser(@Param("userId") int userId);

    @Select(IDENTITY + JOINS + " LEFT JOIN users cu ON cu.user_id=c.user_id " + WHERE + SEARCH
            + " ORDER BY c.client_id LIMIT #{limit} OFFSET #{offset}")
    List<AdvisorClient> clients(@Param("scope") Scope scope, @Param("search") String search,
                                @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(*) " + JOINS + " LEFT JOIN users cu ON cu.user_id=c.user_id " + WHERE + SEARCH)
    long clientCount(@Param("scope") Scope scope, @Param("search") String search);

    @Select(IDENTITY + JOINS + " LEFT JOIN users cu ON cu.user_id=c.user_id " + WHERE + CLIENT)
    AdvisorClient client(@Param("scope") Scope scope);

    @Select("SELECT c.client_id " + JOINS + WHERE + CLIENT + " FOR UPDATE OF c")
    Integer lockClient(@Param("scope") Scope scope);

    @Select("SELECT p.instrument_id AS instrumentId,i.ticker,i.instrument_name AS name,i.currency,"
            + " p.posted::text AS owned,p.reserved::text AS reserved,(p.posted-p.reserved)::text AS available,"
            + " p.updated_at AS updatedAt " + JOINS
            + " JOIN client_positions p ON p.client_id=c.client_id"
            + " JOIN instruments i ON i.instrument_id=p.instrument_id " + WHERE + CLIENT
            + " AND p.posted>0 ORDER BY i.ticker,p.instrument_id")
    List<Position> positions(@Param("scope") Scope scope);

    @Select("SELECT EXISTS (SELECT 1 " + JOINS
            + " JOIN client_positions p ON p.client_id=c.client_id " + WHERE + CLIENT
            + " AND p.instrument_id=#{instrumentId} AND p.posted>0)")
    boolean held(@Param("scope") Scope scope, @Param("instrumentId") int instrumentId);

    @Select("SELECT " + GUIDANCE_COLUMNS + JOINS + GUIDANCE_JOINS + WHERE + CLIENT
            + " ORDER BY g.instrument_id")
    List<HoldingGuidance> guidance(@Param("scope") Scope scope);

    @Select("SELECT " + GUIDANCE_COLUMNS + JOINS + GUIDANCE_JOINS + WHERE + CLIENT
            + " AND g.instrument_id=#{instrumentId}")
    HoldingGuidance guidanceItem(@Param("scope") Scope scope, @Param("instrumentId") int instrumentId);

    // Conflict-free create-only insert; history is appended exclusively by projection triggers.
    @Insert("INSERT INTO advisor_guidance(client_id,instrument_id,status,reasoning,author_advisor_id,author_user_id)"
            + " SELECT c.client_id,#{instrumentId},#{status},#{reasoning},a.advisor_id,actor.user_id "
            + JOINS + WHERE + CLIENT + " ON CONFLICT (client_id,instrument_id) DO NOTHING")
    int insertGuidance(@Param("scope") Scope scope, @Param("instrumentId") int instrumentId,
                        @Param("status") String status, @Param("reasoning") String reasoning);

    @Update("UPDATE advisor_guidance g SET status=#{status},reasoning=#{reasoning},"
            + " author_advisor_id=#{scope.advisorId},author_user_id=#{scope.userId},"
            + " version=g.version+1,updated_at=clock_timestamp() WHERE g.client_id=#{scope.clientId}"
            + " AND g.instrument_id=#{instrumentId} AND g.version=#{version}"
            + " AND EXISTS (SELECT 1 " + JOINS + WHERE + CLIENT + " AND c.client_id=g.client_id)")
    int updateGuidance(@Param("scope") Scope scope, @Param("instrumentId") int instrumentId,
                        @Param("status") String status, @Param("reasoning") String reasoning,
                        @Param("version") long version);

    @Select("SELECT " + REC_COLUMNS + JOINS + REC_JOINS + WHERE + CLIENT
            + " AND rec.archived_at IS NULL AND " + NOT_HELD + " ORDER BY rec.recommendation_id")
    List<Recommendation> recommendations(@Param("scope") Scope scope);

    // Includes archived/now-held records so stale versions yield 409, never a successful overwrite.
    @Select("SELECT " + REC_COLUMNS + JOINS + REC_JOINS + WHERE + CLIENT
            + " AND rec.recommendation_id=#{recommendationId}")
    Recommendation recommendation(@Param("scope") Scope scope, @Param("recommendationId") int recommendationId);

    @Select(value = "INSERT INTO advisor_recommendations(client_id,instrument_id,reasoning,author_advisor_id,author_user_id)"
            + " SELECT c.client_id,#{instrumentId},#{reasoning},a.advisor_id,actor.user_id " + JOINS + WHERE + CLIENT
            + " ON CONFLICT (client_id,instrument_id) WHERE archived_at IS NULL DO NOTHING RETURNING recommendation_id",
            affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    Integer insertRecommendation(@Param("scope") Scope scope, @Param("instrumentId") int instrumentId,
                                  @Param("reasoning") String reasoning);

    @Update("UPDATE advisor_recommendations rec SET reasoning=#{reasoning},"
            + " author_advisor_id=#{scope.advisorId},author_user_id=#{scope.userId},"
            + " version=rec.version+1,updated_at=clock_timestamp() WHERE rec.client_id=#{scope.clientId}"
            + " AND rec.recommendation_id=#{recommendationId} AND rec.version=#{version} AND rec.archived_at IS NULL"
            + " AND EXISTS (SELECT 1 " + JOINS + WHERE + CLIENT + " AND c.client_id=rec.client_id)")
    int updateRecommendation(@Param("scope") Scope scope, @Param("recommendationId") int recommendationId,
                              @Param("reasoning") String reasoning, @Param("version") long version);

    @Update("UPDATE advisor_recommendations rec SET archived_at=clock_timestamp(),"
            + " author_advisor_id=#{scope.advisorId},author_user_id=#{scope.userId},"
            + " version=rec.version+1,updated_at=clock_timestamp() WHERE rec.client_id=#{scope.clientId}"
            + " AND rec.recommendation_id=#{recommendationId} AND rec.version=#{version} AND rec.archived_at IS NULL"
            + " AND EXISTS (SELECT 1 " + JOINS + WHERE + CLIENT + " AND c.client_id=rec.client_id)")
    int archiveRecommendation(@Param("scope") Scope scope, @Param("recommendationId") int recommendationId,
                               @Param("version") long version);

    @Select("SELECT i.instrument_id AS instrumentId,i.ticker,i.instrument_name AS instrumentName,"
            + " i.currency,i.is_active AS isActive FROM instruments i WHERE " + CATALOG + " AND " + CATALOG_SCOPE
            + " AND (strpos(lower(i.ticker),#{search})>0 OR strpos(lower(i.instrument_name),#{search})>0)"
            + " ORDER BY i.ticker,i.instrument_id LIMIT #{limit} OFFSET #{offset}")
    List<Instrument> instruments(@Param("scope") Scope scope, @Param("search") String search,
                                 @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(*) FROM instruments i WHERE " + CATALOG + " AND " + CATALOG_SCOPE
            + " AND (strpos(lower(i.ticker),#{search})>0 OR strpos(lower(i.instrument_name),#{search})>0)")
    long instrumentCount(@Param("scope") Scope scope, @Param("search") String search);

    @Select("SELECT EXISTS (SELECT 1 FROM instruments i WHERE i.instrument_id=#{instrumentId} AND "
            + CATALOG + " AND " + CATALOG_SCOPE + ")")
    boolean approvedInstrument(@Param("scope") Scope scope, @Param("instrumentId") int instrumentId);

    String OWN_JOINS = " FROM clients c JOIN users actor ON actor.user_id=c.user_id ";
    String OWN_WHERE = " WHERE actor.user_id=#{userId} AND actor.enabled AND EXISTS"
            + " (SELECT 1 FROM user_roles ur JOIN roles r USING(role_id)"
            + " WHERE ur.user_id=actor.user_id AND r.role_name='CLIENT') ";

    @Select("SELECT c.client_id " + OWN_JOINS + OWN_WHERE)
    Integer clientForUser(@Param("userId") int userId);

    @Select("SELECT a.advisor_id AS advisorId,a.advisor_name AS name " + OWN_JOINS
            + " JOIN advisors a ON a.advisor_id=c.advisor_id " + OWN_WHERE)
    AssignedAdvisor clientAdvisor(@Param("userId") int userId);

    @Select("SELECT g.instrument_id AS instrumentId,g.status,g.reasoning,author.advisor_name AS authorName,"
            + " g.updated_at AS updatedAt " + OWN_JOINS + GUIDANCE_JOINS + OWN_WHERE
            + " ORDER BY g.instrument_id")
    List<ClientGuidance> clientGuidance(@Param("userId") int userId);

    @Select("SELECT rec.recommendation_id AS recommendationId,rec.instrument_id AS instrumentId,"
            + " i.ticker,i.instrument_name AS instrumentName,rec.reasoning,rec.updated_at AS updatedAt "
            + OWN_JOINS + REC_JOINS + OWN_WHERE + " AND rec.archived_at IS NULL AND " + NOT_HELD
            + " ORDER BY rec.recommendation_id")
    List<ClientRecommendation> clientRecommendations(@Param("userId") int userId);
}