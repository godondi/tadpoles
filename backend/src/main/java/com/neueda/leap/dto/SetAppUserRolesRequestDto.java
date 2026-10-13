package com.neueda.leap.dto;

import java.util.List;

public record SetAppUserRolesRequestDto(
        List<String> roles
) {
}
