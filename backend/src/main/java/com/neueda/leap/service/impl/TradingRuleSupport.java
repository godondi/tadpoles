package com.neueda.leap.service.impl;

import com.neueda.leap.domain.Client;
import com.neueda.leap.domain.ClientHolding;
import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.domain.Instrument;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class TradingRuleSupport {
    private TradingRuleSupport() {
    }

    static TradingRuleEvaluation evaluateTrade(
            Client client,
            ClientHolding holding,
            Instrument instrument,
            ClientTrade trade,
            BigDecimal price,
            String inactiveInstrumentMessage,
            String insufficientCashMessage,
            String insufficientHoldingsMessage
    ) {
        if (Boolean.FALSE.equals(instrument.getIsActive())) {
            throw new IllegalArgumentException(inactiveInstrumentMessage);
        }

        BigDecimal cashDelta = trade.getQuantity().multiply(price).setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentQuantity = holding == null ? BigDecimal.ZERO : holding.getQuantity();

        if ("BUY".equals(trade.getTradeType())) {
            if (client.getCashBalance().compareTo(cashDelta) < 0) {
                throw new IllegalArgumentException(insufficientCashMessage);
            }
            return new TradingRuleEvaluation(
                    client.getCashBalance().subtract(cashDelta),
                    currentQuantity.add(trade.getQuantity())
            );
        }

        if (currentQuantity.compareTo(trade.getQuantity()) < 0) {
            throw new IllegalArgumentException(insufficientHoldingsMessage);
        }

        return new TradingRuleEvaluation(
                client.getCashBalance().add(cashDelta),
                currentQuantity.subtract(trade.getQuantity())
        );
    }
}
