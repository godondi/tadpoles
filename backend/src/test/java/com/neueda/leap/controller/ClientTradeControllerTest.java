package com.neueda.leap.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.dto.OrderFillResponseDto;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.ClientTradeService;
import com.neueda.leap.service.OrderFillService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClientTradeController.class)
@Import(GlobalExceptionHandler.class)
class ClientTradeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientTradeService clientTradeService;
    @MockBean
    private OrderFillService orderFillService;

    @Test
    void listTradesReturnsJsonResponse() throws Exception {
        when(clientTradeService.listClientTrades(7)).thenReturn(List.of(buildTrade()));

        mockMvc.perform(get("/api/clients/7/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trades[0].tradeId").value(21))
                .andExpect(jsonPath("$.trades[0].status").value("APPROVED"));
    }

    @Test
    void createTradeReturnsCreatedResponse() throws Exception {
        when(clientTradeService.createTrade(org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.any()))
                .thenReturn(buildTrade());

        mockMvc.perform(post("/api/clients/7/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrumentId": 11,
                                  "submittedByUserId": 14,
                                  "tradeType": "BUY",
                                  "quantity": 2.5,
                                  "price": 110.25,
                                  "tradeDate": "2026-09-24",
                                  "reason": "Add position"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradeId").value(21))
                .andExpect(jsonPath("$.tradeType").value("BUY"));
    }

    @Test
    void getTradeReturnsJsonResponse() throws Exception {
        when(clientTradeService.getTrade(7, 21)).thenReturn(buildTrade());

        mockMvc.perform(get("/api/clients/7/trades/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(21))
                .andExpect(jsonPath("$.instrumentId").value(11));
    }

    @Test
    void updateTradeReturnsJsonResponse() throws Exception {
        ClientTrade trade = buildTrade();
        trade.setStatus("EXECUTED");
        when(clientTradeService.updateTrade(org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.eq(21), org.mockito.ArgumentMatchers.any()))
                .thenReturn(trade);

        mockMvc.perform(patch("/api/clients/7/trades/21")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "EXECUTED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));
    }

    @Test
    void fillTradeReturnsOrderFillSummary() throws Exception {
        OrderFillResponseDto response = new OrderFillResponseDto(
                7,
                new BigDecimal("799.50"),
                13,
                new BigDecimal("9.500000"),
                21,
                "EXECUTED",
                LocalDateTime.of(2026, 9, 24, 11, 0)
        );
        when(orderFillService.fillTrade(org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.eq(21), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/clients/7/trades/21/fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvedByUserId": 1,
                                  "price": 110.25,
                                  "executedAt": "2026-09-24T11:00:00",
                                  "reason": "Executed on market"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(7))
                .andExpect(jsonPath("$.cashBalance").value(799.5))
                .andExpect(jsonPath("$.tradeStatus").value("EXECUTED"));
    }

    @Test
    void getTradeReturnsBadRequestForInvalidClientId() throws Exception {
        when(clientTradeService.getTrade(0, 21))
                .thenThrow(new IllegalArgumentException("Client id must be a positive integer."));

        mockMvc.perform(get("/api/clients/0/trades/21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Client id must be a positive integer."));
    }

    private ClientTrade buildTrade() {
        ClientTrade trade = new ClientTrade();
        trade.setTradeId(21);
        trade.setClientId(7);
        trade.setInstrumentId(11);
        trade.setSubmittedByUserId(14);
        trade.setApprovedByUserId(1);
        trade.setTradeType("BUY");
        trade.setQuantity(new BigDecimal("2.500000"));
        trade.setPrice(new BigDecimal("110.25"));
        trade.setTradeDate(LocalDate.of(2026, 9, 24));
        trade.setStatus("APPROVED");
        trade.setExecutedAt(LocalDateTime.of(2026, 9, 24, 10, 15));
        trade.setReason("Add position");
        return trade;
    }
}

