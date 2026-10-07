package com.neueda.leap.dto;

public record CreateModelPortfolioRequestDto(
        String modelName,
        String description,
        Integer createdByUserId
) {
}
