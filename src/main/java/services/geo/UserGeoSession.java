package services.geo;

import java.util.Optional;

/**
 * Dernière position « session » (approx. IP ou détection formulaire) pour filtres « Autour de moi » et carte.
 */
public final class UserGeoSession {

    private static final UserGeoSession INSTANCE = new UserGeoSession();

    private volatile GeoPoint last;

    private UserGeoSession() {
    }

    public static UserGeoSession getInstance() {
        return INSTANCE;
    }

    public Optional<GeoPoint> getLast() {
        return Optional.ofNullable(last);
    }

    public void setLast(GeoPoint point) {
        this.last = point != null && point.isValid() ? point : null;
    }

    public void clear() {
        this.last = null;
    }
}
