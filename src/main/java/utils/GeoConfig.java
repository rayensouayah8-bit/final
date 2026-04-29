package utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Charge {@code geo-config.properties} depuis le classpath (optionnel).
 * Variables d'environnement {@code MAPBOX_ACCESS_TOKEN} et {@code HUGGINGFACE_API_TOKEN}
 * surchargent les clés fichier.
 */
public final class GeoConfig {

    private static final String RESOURCE = "/geo-config.properties";
    private static volatile String mapboxAccessToken = "";
    private static volatile String huggingfaceApiToken = "";

    static {
        reload();
    }

    private GeoConfig() {
    }

    public static void reload() {
        Properties p = new Properties();
        try (InputStream in = GeoConfig.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
        }
        String fromFile = p.getProperty("mapbox.access.token", "").trim();
        String fromEnv = System.getenv("MAPBOX_ACCESS_TOKEN");
        if (fromEnv != null && !fromEnv.isBlank()) {
            mapboxAccessToken = fromEnv.trim();
        } else {
            mapboxAccessToken = fromFile;
        }
        String hfFile = p.getProperty("huggingface.api.token", "").trim();
        String hfEnv = System.getenv("HUGGINGFACE_API_TOKEN");
        if (hfEnv != null && !hfEnv.isBlank()) {
            huggingfaceApiToken = hfEnv.trim();
        } else {
            huggingfaceApiToken = hfFile;
        }
    }

    public static String getMapboxAccessToken() {
        return mapboxAccessToken != null ? mapboxAccessToken : "";
    }

    public static boolean hasMapboxToken() {
        String t = getMapboxAccessToken();
        return !t.isEmpty() && t.startsWith("pk.");
    }

    public static String getHuggingfaceApiToken() {
        return huggingfaceApiToken != null ? huggingfaceApiToken : "";
    }

    public static boolean hasHuggingfaceToken() {
        return !getHuggingfaceApiToken().isEmpty();
    }
}
