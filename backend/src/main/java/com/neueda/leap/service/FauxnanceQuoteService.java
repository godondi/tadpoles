package com.neueda.leap.service;

import com.neueda.leap.domain.FauxnanceQuoteResponse;

public interface FauxnanceQuoteService {
    FauxnanceQuoteResponse getQuote(String symbol);
}

