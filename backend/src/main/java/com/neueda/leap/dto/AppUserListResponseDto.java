package com.neueda.leap.dto;

import com.neueda.leap.domain.AppUser;
import java.util.List;

public record AppUserListResponseDto(
        List<AppUserResponseDto> users
) {
    public static AppUserListResponseDto fromEntities(List<AppUser> users) {
        return new AppUserListResponseDto(
                users.stream().map(AppUserResponseDto::fromEntity).toList()
        );
    }
}
