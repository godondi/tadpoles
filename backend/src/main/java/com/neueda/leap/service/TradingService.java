package com.neueda.leap.service;

import com.neueda.leap.trading.TradingModels.*;
import java.util.Map;

public interface TradingService {
    boolean enabled();

    Map<String, Object> availability(int client, Integer instrument);

    Preview preview(int client, Command command);

    Map<String, Object> submit(int client, int actor, String key, Command command);

    Map<String, Object> byRequest(int client, String key);

    Object catalog(int client, Object page);

    Object portfolio(int client, Object legacy);

    Object orders(int client, Object legacy);

    Object detail(int client, int id, Object legacy);

    Object events(int client, int id, int page, int size);

    Object changes(int client, long cursor);
}
