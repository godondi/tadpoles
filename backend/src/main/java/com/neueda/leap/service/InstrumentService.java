package com.neueda.leap.service;

import com.neueda.leap.domain.Instrument;
import com.neueda.leap.dto.CreateInstrumentRequestDto;
import com.neueda.leap.dto.UpdateInstrumentRequestDto;
import java.util.List;

public interface InstrumentService {
    Instrument getInstrument(Integer id);
    List<Instrument> listInstruments();
    Instrument createInstrument(CreateInstrumentRequestDto request);
    Instrument updateInstrument(Integer id, UpdateInstrumentRequestDto request);
}

