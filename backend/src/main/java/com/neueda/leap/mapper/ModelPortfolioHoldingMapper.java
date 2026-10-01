package com.neueda.leap.mapper;

import com.neueda.leap.domain.ModelPortfolioHolding;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface ModelPortfolioHoldingMapper {
    @Select("""
            SELECT model_portfolio_id AS modelPortfolioId,
                   instrument_id AS instrumentId,
                   target_weight_pct AS targetWeightPct
            FROM model_portfolio_holdings
            WHERE model_portfolio_id = #{modelPortfolioId}
            ORDER BY instrument_id
            """)
    List<ModelPortfolioHolding> listModelPortfolioHoldings(@Param("modelPortfolioId") Integer modelPortfolioId);

    @Select("""
            SELECT model_portfolio_id AS modelPortfolioId,
                   instrument_id AS instrumentId,
                   target_weight_pct AS targetWeightPct
            FROM model_portfolio_holdings
            WHERE model_portfolio_id = #{modelPortfolioId}
              AND instrument_id = #{instrumentId}
            """)
    ModelPortfolioHolding getModelPortfolioHolding(
            @Param("modelPortfolioId") Integer modelPortfolioId,
            @Param("instrumentId") Integer instrumentId
    );

    @Insert("""
            INSERT INTO model_portfolio_holdings (
                model_portfolio_id,
                instrument_id,
                target_weight_pct
            )
            VALUES (
                #{modelPortfolioId},
                #{instrumentId},
                #{targetWeightPct}
            )
            """)
    int insertModelPortfolioHolding(ModelPortfolioHolding holding);

    @UpdateProvider(type = ModelPortfolioHoldingSqlProvider.class, method = "buildUpdateModelPortfolioHolding")
    int updateModelPortfolioHolding(ModelPortfolioHolding holding);

    class ModelPortfolioHoldingSqlProvider {
        public String buildUpdateModelPortfolioHolding(ModelPortfolioHolding holding) {
            List<String> updates = new ArrayList<>();
            if (holding.getTargetWeightPct() != null) {
                updates.add("target_weight_pct = #{targetWeightPct}");
            }
            return "UPDATE model_portfolio_holdings SET "
                    + String.join(", ", updates)
                    + " WHERE model_portfolio_id = #{modelPortfolioId} AND instrument_id = #{instrumentId}";
        }
    }
}
