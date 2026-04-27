package services.gestionutilisateurs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Field-level validation outcome for user profile updates.
 */
public final class UserProfileValidationResult {

    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_PHONE = "phone";

    private final Map<String, String> fieldErrors;

    private UserProfileValidationResult(Map<String, String> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    public static UserProfileValidationResult ok() {
        return new UserProfileValidationResult(Collections.emptyMap());
    }

    public static UserProfileValidationResult of(Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) {
            return ok();
        }
        return new UserProfileValidationResult(Collections.unmodifiableMap(new LinkedHashMap<>(errors)));
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
