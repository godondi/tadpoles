package com.neueda.leap.dto;

import com.neueda.leap.domain.Instrument;
import java.util.List;

public record InstrumentListResponseDto(
        List<InstrumentResponseDto> instruments
) {
    public static InstrumentListResponseDto fromEntities(List<Instrument> instruments) {
        return new InstrumentListResponseDto(
                instruments.stream().map(InstrumentResponseDto::fromEntity).toList()
        );
    }
}

