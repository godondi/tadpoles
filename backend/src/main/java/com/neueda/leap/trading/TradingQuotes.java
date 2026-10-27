package com.neueda.leap.trading;

import com.neueda.leap.trading.TradingModels.*;

public interface TradingQuotes {
    Quote quote(Policy policy);
}
