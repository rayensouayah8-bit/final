package services.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import utils.AppConfig;
import utils.GeoConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Géolocalisation pour le bureau : position approximative par IP (ipapi.co),
 * géocodage Nominatim (OSM, usage raisonnable) et option Mapbox si token configuré.
 */
public class GeolocationService {

    private static final String USER_AGENT = "SmartVoyageDesktop/1.0 (university project; contact: noreply@local)";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper json = new ObjectMapper();
    private static final Map<String, double[]> COUNTRY_CENTROIDS = buildCountryCentroids();

    private static final String HF_NER_URL =
            "https://api-inference.huggingface.co/models/dslim/bert-base-NER";

    /**
     * Position approximative via l’adresse IP publique (sans permission GPS).
     */
    public Optional<GeoPoint> locateFromIp() {
        try {
            String body = httpGet("https://ipapi.co/json/");
            JsonNode root = json.readTree(body);
            if (root.has("error")) {
                return Optional.empty();
            }
            if (!root.has("latitude") || !root.has("longitude")) {
                return Optional.empty();
            }
            double lat = root.get("latitude").asDouble();
            double lon = root.get("longitude").asDouble();
            String city = root.path("city").asText("");
            String country = root.path("country_name").asText("");
            String label = (city + ", " + country).replaceAll("^, |, $", "").trim();
            if (label.isEmpty()) {
                label = country;
            }
            GeoPoint p = new GeoPoint(lat, lon, label, "ipapi.co");
            return p.isValid() ? Optional.of(p) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Géocodage direct (pays / ville saisis) → coordonnées pour ancrer un post ou la carte.
     */
    public Optional<GeoPoint> forwardGeocode(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String token = GeoConfig.getMapboxAccessToken();
        if (!token.isEmpty()) {
            Optional<GeoPoint> m = mapboxForward(query, token);
            if (m.isPresent()) {
                return m;
            }
        }
        Optional<GeoPoint> n = nominatimSearch(query);
        if (n.isPresent()) {
            return n;
        }
        return fallbackCountryCentroid(query);
    }

    /**
     * Si un token Hugging Face est configuré, extrait une phrase lieu (NER) depuis le texte du post,
     * puis géocode (Mapbox / Nominatim).
     */
    public Optional<GeoPoint> geocodeFromPostNarrative(String titre, String contenu) {
        return inferLocationQueryFromHf(titre, contenu).flatMap(this::forwardGeocode);
    }

    /**
     * Requête texte libre → lieu probable via l’API Inference HF (token-classification).
     */
    public Optional<String> inferLocationQueryFromHf(String titre, String contenu) {
        if (!AppConfig.hasHuggingfaceInferenceToken()) {
            return Optional.empty();
        }
        String raw = ((titre != null ? titre : "") + "\n" + (contenu != null ? contenu : "")).trim();
        if (raw.length() < 8) {
            return Optional.empty();
        }
        if (raw.length() > 1200) {
            raw = raw.substring(0, 1200);
        }
        try {
            String token = AppConfig.getHuggingfaceApiToken();
            ObjectNode body = json.createObjectNode();
            body.put("inputs", raw);
            String resp = httpPostJson(HF_NER_URL, token, body.toString());
            return parseHfNerLocation(resp);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<GeoPoint> mapboxForward(String query, String token) {
        try {
            String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.mapbox.com/geocoding/v5/mapbox.places/" + enc
                    + ".json?access_token=" + token + "&limit=1";
            String body = httpGet(url);
            JsonNode root = json.readTree(body);
            JsonNode feats = root.path("features");
            if (!feats.isArray() || feats.isEmpty()) {
                return Optional.empty();
            }
            JsonNode coords = feats.get(0).path("center");
            if (!coords.isArray() || coords.size() < 2) {
                return Optional.empty();
            }
            double lon = coords.get(0).asDouble();
            double lat = coords.get(1).asDouble();
            String place = feats.get(0).path("place_name").asText(query);
            GeoPoint p = new GeoPoint(lat, lon, place, "mapbox-geocoding");
            return p.isValid() ? Optional.of(p) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<GeoPoint> nominatimSearch(String query) {
        try {
            String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://nominatim.openstreetmap.org/search?q=" + enc + "&format=json&limit=1";
            String body = httpGet(url);
            JsonNode arr = json.readTree(body);
            if (!arr.isArray() || arr.isEmpty()) {
                return Optional.empty();
            }
            JsonNode o = arr.get(0);
            double lat = o.path("lat").asDouble(Double.NaN);
            double lon = o.path("lon").asDouble(Double.NaN);
            String display = o.path("display_name").asText(query);
            GeoPoint p = new GeoPoint(lat, lon, display, "nominatim");
            return p.isValid() ? Optional.of(p) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> parseHfNerLocation(String responseBody) {
        try {
            JsonNode root = json.readTree(responseBody);
            if (root.isObject() && root.has("error")) {
                return Optional.empty();
            }
            JsonNode arr = root;
            if (root.isArray() && !root.isEmpty() && root.get(0).isArray()) {
                arr = root.get(0);
            }
            if (!arr.isArray() || arr.isEmpty()) {
                return Optional.empty();
            }
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (JsonNode tok : arr) {
                String group = tok.path("entity_group").asText(tok.path("entity").asText("")).toUpperCase(Locale.ROOT);
                boolean isLoc = group.contains("LOC");
                if (!isLoc) {
                    if (current.length() > 0) {
                        parts.add(current.toString().trim());
                        current.setLength(0);
                    }
                    continue;
                }
                String w = tok.path("word").asText("");
                if (w.isEmpty()) {
                    continue;
                }
                if (w.startsWith("##")) {
                    current.append(w.substring(2));
                } else {
                    if (current.length() > 0) {
                        parts.add(current.toString().trim());
                        current.setLength(0);
                    }
                    current.append(w);
                }
            }
            if (current.length() > 0) {
                parts.add(current.toString().trim());
            }
            if (parts.isEmpty()) {
                return Optional.empty();
            }
            String best = parts.stream().max((a, b) -> Integer.compare(a.length(), b.length())).orElse("");
            return best.length() >= 2 ? Optional.of(best) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String httpPostJson(String url, String bearerToken, String jsonBody) throws IOException, InterruptedException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 503) {
            Thread.sleep(1500);
            resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        }
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private String httpGet(String url) throws IOException, InterruptedException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private Optional<GeoPoint> fallbackCountryCentroid(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String key = normalize(query);
        double[] c = COUNTRY_CENTROIDS.get(key);
        if (c == null) {
            return Optional.empty();
        }
        return Optional.of(new GeoPoint(c[0], c[1], query.trim(), "country-centroid-fallback"));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, double[]> buildCountryCentroids() {
        Map<String, double[]> m = new HashMap<>();
        m.put("france", new double[]{46.2276, 2.2137});
        m.put("maroc", new double[]{31.7917, -7.0926});
        m.put("morocco", new double[]{31.7917, -7.0926});
        m.put("tunisie", new double[]{33.8869, 9.5375});
        m.put("tunisia", new double[]{33.8869, 9.5375});
        m.put("algerie", new double[]{28.0339, 1.6596});
        m.put("algeria", new double[]{28.0339, 1.6596});
        m.put("espagne", new double[]{40.4637, -3.7492});
        m.put("spain", new double[]{40.4637, -3.7492});
        m.put("italie", new double[]{41.8719, 12.5674});
        m.put("italy", new double[]{41.8719, 12.5674});
        m.put("allemagne", new double[]{51.1657, 10.4515});
        m.put("germany", new double[]{51.1657, 10.4515});
        m.put("portugal", new double[]{39.3999, -8.2245});
        m.put("royaume-uni", new double[]{55.3781, -3.4360});
        m.put("united kingdom", new double[]{55.3781, -3.4360});
        m.put("uk", new double[]{55.3781, -3.4360});
        m.put("usa", new double[]{39.8283, -98.5795});
        m.put("united states", new double[]{39.8283, -98.5795});
        m.put("canada", new double[]{56.1304, -106.3468});
        m.put("japon", new double[]{36.2048, 138.2529});
        m.put("japan", new double[]{36.2048, 138.2529});
        return m;
    }
}
