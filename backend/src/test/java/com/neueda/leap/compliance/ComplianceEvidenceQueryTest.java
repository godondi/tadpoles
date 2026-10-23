package com.neueda.leap.compliance;

import static org.junit.jupiter.api.Assertions.*;

import com.neueda.leap.mapper.*;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

class ComplianceEvidenceQueryTest extends ComplianceDatabaseFixture {
    @Test
    void postgresRejectsTheHistoricalQualifiedProjectionAlias() throws Exception {
        try (Connection c = connection();
                Statement s = c.createStatement()) {
            SQLException error =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    s.executeQuery(
                                            "SELECT e.event_id::text AS reference FROM order_events"
                                                    + " e ORDER BY e.reference"));
            assertEquals("42703", error.getSQLState());
        }
    }

    @Test
    void everySectionExecutesScopedCountsEmptyAndDeterministicPagesInPostgres() {
        try (SqlSession session = FACTORY.openSession(true)) {
            ComplianceEvidenceMapper mapper = session.getMapper(ComplianceEvidenceMapper.class);
            for (String name : ComplianceEvidenceMapper.SECTIONS) {
                long expected =
                        switch (name) {
                            case "reservations", "fills" -> 1;
                            case "postings" -> 2;
                            default -> 5;
                        };
                assertEquals(expected, mapper.sectionCount(section(name, 1, 0, 1, true)), name);
                List<String> references = new ArrayList<>();
                for (int page = 0; page < expected; page++) {
                    List<Map<String, Object>> rows =
                            mapper.section(section(name, 1, page, 1, false));
                    assertEquals(1, rows.size(), name);
                    references.add(rows.get(0).get("reference").toString());
                    assertEquals(rows, mapper.section(section(name, 1, page, 1, false)), name);
                }
                assertEquals(expected, new HashSet<>(references).size(), name);
                assertTrue(
                        mapper.section(section(name, 1, (int) expected, 1, false)).isEmpty(), name);
                for (int id : List.of(2, 3)) {
                    assertEquals(0, mapper.sectionCount(section(name, id, 0, 1, true)), name);
                    assertTrue(mapper.section(section(name, id, 0, 1, false)).isEmpty(), name);
                }
                Map<String, Object> hidden = section(name, 1, 0, 1, true);
                hidden.put("actor", 99);
                assertEquals(0, mapper.sectionCount(hidden), name);
                hidden.put("count", false);
                assertTrue(mapper.section(hidden).isEmpty(), name);
            }
        }
    }
}
