package com.neueda.leap.dto;

public record UpdateModelPortfolioRequestDto(
        String modelName,
        String description,
        Boolean isActive
) {
}
