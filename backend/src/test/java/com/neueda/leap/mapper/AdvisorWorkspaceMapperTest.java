package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.neueda.leap.mapper.AdvisorWorkspaceMapper.Scope;
import java.util.Map;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parse the actual annotations without requiring a database; PostgreSQL execution is integration-owned. */
class AdvisorWorkspaceMapperTest {
    private Configuration config;
    private final Map<String, Object> params = Map.of(
            "scope", new Scope(11, 4, 7), "search", "demo", "limit", 20, "offset", 0,
            "userId", 11, "instrumentId", 42, "recommendationId", 9,
            "status", "NONE", "reasoning", "Review", "version", 3L);

    @BeforeEach
    void parse() {
        config = new Configuration();
        config.addMapper(AdvisorWorkspaceMapper.class);
    }

    private MappedStatement statement(String method) {
        return config.getMappedStatement(AdvisorWorkspaceMapper.class.getName() + "." + method);
    }

    private String sql(String method) {
        return statement(method).getBoundSql(params).getSql();
    }

    @Test
    void everyAdvisorClientQueryRechecksEnabledRoleAndAssignedRelationship() {
        for (String method : new String[]{"clients", "clientCount", "client", "lockClient", "positions", "held",
                "guidance", "guidanceItem", "insertGuidance", "updateGuidance", "recommendations", "recommendation",
                "insertRecommendation", "updateRecommendation", "archiveRecommendation"}) {
            String sql = sql(method);
            assertTrue(sql.contains("a.advisor_id=c.advisor_id"), method);
            assertTrue(sql.contains("actor.enabled"), method);
            assertTrue(sql.contains("r.role_name='ADVISOR'"), method);
            assertTrue(sql.contains("actor.user_id=?"), method);
            assertTrue(sql.contains("a.advisor_id=?"), method);
        }
    }

    @Test
    void writesAreCasScopedAndNeverWriteFinancialOrHistoryTables() {
        for (String method : new String[]{"updateGuidance", "updateRecommendation", "archiveRecommendation"}) {
            String sql = sql(method);
            assertTrue(sql.contains("version=?"), method);
            assertTrue(sql.contains("version+1"), method);
            assertTrue(sql.contains("client_id=?"), method);
            assertTrue(sql.contains("author_user_id=?"), method);
            assertFalse(sql.contains("INSERT INTO advisor_guidance_history"));
            assertFalse(sql.contains("client_trades"));
            assertFalse(sql.contains("client_cash_accounts"));
        }
        assertTrue(sql("insertGuidance").contains("DO NOTHING"));
        assertTrue(sql("insertRecommendation").contains("WHERE archived_at IS NULL DO NOTHING"));
        assertTrue(statement("insertRecommendation").isFlushCacheRequired());
    }

    @Test
    void ownershipIsExactPostedAndPortfolioDoesNotDropQuarantinedPositions() {
        assertTrue(sql("held").contains("p.posted>0"));
        assertFalse(sql("held").contains("reserved"));
        assertTrue(sql("positions").contains("p.posted::text"));
        assertTrue(sql("positions").contains("(p.posted-p.reserved)::text"));
        assertFalse(sql("positions").contains("available_for_trading"));
        assertTrue(sql("recommendations").contains("p.posted>0"));
        assertTrue(sql("clientRecommendations").contains("p.posted>0"));
        assertTrue(sql("lockClient").endsWith("FOR UPDATE OF c"));
    }

    @Test
    void clientReadsDeriveOnlyFromEnabledVerifiedUserAndCatalogKeepsApprovedRules() {
        for (String method : new String[]{"clientForUser", "clientAdvisor", "clientGuidance", "clientRecommendations"}) {
            String sql = sql(method);
            assertTrue(sql.contains("actor.user_id=c.user_id"), method);
            assertTrue(sql.contains("actor.user_id=? AND actor.enabled"), method);
            assertTrue(sql.contains("r.role_name='CLIENT'"), method);
            assertFalse(sql.contains("c.client_id=?"), method);
        }
        for (String method : new String[]{"instruments", "instrumentCount", "approvedInstrument"}) {
            String sql = sql(method);
            assertTrue(sql.contains("r.role_name='ADVISOR'"), method);
            assertTrue(sql.contains("i.is_active AND i.currency='USD'"), method);
            assertTrue(sql.contains("i.ticker NOT LIKE 'SIM-%'"), method);
        }
        assertTrue(sql("clients").contains("strpos("));
    }
}