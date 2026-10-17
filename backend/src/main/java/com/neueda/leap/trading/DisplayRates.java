package com.neueda.leap.trading;

import java.io.StringReader;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/** ECB reference rates are for display only. No execution service depends on this class. */
public final class DisplayRates {
    private DisplayRates() {}

    public record Estimate(BigDecimal amount, BigDecimal rate, LocalDate rateDate) {}

    public static Estimate convert(String xml, String currency, BigDecimal usd, LocalDate today) {
        try {
            if (xml == null
                    || xml.length() > 100000
                    || !Set.of("GBP", "INR", "EUR").contains(currency))
                throw new IllegalArgumentException();
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var document =
                    factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            var nodes = document.getElementsByTagNameNS("*", "Cube");
            LocalDate date = null;
            Map<String, BigDecimal> rates = new HashMap<>();
            rates.put("EUR", BigDecimal.ONE);
            for (int i = 0; i < nodes.getLength(); i++) {
                var element = (Element) nodes.item(i);
                if (element.hasAttribute("time")) {
                    if (date != null) throw new IllegalArgumentException();
                    date = LocalDate.parse(element.getAttribute("time"));
                }
                if (element.hasAttribute("currency")) {
                    String rate = element.getAttribute("rate");
                    if (!rate.matches("[0-9]{1,7}(\\.[0-9]{1,12})?"))
                        throw new IllegalArgumentException();
                    var value = new BigDecimal(rate);
                    if (value.signum() <= 0
                            || rates.putIfAbsent(element.getAttribute("currency"), value) != null)
                        throw new IllegalArgumentException();
                }
            }
            if (date == null
                    || date.isAfter(today)
                    || date.isBefore(today.minusDays(7))
                    || !rates.containsKey("USD")
                    || !rates.containsKey(currency)) throw new IllegalArgumentException();
            return new Estimate(
                    usd.multiply(rates.get(currency))
                            .divide(rates.get("USD"), 2, RoundingMode.HALF_UP),
                    rates.get(currency).divide(rates.get("USD"), 12, RoundingMode.HALF_UP),
                    date);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Reference rates are missing, invalid or more than 7 days old");
        }
    }
}
