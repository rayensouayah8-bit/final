package enums.gestionagences;

/**
 * Statut d'une demande de compte agence (aligne sur {@code App\Enum\AgencyApplicationStatus} Symfony).
 */
public enum AgencyApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public String asDb() {
        return name();
    }

    public static AgencyApplicationStatus fromDb(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING;
        }
        return valueOf(raw.trim().toUpperCase());
    }
}
