package com.neueda.leap.mapper;

import com.neueda.leap.domain.ClientSubscription;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClientSubscriptionMapper {
    @Select("""
            SELECT subscription_id AS subscriptionId,
                   client_id AS clientId,
                   model_portfolio_id AS modelPortfolioId,
                   subscribed_date AS subscribedDate,
                   ended_date AS endedDate,
                   status AS status,
                   approved_by_user_id AS approvedByUserId
            FROM client_subscriptions
            WHERE client_id = #{clientId}
            ORDER BY subscribed_date DESC, subscription_id DESC
            """)
    List<ClientSubscription> listClientSubscriptions(@Param("clientId") Integer clientId);

    @Select("""
            SELECT subscription_id AS subscriptionId,
                   client_id AS clientId,
                   model_portfolio_id AS modelPortfolioId,
                   subscribed_date AS subscribedDate,
                   ended_date AS endedDate,
                   status AS status,
                   approved_by_user_id AS approvedByUserId
            FROM client_subscriptions
            WHERE client_id = #{clientId}
              AND subscription_id = #{subscriptionId}
            """)
    ClientSubscription getClientSubscription(
            @Param("clientId") Integer clientId,
            @Param("subscriptionId") Integer subscriptionId
    );

    @Insert("""
            INSERT INTO client_subscriptions (
                client_id,
                model_portfolio_id,
                subscribed_date,
                ended_date,
                status,
                approved_by_user_id
            )
            VALUES (
                #{clientId},
                #{modelPortfolioId},
                #{subscribedDate},
                #{endedDate},
                #{status},
                #{approvedByUserId}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "subscriptionId", keyColumn = "subscription_id")
    int insertClientSubscription(ClientSubscription subscription);
}
