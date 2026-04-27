package services.gestionagences;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Field-level validation for agency post drafts (service-layer). */
public final class AgencyPostValidationResult {

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_CONTENT = "content";

    private final Map<String, String> fieldErrors;

    private AgencyPostValidationResult(Map<String, String> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    public static AgencyPostValidationResult valid() {
        return new AgencyPostValidationResult(Collections.emptyMap());
    }

    public static AgencyPostValidationResult of(Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) {
            return valid();
        }
        return new AgencyPostValidationResult(Collections.unmodifiableMap(new LinkedHashMap<>(errors)));
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
