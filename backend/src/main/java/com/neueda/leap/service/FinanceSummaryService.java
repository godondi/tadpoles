package com.neueda.leap.service;

import com.neueda.leap.dto.FinanceSummaryResponseDto;
import java.time.LocalDate;

public interface FinanceSummaryService {
    FinanceSummaryResponseDto summary(LocalDate from, LocalDate to);
}
