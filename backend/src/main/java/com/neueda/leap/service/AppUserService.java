package com.neueda.leap.service;

import com.neueda.leap.domain.AppUser;
import com.neueda.leap.dto.SetAppUserRolesRequestDto;
import com.neueda.leap.dto.UpdateAppUserRequestDto;
import java.util.List;

public interface AppUserService {
    AppUser getUser(Integer id);
    AppUser getUserByUsername(String username);
    List<AppUser> listUsers();
    AppUser updateUser(Integer id, UpdateAppUserRequestDto request);
    AppUser setUserRoles(Integer id, SetAppUserRolesRequestDto request);
}
