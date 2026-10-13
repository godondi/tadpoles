package com.neueda.leap.mapper;

import com.neueda.leap.domain.TradeSuggestion;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface TradeSuggestionMapper {
    @Select("""
            SELECT suggestion_id AS suggestionId,
                   advisor_id AS advisorId,
                   client_id AS clientId,
                   instrument_id AS instrumentId,
                   trade_type AS tradeType,
                   quantity AS quantity,
                   proposed_price AS proposedPrice,
                   suggested_at AS suggestedAt,
                   status AS status,
                   notes AS notes
            FROM trade_suggestions
            WHERE client_id = #{clientId}
            ORDER BY suggested_at DESC, suggestion_id DESC
            """)
    List<TradeSuggestion> listClientTradeSuggestions(@Param("clientId") Integer clientId);

    @Select("""
            SELECT suggestion_id AS suggestionId,
                   advisor_id AS advisorId,
                   client_id AS clientId,
                   instrument_id AS instrumentId,
                   trade_type AS tradeType,
                   quantity AS quantity,
                   proposed_price AS proposedPrice,
                   suggested_at AS suggestedAt,
                   status AS status,
                   notes AS notes
            FROM trade_suggestions
            WHERE suggestion_id = #{suggestionId}
            """)
    TradeSuggestion getTradeSuggestion(@Param("suggestionId") Integer suggestionId);

    @Insert("""
            INSERT INTO trade_suggestions (
                advisor_id,
                client_id,
                instrument_id,
                trade_type,
                quantity,
                proposed_price,
                suggested_at,
                status,
                notes
            )
            VALUES (
                #{advisorId},
                #{clientId},
                #{instrumentId},
                #{tradeType},
                #{quantity},
                #{proposedPrice},
                #{suggestedAt},
                #{status},
                #{notes}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "suggestionId", keyColumn = "suggestion_id")
    int insertTradeSuggestion(TradeSuggestion suggestion);

    @UpdateProvider(type = TradeSuggestionSqlProvider.class, method = "buildUpdateTradeSuggestion")
    int updateTradeSuggestion(TradeSuggestion suggestion);

    class TradeSuggestionSqlProvider {
        public String buildUpdateTradeSuggestion(TradeSuggestion suggestion) {
            List<String> updates = new ArrayList<>();
            if (suggestion.getStatus() != null) {
                updates.add("status = #{status}");
            }
            if (suggestion.getNotes() != null) {
                updates.add("notes = #{notes}");
            }
            return "UPDATE trade_suggestions SET "
                    + String.join(", ", updates)
                    + " WHERE suggestion_id = #{suggestionId}";
        }
    }
}

