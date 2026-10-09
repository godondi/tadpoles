package com.neueda.leap.service;

import com.neueda.leap.domain.Instrument;
import com.neueda.leap.service.impl.ExecutionQuote;

public interface ExecutionQuoteService {
    ExecutionQuote getExecutionQuote(Instrument instrument);
}
