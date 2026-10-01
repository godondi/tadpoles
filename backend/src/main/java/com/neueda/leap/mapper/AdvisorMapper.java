package com.neueda.leap.mapper;

import com.neueda.leap.domain.Advisor;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface AdvisorMapper {
    @Insert("""
            INSERT INTO advisors (advisor_name, user_id)
            VALUES (#{advisorName}, #{userId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "advisorId", keyColumn = "advisor_id")
    int insertAdvisor(Advisor advisor);
}

