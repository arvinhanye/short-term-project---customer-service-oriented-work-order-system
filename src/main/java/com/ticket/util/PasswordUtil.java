package com.ticket.util;

import com.ticket.exception.BusinessException;
import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {
    private static final int MIN_PASSWORD_LENGTH = 8;

    private PasswordUtil() {
    }

    public static String hashPassword(String rawPassword) {
        validateStrength(rawPassword);
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    public static boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        return BCrypt.checkpw(rawPassword, passwordHash);
    }

    public static void validateStrength(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH || rawPassword.length() > 64) {
            throw new BusinessException("密码长度需为 8 到 64 位");
        }
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;
        for (char ch : rawPassword.toCharArray()) {
            if (Character.isLowerCase(ch)) {
                hasLower = true;
            } else if (Character.isUpperCase(ch)) {
                hasUpper = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            } else {
                hasSymbol = true;
            }
        }
        if (!hasLower || !hasUpper || !hasDigit || !hasSymbol) {
            throw new BusinessException("密码需包含大小写字母、数字和特殊字符");
        }
    }
}
