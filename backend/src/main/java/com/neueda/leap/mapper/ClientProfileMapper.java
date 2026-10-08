package com.neueda.leap.mapper;

import com.neueda.leap.domain.ClientProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ClientProfileMapper {
    @Select("""
            SELECT client_profile_id AS clientProfileId,
                   user_id AS userId,
                   client_id AS clientId,
                   phone,
                   date_of_birth AS dateOfBirth,
                   address_line_1 AS addressLine1,
                   address_line_2 AS addressLine2,
                   city,
                   state,
                   postal_code AS postalCode,
                   country,
                   employment_status AS employmentStatus,
                   net_worth AS netWorth,
                   risk_tolerance AS riskTolerance,
                   investment_objective AS investmentObjective,
                   preferred_contact_method AS preferredContactMethod,
                   paperless_statements AS paperlessStatements,
                   marketing_opt_in AS marketingOptIn,
                   onboarding_complete AS onboardingComplete,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM client_profiles
            WHERE user_id = #{userId}
            """)
    ClientProfile findByUserId(@Param("userId") Integer userId);

    @Insert("""
            INSERT INTO client_profiles (
                user_id,
                client_id,
                phone,
                date_of_birth,
                address_line_1,
                address_line_2,
                city,
                state,
                postal_code,
                country,
                employment_status,
                net_worth,
                risk_tolerance,
                investment_objective,
                preferred_contact_method,
                paperless_statements,
                marketing_opt_in,
                onboarding_complete,
                created_at,
                updated_at
            )
            VALUES (
                #{userId},
                #{clientId},
                #{phone},
                #{dateOfBirth},
                #{addressLine1},
                #{addressLine2},
                #{city},
                #{state},
                #{postalCode},
                #{country},
                #{employmentStatus},
                #{netWorth},
                #{riskTolerance},
                #{investmentObjective},
                #{preferredContactMethod},
                #{paperlessStatements},
                #{marketingOptIn},
                #{onboardingComplete},
                #{createdAt},
                #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "clientProfileId", keyColumn = "client_profile_id")
    int insertClientProfile(ClientProfile clientProfile);

    @Update("""
            UPDATE client_profiles
            SET client_id = #{clientId},
                phone = #{phone},
                date_of_birth = #{dateOfBirth},
                address_line_1 = #{addressLine1},
                address_line_2 = #{addressLine2},
                city = #{city},
                state = #{state},
                postal_code = #{postalCode},
                country = #{country},
                employment_status = #{employmentStatus},
                net_worth = #{netWorth},
                risk_tolerance = #{riskTolerance},
                investment_objective = #{investmentObjective},
                preferred_contact_method = #{preferredContactMethod},
                paperless_statements = #{paperlessStatements},
                marketing_opt_in = #{marketingOptIn},
                onboarding_complete = #{onboardingComplete},
                updated_at = CURRENT_TIMESTAMP
            WHERE client_profile_id = #{clientProfileId}
            """)
    int updateClientProfile(ClientProfile clientProfile);
}

