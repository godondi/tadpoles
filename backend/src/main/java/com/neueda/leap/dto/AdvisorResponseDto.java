package com.neueda.leap.dto;

import com.neueda.leap.domain.Advisor;

public record AdvisorResponseDto(
        Integer advisorId,
        String advisorName,
        Integer userId
) {
    public static AdvisorResponseDto fromEntity(Advisor advisor) {
        return new AdvisorResponseDto(
                advisor.getAdvisorId(),
                advisor.getAdvisorName(),
                advisor.getUserId()
        );
    }
}
