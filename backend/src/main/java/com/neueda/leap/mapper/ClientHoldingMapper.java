package com.neueda.leap.mapper;

import com.neueda.leap.domain.ClientHolding;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface ClientHoldingMapper {
    @Select("""
            SELECT holding_id AS holdingId,
                   client_id AS clientId,
                   instrument_id AS instrumentId,
                   quantity AS quantity,
                   as_of_date AS asOfDate
            FROM client_holdings
            WHERE client_id = #{clientId}
            ORDER BY as_of_date DESC, holding_id DESC
            """)
    List<ClientHolding> listClientHoldings(@Param("clientId") Integer clientId);

    @Select("""
            SELECT holding_id AS holdingId,
                   client_id AS clientId,
                   instrument_id AS instrumentId,
                   quantity AS quantity,
                   as_of_date AS asOfDate
            FROM client_holdings
            WHERE client_id = #{clientId}
              AND holding_id = #{holdingId}
            """)
    ClientHolding getClientHolding(@Param("clientId") Integer clientId, @Param("holdingId") Integer holdingId);

    @Insert("""
            INSERT INTO client_holdings (
                client_id,
                instrument_id,
                quantity,
                as_of_date
            )
            VALUES (
                #{clientId},
                #{instrumentId},
                #{quantity},
                #{asOfDate}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "holdingId", keyColumn = "holding_id")
    int insertClientHolding(ClientHolding holding);

    @UpdateProvider(type = ClientHoldingSqlProvider.class, method = "buildUpdateClientHolding")
    int updateClientHolding(ClientHolding holding);

    @Delete("""
            DELETE FROM client_holdings
            WHERE client_id = #{clientId}
              AND holding_id = #{holdingId}
            """)
    int deleteClientHolding(@Param("clientId") Integer clientId, @Param("holdingId") Integer holdingId);

    @Select("""
            SELECT holding_id AS holdingId,
                   client_id AS clientId,
                   instrument_id AS instrumentId,
                   quantity AS quantity,
                   as_of_date AS asOfDate
            FROM client_holdings
            WHERE client_id = #{clientId}
              AND instrument_id = #{instrumentId}
            ORDER BY as_of_date DESC, holding_id DESC
            LIMIT 1
            FOR UPDATE
            """)
    ClientHolding getLatestHoldingForUpdate(@Param("clientId") Integer clientId, @Param("instrumentId") Integer instrumentId);

    class ClientHoldingSqlProvider {
        public String buildUpdateClientHolding(ClientHolding holding) {
            List<String> updates = new ArrayList<>();
            if (holding.getQuantity() != null) {
                updates.add("quantity = #{quantity}");
            }
            if (holding.getAsOfDate() != null) {
                updates.add("as_of_date = #{asOfDate}");
            }
            return "UPDATE client_holdings SET "
                    + String.join(", ", updates)
                    + " WHERE client_id = #{clientId} AND holding_id = #{holdingId}";
        }
    }
}


