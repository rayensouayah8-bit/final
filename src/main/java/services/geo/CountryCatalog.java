package services.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads countries (name, ISO-3166-1 alpha-2, flag emoji) from REST Countries API.
 * Used for filter UIs; agency rows may store {@code country} as ISO-2 codes or localized names.
 */
public final class CountryCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * English + French display names (lowercase) → ISO-3166-1 alpha-2, for resolving DB text and addresses.
     */
    private static final Map<String, String> ISO_BY_LOCALIZED_COUNTRY_NAME = new HashMap<>();

    static {
        for (String code : Locale.getISOCountries()) {
            if (code == null || code.length() != 2) {
                continue;
            }
            String iso = code.toUpperCase(Locale.ROOT);
            Locale countryLocale = new Locale("", code);
            for (Locale lang : new Locale[]{Locale.ENGLISH, Locale.FRENCH}) {
                String name = countryLocale.getDisplayCountry(lang);
                if (name != null && !name.isBlank()) {
                    ISO_BY_LOCALIZED_COUNTRY_NAME.put(name.trim().toLowerCase(Locale.ROOT), iso);
                }
            }
        }
    }
    /** Compact field list keeps payload small; flag is the emoji string from the API. */
    public static final String REST_COUNTRIES_URL =
            "https://restcountries.com/v3.1/all?fields=name,cca2,flag";

    public record CountryRow(String name, String cca2, String flagEmoji) {
        public String choiceLabel() {
            return flagEmoji + "  " + name + " (" + cca2 + ")";
        }

        /** Raster flag (PNG); avoids emoji rendering as two-letter codes on some Windows fonts. */
        public String flagImageUrl() {
            return CountryCatalog.flagPngUrl(cca2);
        }
    }

    /**
     * Public-domain style flags from flagcdn.com (ISO 3166-1 alpha-2, lowercase in URL).
     */
    public static String flagPngUrl(String cca2) {
        if (cca2 == null || cca2.length() != 2) {
            return null;
        }
        return "https://flagcdn.com/w160/" + cca2.toLowerCase(Locale.ROOT) + ".png";
    }

    /**
     * Resolves ISO-3166-1 alpha-2 for flags and filters: accepts 2-letter codes, English/French country names,
     * and parses the last segments of {@code address} (e.g. {@code "Tunis, Tunisia"} → TN).
     */
    public static String resolveIso2(String countryField, String addressField) {
        String iso = tryIso2Letters(countryField);
        if (iso != null) {
            return iso;
        }
        iso = localizedCountryNameToIso(countryField);
        if (iso != null) {
            return iso;
        }
        return isoFromAddressSegments(addressField);
    }

    private static String tryIso2Letters(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        if (t.length() == 2 && t.chars().allMatch(Character::isLetter)) {
            return t.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private static String localizedCountryNameToIso(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return ISO_BY_LOCALIZED_COUNTRY_NAME.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private static String isoFromAddressSegments(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] parts = address.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String seg = parts[i].trim();
            if (seg.isEmpty()) {
                continue;
            }
            String iso = tryIso2Letters(seg);
            if (iso != null) {
                return iso;
            }
            iso = localizedCountryNameToIso(seg);
            if (iso != null) {
                return iso;
            }
        }
        return null;
    }

    private CountryCatalog() {
    }

    /**
     * Synchronous HTTP fetch; returns empty list on failure (caller should use a fallback list).
     */
    public static List<CountryRow> fetchAllOrEmpty() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REST_COUNTRIES_URL))
                    .timeout(Duration.ofSeconds(14))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseJson(response.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return List.of();
    }

    static List<CountryRow> parseJson(String body) throws IOException {
        List<CountryRow> out = new ArrayList<>();
        JsonNode root = MAPPER.readTree(body);
        if (!root.isArray()) {
            return out;
        }
        for (JsonNode n : root) {
            String code = n.path("cca2").asText("");
            if (code == null || code.length() != 2) {
                continue;
            }
            code = code.toUpperCase(Locale.ROOT);
            String flag = n.path("flag").asText("");
            String name = n.path("name").path("common").asText("");
            if (name.isBlank()) {
                continue;
            }
            if (flag.isBlank()) {
                flag = "\uD83C\uDF0D";
            }
            out.add(new CountryRow(name, code, flag));
        }
        out.sort(Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    public static List<CountryRow> fallbackSample() {
        return List.of(
                new CountryRow("Tunisia", "TN", "\uD83C\uDDF9\uD83C\uDDF3"),
                new CountryRow("France", "FR", "\uD83C\uDDEB\uD83C\uDDF7"),
                new CountryRow("United Arab Emirates", "AE", "\uD83C\uDDE6\uD83C\uDDEA"),
                new CountryRow("Maldives", "MV", "\uD83C\uDDF2\uD83C\uDDFB"),
                new CountryRow("Norway", "NO", "\uD83C\uDDF3\uD83C\uDDF4")
        );
    }
}
