package com.neueda.leap.service.support;

import java.util.List;
import java.util.Set;

public interface SupportCategoryHandler {
    record Task(String code, String label) {}

    record Definition(
            String category,
            List<String> reasons,
            Set<String> intakeFields,
            List<Task> tasks,
            List<String> resolutions) {}

    Definition definition();
}
