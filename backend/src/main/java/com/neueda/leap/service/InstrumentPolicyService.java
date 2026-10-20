package com.neueda.leap.service;

import com.neueda.leap.dto.InstrumentPolicyRequest;
import java.util.*;

public interface InstrumentPolicyService {
    List<Map<String, Object>> list(String search, int page);

    Map<String, Object> get(int id);

    Map<String, Object> test(String symbol);

    Map<String, Object> save(Integer id, int actor, InstrumentPolicyRequest request);
}
