package com.neueda.leap.dto;

public record CreateAdvisorRequestDto(
        String advisorName,
        Integer userId
) {
}
