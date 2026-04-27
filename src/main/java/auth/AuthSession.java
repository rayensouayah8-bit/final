package auth;

import models.gestionutilisateurs.User;

import java.util.Optional;

/**
 * Minimal in-memory session after successful sign-in (testing only; no post-login navigation).
 */
public final class AuthSession {

    private static User currentUser;

    private AuthSession() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static void clear() {
        currentUser = null;
    }

    public static Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }
}
