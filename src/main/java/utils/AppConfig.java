package utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Charge {@code config.properties} depuis le classpath (optionnel).
 * Ne pas committer de secrets : copier {@code config.properties.example} → {@code config.properties}.
 */
public final class AppConfig {

    private static final String RESOURCE = "/config.properties";
    private static final Properties P = new Properties();

    static {
        try (InputStream in = AppConfig.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                P.load(in);
            }
        } catch (IOException ignored) {
        }
    }

    private AppConfig() {
    }

    public static String getHuggingfaceApiToken() {
        String env = System.getenv("HUGGINGFACE_API_TOKEN");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String fromFile = P.getProperty("huggingface.api.token", "").trim();
        if (!fromFile.isEmpty()) {
            return fromFile;
        }
        return GeoConfig.getHuggingfaceApiToken();
    }

    public static boolean hasHuggingfaceInferenceToken() {
        return !getHuggingfaceApiToken().isEmpty();
    }

    public static String getCloudinaryCloudName() {
        String env = System.getenv("CLOUDINARY_CLOUD_NAME");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return P.getProperty("cloudinary.cloud_name", "").trim();
    }

    public static String getCloudinaryUploadPreset() {
        String env = System.getenv("CLOUDINARY_UPLOAD_PRESET");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return P.getProperty("cloudinary.upload_preset", "").trim();
    }

    public static boolean isCloudinaryConfigured() {
        return !getCloudinaryCloudName().isEmpty() && !getCloudinaryUploadPreset().isEmpty();
    }

    public static String getTwilioAccountSid() {
        String env = System.getenv("TWILIO_ACCOUNT_SID");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return P.getProperty("twilio.account_sid", "").trim();
    }

    public static String getTwilioAuthToken() {
        String env = System.getenv("TWILIO_AUTH_TOKEN");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return P.getProperty("twilio.auth_token", "").trim();
    }

    public static String getTwilioFromPhone() {
        String env = System.getenv("TWILIO_FROM_PHONE");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return P.getProperty("twilio.from_phone", "").trim();
    }

    public static String getTwilioToPhone() {
        String env = System.getenv("TWILIO_TO_PHONE");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return P.getProperty("twilio.to_phone", "").trim();
    }

    public static String getTwilioDefaultToPhone() {
        String env = System.getenv("TWILIO_DEFAULT_TO_PHONE");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return P.getProperty("twilio.default_to_phone", "").trim();
    }
}
