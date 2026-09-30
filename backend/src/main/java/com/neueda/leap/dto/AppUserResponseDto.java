package com.neueda.leap.dto;

import com.neueda.leap.domain.AppUser;
import java.util.List;

public record AppUserResponseDto(
        Integer userId,
        String username,
        String email,
        String displayName,
        Boolean enabled,
        List<String> roles,
        Integer advisorId,
        Integer clientId
) {
    public static AppUserResponseDto fromEntity(AppUser user) {
        return new AppUserResponseDto(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getEnabled(),
                user.getRoles() == null ? List.of() : List.copyOf(user.getRoles()),
                user.getAdvisorId(),
                user.getClientId()
        );
    }
}
