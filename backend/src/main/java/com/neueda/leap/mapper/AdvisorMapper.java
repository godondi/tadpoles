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