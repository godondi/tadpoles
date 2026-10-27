package com.neueda.leap.mapper;

import com.neueda.leap.domain.Advisor;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface AdvisorMapper {
    @Select("""
            SELECT advisor_id AS advisorId,
                   advisor_name AS advisorName,
                   user_id AS userId
            FROM advisors
            WHERE advisor_id = #{id}
            """)
    Advisor getAdvisor(@Param("id") Integer id);

    @Select("""
            SELECT advisor_id AS advisorId,
                   advisor_name AS advisorName,
                   user_id AS userId
            FROM advisors
            ORDER BY advisor_id
            """)
    List<Advisor> listAdvisors();

    /** Serialize onboarding assignment so concurrent completions see the latest client counts. */
    @Select("SELECT (pg_advisory_xact_lock(746532910::bigint) IS NULL)::integer")
    Integer lockAdvisorAssignmentBucket();

    /**
     * Select an enabled, linked ADVISOR account with the fewest assigned clients. Demo fixtures
     * remain a fallback only; real staff advisors win ties before the stable advisor-id tie break.
     */
    @Select("""
            SELECT a.advisor_id
            FROM advisors a
            JOIN users u ON u.user_id = a.user_id AND u.enabled
            JOIN user_roles ur ON ur.user_id = u.user_id
            JOIN roles r ON r.role_id = ur.role_id AND r.role_name = 'ADVISOR'
            ORDER BY CASE WHEN u.username LIKE 'demo.%' THEN 1 ELSE 0 END, (
                SELECT COUNT(*)
                FROM clients c
                WHERE c.advisor_id = a.advisor_id
            ), a.advisor_id
            LIMIT 1
            """)
    Integer findLeastAssignedAdvisorId();

    @Insert("""
            INSERT INTO advisors (
                advisor_name,
                user_id
            )
            VALUES (
                #{advisorName},
                #{userId}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "advisorId", keyColumn = "advisor_id")
    int insertAdvisor(Advisor advisor);

    @UpdateProvider(type = AdvisorSqlProvider.class, method = "buildUpdateAdvisor")
    int updateAdvisor(Advisor advisor);

    class AdvisorSqlProvider {
        public String buildUpdateAdvisor(Advisor advisor) {
            List<String> updates = new ArrayList<>();
            if (advisor.getAdvisorName() != null) {
                updates.add("advisor_name = #{advisorName}");
            }
            if (advisor.getUserId() != null) {
                updates.add("user_id = #{userId}");
            }
            return "UPDATE advisors SET "
                    + String.join(", ", updates)
                    + " WHERE advisor_id = #{advisorId}";
        }
    }
}
