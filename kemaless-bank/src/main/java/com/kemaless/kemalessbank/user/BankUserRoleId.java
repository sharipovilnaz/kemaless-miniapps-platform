package com.kemaless.kemalessbank.user;

import java.io.Serializable;
import java.util.Objects;

public final class BankUserRoleId implements Serializable {

    private Long user;
    private Long role;

    public BankUserRoleId() {}

    public BankUserRoleId(Long user, Long role) {
        this.user = user;
        this.role = role;
    }

    public Long getUser() {
        return user;
    }

    public Long getRole() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BankUserRoleId that = (BankUserRoleId) o;
        return Objects.equals(user, that.user) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, role);
    }
}
