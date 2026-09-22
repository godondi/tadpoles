package com.neueda.leap.Domain;

import java.io.Serializable;
import java.util.Objects;

public class UserRolesId implements Serializable {
    private Integer userId;
    private Integer roleId;

    public UserRolesId() {
    }

    public UserRolesId(Integer userId, Integer roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserRolesId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}
