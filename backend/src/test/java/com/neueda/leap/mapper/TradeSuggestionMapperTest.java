package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.TradeSuggestion;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql"})
class TradeSuggestionMapperTest {
    @Autowired
    private TradeSuggestionMapper tradeSuggestionMapper;

    @Test
    void listClientTradeSuggestionsReturnsRows() {
        List<TradeSuggestion> suggestions = tradeSuggestionMapper.listClientTradeSuggestions(7);

        assertEquals(1, suggestions.size());
        assertEquals(15, suggestions.get(0).getSuggestionId());
        assertEquals("SUGGESTED", suggestions.get(0).getStatus());
    }

    @Test
    void getTradeSuggestionReturnsSuggestion() {
        TradeSuggestion suggestion = tradeSuggestionMapper.getTradeSuggestion(15);

        assertNotNull(suggestion);
        assertEquals(3, suggestion.getAdvisorId());
        assertEquals(new BigDecimal("5.500000"), suggestion.getQuantity());
        assertEquals("Increase technology exposure", suggestion.getNotes());
    }

    @Test
    void insertTradeSuggestionCreatesNewRowWithGeneratedId() {
        TradeSuggestion suggestion = new TradeSuggestion();
        suggestion.setAdvisorId(3);
        suggestion.setClientId(7);
        suggestion.setInstrumentId(12);
        suggestion.setTradeType("SELL");
        suggestion.setQuantity(new BigDecimal("3.250000"));
        suggestion.setProposedPrice(new BigDecimal("220.10"));
        suggestion.setSuggestedAt(java.time.LocalDateTime.of(2026, 9, 25, 10, 0));
        suggestion.setStatus("SUGGESTED");
        suggestion.setNotes("Trim concentration");

        int rows = tradeSuggestionMapper.insertTradeSuggestion(suggestion);

        assertEquals(1, rows);
        assertNotNull(suggestion.getSuggestionId());
        TradeSuggestion stored = tradeSuggestionMapper.getTradeSuggestion(suggestion.getSuggestionId());
        assertEquals("SELL", stored.getTradeType());
        assertEquals(new BigDecimal("220.10"), stored.getProposedPrice());
    }

    @Test
    void updateTradeSuggestionUpdatesRequestedFields() {
        TradeSuggestion update = new TradeSuggestion();
        update.setSuggestionId(15);
        update.setStatus("ACCEPTED");
        update.setNotes("Client approved the idea");

        int rows = tradeSuggestionMapper.updateTradeSuggestion(update);

        assertEquals(1, rows);
        TradeSuggestion stored = tradeSuggestionMapper.getTradeSuggestion(15);
        assertEquals("ACCEPTED", stored.getStatus());
        assertEquals("Client approved the idea", stored.getNotes());
    }
}

