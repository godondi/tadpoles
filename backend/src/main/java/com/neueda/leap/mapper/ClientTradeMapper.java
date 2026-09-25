package com.neueda.leap.mapper;

import com.neueda.leap.domain.ClientTrade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface ClientTradeMapper {
    @Select("""
            SELECT trade_id AS tradeId,
                   client_id AS clientId,
                   instrument_id AS instrumentId,
                   submitted_by_user_id AS submittedByUserId,
                   approved_by_user_id AS approvedByUserId,
                   trade_type AS tradeType,
                   quantity AS quantity,
                   price AS price,
                   trade_date AS tradeDate,
                   status AS status,
                   executed_at AS executedAt,
                   reason AS reason
            FROM client_trades
            WHERE client_id = #{clientId}
            ORDER BY trade_date DESC, trade_id DESC
            """)
    List<ClientTrade> listClientTrades(@Param("clientId") Integer clientId);

    @Select("""
            SELECT trade_id AS tradeId,
                   client_id AS clientId,
                   instrument_id AS instrumentId,
                   submitted_by_user_id AS submittedByUserId,
                   approved_by_user_id AS approvedByUserId,
                   trade_type AS tradeType,
                   quantity AS quantity,
                   price AS price,
                   trade_date AS tradeDate,
                   status AS status,
                   executed_at AS executedAt,
                   reason AS reason
            FROM client_trades
            WHERE client_id = #{clientId}
              AND trade_id = #{tradeId}
            """)
    ClientTrade getClientTrade(@Param("clientId") Integer clientId, @Param("tradeId") Integer tradeId);

    @Insert("""
            INSERT INTO client_trades (
                client_id,
                instrument_id,
                submitted_by_user_id,
                approved_by_user_id,
                trade_type,
                quantity,
                price,
                trade_date,
                status,
                reason
            )
            VALUES (
                #{clientId},
                #{instrumentId},
                #{submittedByUserId},
                #{approvedByUserId},
                #{tradeType},
                #{quantity},
                #{price},
                #{tradeDate},
                #{status},
                #{reason}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "tradeId", keyColumn = "trade_id")
    int insertClientTrade(ClientTrade trade);

    @UpdateProvider(type = ClientTradeSqlProvider.class, method = "buildUpdateClientTrade")
    int updateClientTrade(ClientTrade trade);

    @Select("""
            SELECT trade_id AS tradeId,
                   client_id AS clientId,
                   instrument_id AS instrumentId,
                   submitted_by_user_id AS submittedByUserId,
                   approved_by_user_id AS approvedByUserId,
                   trade_type AS tradeType,
                   quantity AS quantity,
                   price AS price,
                   trade_date AS tradeDate,
                   status AS status,
                   executed_at AS executedAt,
                   reason AS reason
            FROM client_trades
            WHERE trade_id = #{tradeId}
            FOR UPDATE
            """)
    ClientTrade getTradeForUpdate(@Param("tradeId") Integer tradeId);

    @Update("""
            UPDATE client_trades
            SET approved_by_user_id = #{approvedByUserId},
                price = #{price},
                status = 'EXECUTED',
                executed_at = #{executedAt},
                reason = #{reason}
            WHERE trade_id = #{tradeId}
            """)
    int markTradeExecuted(
            @Param("tradeId") Integer tradeId,
            @Param("approvedByUserId") Integer approvedByUserId,
            @Param("price") BigDecimal price,
            @Param("executedAt") java.time.LocalDateTime executedAt,
            @Param("reason") String reason
    );

    class ClientTradeSqlProvider {
        public String buildUpdateClientTrade(ClientTrade trade) {
            List<String> updates = new ArrayList<>();
            if (trade.getSubmittedByUserId() != null) {
                updates.add("submitted_by_user_id = #{submittedByUserId}");
            }
            if (trade.getApprovedByUserId() != null) {
                updates.add("approved_by_user_id = #{approvedByUserId}");
            }
            if (trade.getStatus() != null) {
                updates.add("status = #{status}");
            }
            if (trade.getExecutedAt() != null) {
                updates.add("executed_at = #{executedAt}");
            }
            if (trade.getReason() != null) {
                updates.add("reason = #{reason}");
            }
            return "UPDATE client_trades SET "
                    + String.join(", ", updates)
                    + " WHERE client_id = #{clientId} AND trade_id = #{tradeId}";
        }
    }
}

