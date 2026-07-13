package com.example.population.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QuickPasswordTest {

    @Test
    void testBcryptPassword() {
        String rawPassword = "Admin@123";
        // 这个是 reset_admin_password.sql 中的 BCrypt 哈希
        String storedHash = "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4uUVQFG.6R.lHzjW";

        boolean matches = PasswordEncoder.matches(rawPassword, storedHash);
        System.out.println("Testing: " + rawPassword);
        System.out.println("Hash: " + storedHash);
        System.out.println("Matches: " + matches);

        assertThat(matches).isTrue();
    }

    @Test
    void testEncodeAndMatch() {
        String rawPassword = "Admin@123";
        String encoded = PasswordEncoder.encode(rawPassword);
        System.out.println("Encoded: " + encoded);

        boolean matches = PasswordEncoder.matches(rawPassword, encoded);
        System.out.println("Matches: " + matches);

        assertThat(matches).isTrue();
    }
}
