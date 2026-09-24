package com.neueda.leap.mapper;

import com.neueda.leap.domain.Client;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface ClientMapper {
    @Select("""
            SELECT client_id AS clientId,
                   client_name AS clientName,
                   advisor_id AS advisorId,
                   model_portfolio_id AS modelPortfolioId,
                   created_by_user_id AS createdByUserId,
                   created_at AS createdAt,
                   cash_balance AS cashBalance
            FROM clients
            WHERE client_id = #{id}
            """)
    Client getClient(@Param("id") Integer id);

    @Select("""
            SELECT client_id AS clientId,
                   client_name AS clientName,
                   advisor_id AS advisorId,
                   model_portfolio_id AS modelPortfolioId,
                   created_by_user_id AS createdByUserId,
                   created_at AS createdAt,
                   cash_balance AS cashBalance
            FROM clients
            ORDER BY client_id
            """)
    List<Client> listClients();

    @Insert("""
            INSERT INTO clients (
                client_name,
                advisor_id,
                model_portfolio_id,
                created_by_user_id,
                cash_balance
            )
            VALUES (
                #{clientName},
                #{advisorId},
                #{modelPortfolioId},
                #{createdByUserId},
                #{cashBalance}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "clientId", keyColumn = "client_id")
    int insertClient(Client client);

    @UpdateProvider(type = ClientSqlProvider.class, method = "buildUpdateClient")
    int updateClient(Client client);

    @Select("""
            SELECT cash_balance
            FROM clients
            WHERE client_id = #{id}
            """)
    BigDecimal getClientBalance(@Param("id") Integer id);

    class ClientSqlProvider {
        public String buildUpdateClient(Client client) {
            List<String> updates = new ArrayList<>();
            if (client.getClientName() != null) {
                updates.add("client_name = #{clientName}");
            }
            if (client.getAdvisorId() != null) {
                updates.add("advisor_id = #{advisorId}");
            }
            if (client.getModelPortfolioId() != null) {
                updates.add("model_portfolio_id = #{modelPortfolioId}");
            }
            if (client.getCashBalance() != null) {
                updates.add("cash_balance = #{cashBalance}");
            }
            return "UPDATE clients SET "
                    + String.join(", ", updates)
                    + " WHERE client_id = #{clientId}";
        }
    }
}
