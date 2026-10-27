package com.neueda.leap.service;

import com.neueda.leap.dto.BankingDtos.*;
import java.util.List;
import java.util.UUID;

public interface BankingService {
    List<BankReference> banks(int client);

    BankReference save(int client, int actor, UUID id, BankRequest request);

    void remove(int client, int actor, UUID id, long version);

    Movement move(int client, int actor, String key, MovementRequest request);

    Movement byRequest(int client, String key);

    List<Movement> history(int client, int page, int pageSize);
}
