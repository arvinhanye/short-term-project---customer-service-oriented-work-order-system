package com.ticket.dao;

import com.ticket.util.PasswordUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UserDAOTest {
    @Test
    void shouldHashAndVerifyPassword() {
        String password = "Ticket@123";
        String hash = PasswordUtil.hashPassword(password);
        Assertions.assertNotEquals(password, hash);
        Assertions.assertTrue(PasswordUtil.matches(password, hash));
    }
}
