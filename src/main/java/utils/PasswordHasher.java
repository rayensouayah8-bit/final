package utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordHasher {

    private static final int BCRYPT_STRENGTH = 13;
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    private PasswordHasher() {
    }

    public static String hash(String plainPassword) {
        return ENCODER.encode(plainPassword);
    }

    public static boolean matches(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (storedHash.startsWith("$2y$")) {
            storedHash = "$2a$" + storedHash.substring(4);
        }
        return ENCODER.matches(plainPassword, storedHash);
    }
}
