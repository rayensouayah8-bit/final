package services.gestionagences;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Field-level validation outcome for agency profile updates (service-layer / "backend" rules).
 */
public final class AgencyAccountValidationResult {

    public static final String FIELD_AGENCY_NAME = "agencyName";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_WEBSITE_URL = "websiteUrl";
    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_COUNTRY = "country";

    private final Map<String, String> fieldErrors;

    private AgencyAccountValidationResult(Map<String, String> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    public static AgencyAccountValidationResult valid() {
        return new AgencyAccountValidationResult(Collections.emptyMap());
    }

    public static AgencyAccountValidationResult of(Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) {
            return valid();
        }
        return new AgencyAccountValidationResult(Collections.unmodifiableMap(new LinkedHashMap<>(errors)));
    }

    public boolean isValid() {
        return fieldErrors.isEmpty();
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public String getError(String field) {
        return fieldErrors.get(field);
    }
}
