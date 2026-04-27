package enums.gestionutilisateurs;

public enum UserRole {
    USER("ROLE_USER"),
    ADMIN("ROLE_ADMIN"),
    AGENCY_ADMIN("ROLE_AGENCY_ADMIN");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
