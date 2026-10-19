package com.neueda.leap.trading;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DisplayRatesTest {
    final String xml =
            "<Envelope><Cube><Cube time='2026-10-15'><Cube currency='USD' rate='1.25'/><Cube"
                    + " currency='GBP' rate='0.8'/><Cube currency='INR'"
                    + " rate='100'/></Cube></Cube></Envelope>";

    @Test
    void convertsEuroBaseIntoTargetPerUsdAndRoundsOnlyDisplay() {
        var result =
                DisplayRates.convert(
                        xml, "GBP", new BigDecimal("100.01"), LocalDate.of(2026, 10, 15));
        assertEquals(new BigDecimal("64.01"), result.amount());
        assertEquals(0, new BigDecimal("0.64").compareTo(result.rate()));
        assertEquals(
                new BigDecimal("80.01"),
                DisplayRates.convert(
                                xml, "EUR", new BigDecimal("100.01"), LocalDate.of(2026, 10, 15))
                        .amount());
    }

    @Test
    void staleFutureMissingAndExternalEntityRatesFailClosed() {
        for (String invalid :
                new String[] {
                    xml.replace("2026-10-15", "2026-10-01"),
                    xml.replace("2026-10-15", "2026-10-16"),
                    xml.replace("currency='USD'", "currency='CAD'"),
                    "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>"
                })
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            DisplayRates.convert(
                                    invalid, "GBP", BigDecimal.ONE, LocalDate.of(2026, 10, 15)));
    }
}
