package com.neueda.leap.service.impl;

import java.math.BigDecimal;

record TradingRuleEvaluation(
        BigDecimal newCashBalance,
        BigDecimal newHoldingQuantity
) {
}
