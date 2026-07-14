package com.ticket.integration;

import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import com.ticket.service.UserService;
import com.ticket.util.MongoDBUtil;
import com.ticket.util.MySQLDBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "ticket.integration", matches = "true")
class PasswordSecurityIntegrationTest {
    @AfterAll
    static void closeConnections() {
        MongoDBUtil.close();
        MySQLDBUtil.close();
    }

    @Test
    void shouldAuthenticateRotatedSeedCredentialAndRequireChange() {
        BusinessException failedAttempt = Assertions.assertThrows(BusinessException.class,
            () -> new UserService().login("admin01", "WrongPassword#482"));
        Assertions.assertEquals("用户名或密码错误", failedAttempt.getMessage());

        User user = new UserService().login("admin01", "CedarFalcon#481");

        Assertions.assertEquals("ADMIN", user.getRole());
        Assertions.assertEquals(1, user.getMustChangePassword());
        Assertions.assertEquals(0, user.getFailedLoginAttempts());
        Assertions.assertNull(user.getLockedUntil());
        Assertions.assertNull(user.getPasswordHash());
    }

    @Test
    void shouldStillRejectDisabledSeedAccount() {
        BusinessException exception = Assertions.assertThrows(BusinessException.class,
            () -> new UserService().login("user06", "PolarMeadow#769"));

        Assertions.assertEquals("用户已被禁用", exception.getMessage());
    }
}
