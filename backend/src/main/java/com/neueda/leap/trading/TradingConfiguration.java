package com.neueda.leap.trading;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class TradingConfiguration {
    @Bean
    public Clock tradingClock() {
        return Clock.systemUTC();
    }
}
