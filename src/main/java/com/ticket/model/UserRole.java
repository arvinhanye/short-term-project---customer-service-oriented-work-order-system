package com.ticket.model;

import com.ticket.exception.BusinessException;

/** 系统角色及其治理层级。层级只用于账号管理，业务权限由显式能力方法判断。 */
public enum UserRole {
    ROOT(3, "系统所有者"),
    ADMIN(2, "管理员"),
    USER(1, "普通用户");

    private final int level;
    private final String displayName;

    UserRole(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public int level() {
        return level;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return name() + " · " + displayName;
    }

    public boolean isLowerThan(UserRole other) {
        return level < other.level;
    }

    public boolean canProcessTickets() {
        return this == ADMIN;
    }

    public boolean canManageBusiness() {
        return this == ADMIN;
    }

    public boolean canViewAdministration() {
        return this == ROOT || this == ADMIN;
    }

    public static UserRole from(String value) {
        if (value == null) {
            throw new BusinessException("用户角色缺失");
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("用户角色非法：" + value);
        }
    }
}
