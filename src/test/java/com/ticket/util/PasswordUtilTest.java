package com.ticket.util;

import com.ticket.exception.BusinessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

class PasswordUtilTest {
    @Test
    void shouldRejectCommonAndAccountRelatedPasswords() {
        Assertions.assertThrows(BusinessException.class,
            () -> PasswordUtil.validateStrength("Ticket@123456"));
        Assertions.assertThrows(BusinessException.class,
            () -> PasswordUtil.validateStrength("AliceSafe#482", "alice"));
        Assertions.assertThrows(BusinessException.class,
            () -> PasswordUtil.validateStrength("Short#12"));
        Assertions.assertThrows(BusinessException.class,
            () -> PasswordUtil.validateStrength("River Stone#123"));
        Assertions.assertThrows(BusinessException.class,
            () -> PasswordUtil.validateStrength("River\tStone#123"));
    }

    @Test
    void shouldGenerateStrongTemporaryPasswords() {
        String first = PasswordUtil.generateTemporaryPassword();
        String second = PasswordUtil.generateTemporaryPassword();

        Assertions.assertNotEquals(first, second);
        Assertions.assertDoesNotThrow(() -> PasswordUtil.validateStrength(first));
        Assertions.assertDoesNotThrow(() -> PasswordUtil.validateStrength(second));
    }

    @Test
    void shouldDetectOldCostAndTolerateMalformedHashes() {
        String oldHash = BCrypt.hashpw("RiverStone#123", BCrypt.gensalt(10));

        Assertions.assertTrue(PasswordUtil.needsRehash(oldHash));
        Assertions.assertTrue(PasswordUtil.needsRehash("not-a-bcrypt-hash"));
        Assertions.assertFalse(PasswordUtil.matches("RiverStone#123", "not-a-bcrypt-hash"));
    }
}
