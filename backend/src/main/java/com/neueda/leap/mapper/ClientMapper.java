package com.neueda.leap.mapper;

import com.neueda.leap.domain.Client;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClientMapper {
    @Select("""
            SELECT client_id,
                   client_name,
                   advisor_id,
                   model_portfolio_id,
                   created_by_user_id,
                   created_at
            FROM clients
            WHERE client_id = #{id}
            """)
    Client getClient(@Param("id") Integer id);
}


