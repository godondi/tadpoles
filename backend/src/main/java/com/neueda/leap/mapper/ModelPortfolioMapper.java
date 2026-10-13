package com.neueda.leap.mapper;

import com.neueda.leap.domain.ModelPortfolio;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface ModelPortfolioMapper {
    @Select("""
            SELECT model_portfolio_id AS modelPortfolioId,
                   model_name AS modelName,
                   description AS description,
                   is_active AS isActive,
                   created_by_user_id AS createdByUserId,
                   created_at AS createdAt
            FROM model_portfolios
            WHERE model_portfolio_id = #{id}
            """)
    ModelPortfolio getModelPortfolio(@Param("id") Integer id);

    @Select("""
            SELECT model_portfolio_id AS modelPortfolioId,
                   model_name AS modelName,
                   description AS description,
                   is_active AS isActive,
                   created_by_user_id AS createdByUserId,
                   created_at AS createdAt
            FROM model_portfolios
            ORDER BY model_portfolio_id
            """)
    List<ModelPortfolio> listModelPortfolios();

    @Insert("""
            INSERT INTO model_portfolios (
                model_name,
                description,
                is_active,
                created_by_user_id
            )
            VALUES (
                #{modelName},
                #{description},
                #{isActive},
                #{createdByUserId}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "modelPortfolioId", keyColumn = "model_portfolio_id")
    int insertModelPortfolio(ModelPortfolio modelPortfolio);

    @UpdateProvider(type = ModelPortfolioSqlProvider.class, method = "buildUpdateModelPortfolio")
    int updateModelPortfolio(ModelPortfolio modelPortfolio);

    class ModelPortfolioSqlProvider {
        public String buildUpdateModelPortfolio(ModelPortfolio modelPortfolio) {
            List<String> updates = new ArrayList<>();
            if (modelPortfolio.getModelName() != null) {
                updates.add("model_name = #{modelName}");
            }
            if (modelPortfolio.getDescription() != null) {
                updates.add("description = #{description}");
            }
            if (modelPortfolio.getIsActive() != null) {
                updates.add("is_active = #{isActive}");
            }
            return "UPDATE model_portfolios SET "
                    + String.join(", ", updates)
                    + " WHERE model_portfolio_id = #{modelPortfolioId}";
        }
    }
}
