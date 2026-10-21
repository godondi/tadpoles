package com.neueda.leap.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SupportInputTest {
    @Test
    void prohibitedControlsCannotBeHiddenByTrimming() {
        var json = new ObjectMapper();
        for (String subject :
                new String[] {
                    "\nReceipt question", "Receipt question\t", "\u0085Receipt question"
                }) {
            var body = json.createObjectNode().put("subject", subject);
            assertThrows(
                    ResponseStatusException.class,
                    () -> SupportCaseService.text(body, "subject", 5, 120, false));
        }
        var body = json.createObjectNode().put("description", "\rPlease explain this receipt.");
        assertThrows(
                ResponseStatusException.class,
                () -> SupportCaseService.text(body, "description", 10, 4000, true));
        assertEquals(
                "Please explain\nthis receipt.",
                SupportCaseService.text(
                        json.createObjectNode()
                                .put("description", " Please explain\nthis receipt. "),
                        "description",
                        10,
                        4000,
                        true));
    }
}
