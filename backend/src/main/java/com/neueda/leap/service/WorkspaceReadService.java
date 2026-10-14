package com.neueda.leap.service;
import com.neueda.leap.dto.WorkspaceDtos.*;
import com.neueda.leap.dto.InstrumentResponseDto;
import java.time.LocalDate;
public interface WorkspaceReadService {
    Portfolio portfolio(int clientId);
    Page<Order> orders(int clientId, int page, int pageSize, String status, String side, Integer instrumentId, LocalDate from, LocalDate to);
    OrderDetail order(int clientId, int orderId);
    Page<InstrumentResponseDto> instruments(int page, int pageSize, String search, String assetClass);
}
