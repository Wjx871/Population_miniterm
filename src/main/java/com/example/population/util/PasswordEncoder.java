package com.example.population.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordEncoder {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordEncoder() {}

    public static String encode(String rawPassword) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return hash(rawPassword, salt) + ":" + Base64.getEncoder().encodeToString(salt);
    }

    public static boolean matches(String rawPassword, String stored) {
        if (stored == null || !stored.contains(":")) {
            return false;
        }
        String[] parts = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(parts[1]);
        String expected = parts[0];
        return expected.equals(hash(rawPassword, salt));
    }

    private static String hash(String rawPassword, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] bytes = digest.digest(rawPassword.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}