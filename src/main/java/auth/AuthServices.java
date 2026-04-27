package auth;

import services.gestionutilisateurs.UserService;

/**
 * Lazily provides the same {@link UserService} stack as Pi-java2 for auth testing,
 * without coupling to Pi-java2's {@code utils.NavigationManager}.
 */
public final class AuthServices {

    private static volatile UserService userService;

    private AuthServices() {
    }

    public static UserService userService() {
        if (userService == null) {
            synchronized (AuthServices.class) {
                if (userService == null) {
                    userService = new UserService();
                }
            }
        }
        return userService;
    }
}
