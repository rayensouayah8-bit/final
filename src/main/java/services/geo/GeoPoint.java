package services.geo;

/**
 * Point géographique avec libellé optionnel (ville, pays) et source (IP, Mapbox, Nominatim…).
 */
public record GeoPoint(double latitude, double longitude, String label, String source) {

    public boolean isValid() {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }
}
