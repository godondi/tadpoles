package com.neueda.leap.trading;

import com.neueda.leap.mapper.TradingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ordinary registration creates a zero USD account, never a grant. */
@Service
public class TradingAccountSetup {
    private final TradingMapper db;

    public TradingAccountSetup(TradingMapper db) {
        this.db = db;
    }

    @Transactional
    public void initialize(int client) {
        db.zeroAccount(client, "USD");
    }
}
