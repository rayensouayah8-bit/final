package utils;

import auth.AuthSession;
import enums.gestionutilisateurs.UserRole;
import models.gestionagences.AgencyAccount;
import models.gestionoffres.Reservation;
import models.gestionoffres.TravelOffer;
import models.gestionutilisateurs.User;
import services.gestionagences.AgencyAccountService;

import java.sql.SQLException;
import java.util.Optional;

public final class SessionManager {
    private static volatile SessionManager instance;

    private final AgencyAccountService agencyAccountService = new AgencyAccountService();
    private TravelOffer selectedOffer;
    private Reservation selectedReservation;

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    public Long getUserId() {
        return AuthSession.getCurrentUser().map(User::getId).map(Integer::longValue).orElse(null);
    }

    public String getRole() {
        Optional<User> user = AuthSession.getCurrentUser();
        if (user.isEmpty()) {
            return null;
        }
        User u = user.get();
        if (hasRole(u, UserRole.AGENCY_ADMIN.getValue())) {
            return "ROLE_AGENCY";
        }
        return "ROLE_USER";
    }

    public Long getAgencyId() {
        Long userId = getUserId();
        if (userId == null || !"ROLE_AGENCY".equals(getRole())) {
            return null;
        }
        try {
            Optional<AgencyAccount> agency = agencyAccountService.findByResponsableId(userId.intValue());
            return agency.map(AgencyAccount::getId).orElse(null);
        } catch (SQLException e) {
            return null;
        }
    }

    public String getUserName() {
        return AuthSession.getCurrentUser().map(u -> {
            if (u.getUsername() != null && !u.getUsername().isBlank()) {
                return u.getUsername();
            }
            return u.getEmail();
        }).orElse(null);
    }

    public TravelOffer getSelectedOffer() {
        return selectedOffer;
    }

    public void setSelectedOffer(TravelOffer selectedOffer) {
        this.selectedOffer = selectedOffer;
    }

    public Reservation getSelectedReservation() {
        return selectedReservation;
    }

    public void setSelectedReservation(Reservation selectedReservation) {
        this.selectedReservation = selectedReservation;
    }

    private boolean hasRole(User user, String role) {
        if (user == null || role == null) {
            return false;
        }
        if (user.getRoles() != null) {
            for (String r : user.getRoles()) {
                if (role.equalsIgnoreCase(r)) {
                    return true;
                }
            }
        }
        return role.equalsIgnoreCase(user.getRole());
    }
}
