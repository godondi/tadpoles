package com.neueda.leap.trading;

import static com.neueda.leap.trading.TradingData.*;

import com.neueda.leap.dto.BankingDtos.BankRequest;
import com.neueda.leap.mapper.*;
import com.neueda.leap.service.BankingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in local demo grant. Ordinary signup/onboarding never calls this. */
@Service
public class DemoAccountSetup {
    private final TradingSetupMapper setup;
    private final TradingMapper accounts;
    private final BankingService banking;
    private final Clock clock;

    public DemoAccountSetup(
            TradingSetupMapper setup, TradingMapper accounts, BankingService banking, Clock clock) {
        this.setup = setup;
        this.accounts = accounts;
        this.banking = banking;
        this.clock = clock;
    }

    @Transactional
    public void initialize() {
        var demo = setup.demo();
        if (demo == null) return;
        int client = integer(demo, "client_id"), actor = integer(demo, "user_id");
        accounts.lockClient(client);
        accounts.zeroAccount(client, "USD");
        var cash = accounts.cash(client, "USD");
        if (setup.hasHistory(client)
                || !Boolean.TRUE.equals(cash.get("available_for_trading"))
                || amount(cash, "posted").signum() != 0) return;
        if (banking.banks(client).isEmpty())
            banking.save(
                    client,
                    actor,
                    null,
                    new BankRequest(
                            "Fictitious demo reference", "000000001", "00001000", true, null));
        setup.opening(
                map(
                        "posting",
                        UUID.randomUUID(),
                        "client",
                        client,
                        "reference",
                        "local-demo-usd-v1:" + client,
                        "group",
                        UUID.randomUUID(),
                        "now",
                        clock.instant()));
        accounts.cashMovement(
                client, "USD", new BigDecimal("10000"), BigDecimal.ZERO, clock.instant());
    }
}
