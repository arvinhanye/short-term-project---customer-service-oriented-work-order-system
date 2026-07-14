package com.ticket.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SeedPasswordSecurityTest {
    private static final Pattern USER_ROW = Pattern.compile(
        "\\(\\d+, '([^']+)', '(\\$2a\\$[^']+)'",
        Pattern.MULTILINE
    );

    @Test
    void shouldUseDistinctForcedRotationHashesForSeedUsers() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream("/sql/mysql_init_data.sql")) {
            Assertions.assertNotNull(stream);
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher matcher = USER_ROW.matcher(sql);
        Map<String, String> hashes = new HashMap<>();
        while (matcher.find()) {
            hashes.put(matcher.group(1), matcher.group(2));
        }

        Assertions.assertEquals(12, hashes.size());
        Assertions.assertEquals(12, new HashSet<>(hashes.values()).size());
        Assertions.assertTrue(PasswordUtil.matches("CedarFalcon#481", hashes.get("admin01")));
        Assertions.assertTrue(PasswordUtil.matches("HarborPine#846", hashes.get("user01")));
        Assertions.assertTrue(PasswordUtil.matches("PolarMeadow#769", hashes.get("user06")));
        Assertions.assertTrue(sql.contains("must_change_password, password_changed_at"));
    }
}
