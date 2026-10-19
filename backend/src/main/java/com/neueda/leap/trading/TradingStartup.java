package com.neueda.leap.trading;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TradingStartup implements ApplicationRunner {
    private final DemoAccountSetup demo;
    private final CatalogSetup catalog;
    private final boolean enabled;

    public TradingStartup(
            DemoAccountSetup demo,
            CatalogSetup catalog,
            @Value("${trading.local-demo-setup:false}") boolean enabled) {
        this.demo = demo;
        this.catalog = catalog;
        this.enabled = enabled;
    }

    public void run(ApplicationArguments args) {
        if (enabled) {
            demo.initialize();
            catalog.initialize();
        }
    }
}
