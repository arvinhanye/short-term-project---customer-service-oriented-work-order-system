package com.ticket.service;

import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UserServiceSecurityTest {
    @Test
    void shouldRequireActiveUser() {
        Assertions.assertThrows(BusinessException.class, () -> UserService.requireActiveUser(null));
        Assertions.assertThrows(BusinessException.class, () -> UserService.requireActiveUser(user("USER", 0)));
        Assertions.assertDoesNotThrow(() -> UserService.requireActiveUser(user("USER", 1)));
    }

    @Test
    void shouldRequireAdminRole() {
        Assertions.assertThrows(BusinessException.class, () -> UserService.requireAdmin(user("USER", 1)));
        Assertions.assertDoesNotThrow(() -> UserService.requireAdmin(user("ADMIN", 1)));
    }

    @Test
    void shouldIdentifyAdminRole() {
        Assertions.assertFalse(UserService.isAdmin(null));
        Assertions.assertFalse(UserService.isAdmin(user("USER", 1)));
        Assertions.assertTrue(UserService.isAdmin(user("ADMIN", 1)));
    }

    @Test
    void shouldRejectDisablingCurrentAdminAndInvalidStatus() {
        User admin = user("ADMIN", 1);

        Assertions.assertThrows(BusinessException.class,
            () -> UserService.validateStatusChange(admin, admin.getUserId(), 0));
        Assertions.assertThrows(BusinessException.class,
            () -> UserService.validateStatusChange(admin, 20002L, 2));
        Assertions.assertDoesNotThrow(
            () -> UserService.validateStatusChange(admin, 20002L, 0));
    }

    private User user(String role, int status) {
        User user = new User();
        user.setUserId(10001L);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
