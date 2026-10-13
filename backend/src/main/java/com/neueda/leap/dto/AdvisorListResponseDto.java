package com.neueda.leap.dto;

import com.neueda.leap.domain.Advisor;
import java.util.List;

public record AdvisorListResponseDto(
        List<AdvisorResponseDto> advisors
) {
    public static AdvisorListResponseDto fromEntities(List<Advisor> advisors) {
        return new AdvisorListResponseDto(
                advisors.stream().map(AdvisorResponseDto::fromEntity).toList()
        );
    }
}
