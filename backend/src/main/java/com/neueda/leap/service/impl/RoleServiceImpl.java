package com.neueda.leap.service.impl;

import com.neueda.leap.domain.Role;
import com.neueda.leap.exception.RoleNotFoundException;
import com.neueda.leap.mapper.RoleMapper;
import com.neueda.leap.service.RoleService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoleServiceImpl implements RoleService {
    private final RoleMapper roleMapper;

    public RoleServiceImpl(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public Role getRole(Integer id) {
        validateId(id);

        Role role = roleMapper.getRole(id);
        if (role == null) {
            throw new RoleNotFoundException(id);
        }

        return role;
    }

    @Override
    public List<Role> listRoles() {
        return roleMapper.listRoles();
    }

    private void validateId(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Role id must be a positive integer.");
        }
    }
}

