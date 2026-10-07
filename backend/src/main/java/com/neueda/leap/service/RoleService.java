package com.neueda.leap.service;

import com.neueda.leap.domain.Role;
import java.util.List;

public interface RoleService {
    Role getRole(Integer id);
    List<Role> listRoles();
}

