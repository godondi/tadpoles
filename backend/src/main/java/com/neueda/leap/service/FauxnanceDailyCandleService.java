package com.neueda.leap.service;

import com.neueda.leap.domain.FauxnanceDailyCandleResponse;
import java.time.LocalDate;

public interface FauxnanceDailyCandleService {
	FauxnanceDailyCandleResponse getCandles(String symbol, LocalDate from, LocalDate to, String interval);
}