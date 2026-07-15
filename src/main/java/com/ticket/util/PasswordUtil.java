package com.ticket.util;

import com.ticket.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 64;
    private static final int MAX_BCRYPT_BYTES = 72;
    private static final int BCRYPT_COST = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TEMP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final Set<String> BLOCKED_PASSWORDS = Set.of(
        "ticket@123", "password123!", "password@123", "admin@123456",
        "qwerty123!", "12345678@a", "welcome123!", "letmein123!"
    );
    private static final String DUMMY_HASH = BCrypt.hashpw("TimingOnly#9fK2vL7q", BCrypt.gensalt(BCRYPT_COST));

    private PasswordUtil() {
    }

    public static String hashPassword(String rawPassword) {
        validateStrength(rawPassword);
        return BCrypt.hashpw(normalize(rawPassword), BCrypt.gensalt(BCRYPT_COST));
    }

    public static String hashPassword(String rawPassword, String... contextValues) {
        validateStrength(rawPassword, contextValues);
        return BCrypt.hashpw(normalize(rawPassword), BCrypt.gensalt(BCRYPT_COST));
    }

    public static String rehashPassword(String rawPassword) {
        if (rawPassword == null) {
            throw new BusinessException("密码不能为空");
        }
        return BCrypt.hashpw(normalize(rawPassword), BCrypt.gensalt(BCRYPT_COST));
    }

    public static boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(normalize(rawPassword), passwordHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static void consumeDummyHash(String rawPassword) {
        matches(rawPassword == null ? "" : rawPassword, DUMMY_HASH);
    }

    public static boolean needsRehash(String passwordHash) {
        if (passwordHash == null || passwordHash.length() < 7) {
            return true;
        }
        try {
            return Integer.parseInt(passwordHash.substring(4, 6)) < BCRYPT_COST;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    public static void validateStrength(String rawPassword) {
        validateStrength(rawPassword, new String[0]);
    }

    public static void validateStrength(String rawPassword, String... contextValues) {
        String password = normalize(rawPassword);
        int length = password == null ? 0 : password.codePointCount(0, password.length());
        if (password == null || length < MIN_PASSWORD_LENGTH || length > MAX_PASSWORD_LENGTH) {
            throw new BusinessException("密码长度需为 12 到 64 位");
        }
        if (password.codePoints().anyMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint))) {
            throw new BusinessException("密码不能包含空格或其他空白字符");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new BusinessException("当前 BCrypt 架构下密码 UTF-8 编码不能超过 72 字节");
        }
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;
        for (char ch : password.toCharArray()) {
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
        String comparable = password.toLowerCase(Locale.ROOT);
        if (BLOCKED_PASSWORDS.contains(comparable) || comparable.contains("ticket") || comparable.contains("工单管理")) {
            throw new BusinessException("该密码过于常见或包含系统名称，请更换密码");
        }
        if (contextValues != null) {
            for (String contextValue : contextValues) {
                String context = contextValue == null ? "" : normalize(contextValue).trim().toLowerCase(Locale.ROOT);
                int at = context.indexOf('@');
                if (at > 0) {
                    context = context.substring(0, at);
                }
                if (context.length() >= 3 && comparable.contains(context)) {
                    throw new BusinessException("密码不能包含用户名、邮箱或手机号等账号信息");
                }
            }
        }
    }

    public static String generateTemporaryPassword() {
        char[] result = new char[20];
        result[0] = "ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(SECURE_RANDOM.nextInt(24));
        result[1] = "abcdefghijkmnopqrstuvwxyz".charAt(SECURE_RANDOM.nextInt(25));
        result[2] = "23456789".charAt(SECURE_RANDOM.nextInt(8));
        result[3] = "!@#$%".charAt(SECURE_RANDOM.nextInt(5));
        for (int index = 4; index < result.length; index++) {
            result[index] = TEMP_ALPHABET.charAt(SECURE_RANDOM.nextInt(TEMP_ALPHABET.length()));
        }
        for (int index = result.length - 1; index > 0; index--) {
            int swapIndex = SECURE_RANDOM.nextInt(index + 1);
            char value = result[index];
            result[index] = result[swapIndex];
            result[swapIndex] = value;
        }
        return new String(result);
    }

    private static String normalize(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFC);
    }
}
