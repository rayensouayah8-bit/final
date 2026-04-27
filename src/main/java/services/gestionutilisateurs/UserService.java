package services.gestionutilisateurs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import enums.gestionutilisateurs.UserRole;
import models.gestionagences.ImageAsset;
import models.gestionutilisateurs.User;
import services.CRUD;
import services.gestionagences.ImageAssetService;
import utils.DbConnexion;
import utils.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class UserService implements CRUD<User, Integer> {

    private static final Pattern EMAIL_SIMPLE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME_ALLOWED = Pattern.compile("^[a-zA-Z0-9_.-]{3,30}$");
    private static final Pattern PHONE_ALLOWED = Pattern.compile("^[0-9+()\\-\\s.]{8,25}$");
    private static final long MAX_PROFILE_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ImageAssetService imageAssetService;

    public UserService() {
        this(new ImageAssetService());
    }

    public UserService(ImageAssetService imageAssetService) {
        this.imageAssetService = imageAssetService;
    }

    private static final String SELECT_BY_EMAIL = """
            SELECT id, username, email, password, roles, role, is_active, profile_image_id, phone
            FROM `user` WHERE LOWER(email) = LOWER(?)
            """;

    private static final String SELECT_BY_USERNAME = """
            SELECT id, username, email, password, roles, role, is_active, profile_image_id, phone
            FROM `user` WHERE LOWER(username) = LOWER(?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, username, email, password, roles, role, is_active, profile_image_id, phone
            FROM `user` WHERE id = ?
            """;

    private static final String INSERT_USER = """
            INSERT INTO `user` (
                username, email, password, roles, role, is_active, profile_image_id, phone
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_USER = """
            UPDATE `user` SET
                username = ?, email = ?, password = ?, roles = ?, role = ?, is_active = ?, profile_image_id = ?, phone = ?
            WHERE id = ?
            """;

    private static final String DELETE_USER = """
            DELETE FROM `user` WHERE id = ?
            """;

    public User signUp(String username, String email, String rawPassword) throws SQLException {
        return signUp(username, email, rawPassword, UserRole.USER);
    }

    public User signUp(String username, String email, String rawPassword, UserRole registerAs) throws SQLException {
        validateSignUp(username, email, rawPassword);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("This email is already in use.");
        }
        if (findByUsername(username.trim()).isPresent()) {
            throw new IllegalArgumentException("This username is already in use.");
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(PasswordHasher.hash(rawPassword));
        UserRole safeRole = registerAs != null ? registerAs : UserRole.USER;
        if (safeRole == UserRole.AGENCY_ADMIN) {
            user.setRoles(List.of(UserRole.USER.getValue(), UserRole.AGENCY_ADMIN.getValue()));
            user.setRole(UserRole.AGENCY_ADMIN.getValue());
        } else {
            user.setRoles(List.of(UserRole.USER.getValue()));
            user.setRole(UserRole.USER.getValue());
        }
        user.setIsActive(true);
        insert(user);
        user.setPassword(null);
        return user;
    }

    public Optional<User> login(String email, String rawPassword) throws SQLException {
        if (email == null || email.isBlank() || rawPassword == null) {
            return Optional.empty();
        }
        Optional<User> found = findByEmail(email.trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        User user = found.get();
        if (!PasswordHasher.matches(rawPassword, user.getPassword())) {
            return Optional.empty();
        }
        user.setPassword(null);
        return Optional.of(user);
    }

    private static void validateSignUp(String username, String email, String rawPassword) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (!USERNAME_ALLOWED.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException("Username must be 3-30 characters (letters, numbers, _ . -).");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (!EMAIL_SIMPLE.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Email is not valid.");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
    }

    @Override
    public void create(User entity) throws SQLException {
        insert(entity);
    }

    @Override
    public void insert(User entity) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(INSERT_USER, Statement.RETURN_GENERATED_KEYS)) {
            fillStatementWithoutId(entity, ps);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    entity.setId(keys.getInt(1));
                }
            }
        }
    }

    @Override
    public void update(User entity) throws SQLException {
        if (entity.getId() == null) {
            throw new IllegalArgumentException("id is required for update");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(UPDATE_USER)) {
            fillStatementWithoutId(entity, ps);
            ps.setInt(9, entity.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(Integer id) throws SQLException {
        if (id == null) {
            throw new IllegalArgumentException("id is required for delete");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(DELETE_USER)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public Optional<User> get(Integer id) throws SQLException {
        if (id == null) {
            throw new IllegalArgumentException("id is required for get");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_EMAIL)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_USERNAME)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public UserProfileValidationResult validateProfileUpdate(Integer userId, String username, String email, String phone) throws SQLException {
        Map<String, String> errors = new LinkedHashMap<>();
        String userTrim = username == null ? "" : username.trim();
        String emailTrim = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String phoneTrim = phone == null ? "" : phone.trim();

        if (userId == null) {
            errors.put(UserProfileValidationResult.FIELD_USERNAME, "Session user not found.");
            return UserProfileValidationResult.of(errors);
        }
        if (userTrim.isEmpty()) {
            errors.put(UserProfileValidationResult.FIELD_USERNAME, "Username is required.");
        } else if (!USERNAME_ALLOWED.matcher(userTrim).matches()) {
            errors.put(UserProfileValidationResult.FIELD_USERNAME, "Username must be 3-30 characters (letters, numbers, _ . -).");
        } else {
            Optional<User> existing = findByUsername(userTrim);
            if (existing.isPresent() && !userId.equals(existing.get().getId())) {
                errors.put(UserProfileValidationResult.FIELD_USERNAME, "This username is already in use.");
            }
        }

        if (emailTrim.isEmpty()) {
            errors.put(UserProfileValidationResult.FIELD_EMAIL, "Email is required.");
        } else if (!EMAIL_SIMPLE.matcher(emailTrim).matches()) {
            errors.put(UserProfileValidationResult.FIELD_EMAIL, "Email is not valid.");
        } else {
            Optional<User> existing = findByEmail(emailTrim);
            if (existing.isPresent() && !userId.equals(existing.get().getId())) {
                errors.put(UserProfileValidationResult.FIELD_EMAIL, "This email is already in use.");
            }
        }

        if (!phoneTrim.isEmpty()) {
            if (!PHONE_ALLOWED.matcher(phoneTrim).matches()) {
                errors.put(UserProfileValidationResult.FIELD_PHONE, "Invalid phone number (8-25 characters: digits, spaces, + ( ) - .).");
            } else {
                long digits = phoneTrim.chars().filter(Character::isDigit).count();
                if (digits < 8 || digits > 15) {
                    errors.put(UserProfileValidationResult.FIELD_PHONE, "Phone must contain between 8 and 15 digits.");
                }
            }
        }
        return UserProfileValidationResult.of(errors);
    }

    public User updateProfile(Integer userId, String username, String email, String phone) throws SQLException {
        if (userId == null) {
            throw new IllegalArgumentException("User not found.");
        }
        UserProfileValidationResult validation = validateProfileUpdate(userId, username, email, phone);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(String.join(" ", validation.getFieldErrors().values()));
        }
        User current = get(userId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        current.setUsername(username.trim());
        current.setEmail(email.trim().toLowerCase(Locale.ROOT));
        String phoneTrim = phone == null ? "" : phone.trim();
        current.setPhone(phoneTrim.isEmpty() ? null : phoneTrim);
        update(current);
        User refreshed = get(userId).orElse(current);
        refreshed.setPassword(null);
        return refreshed;
    }

    /**
     * Ajoute {@link UserRole#AGENCY_ADMIN} aux roles JSON du compte (apres approbation demande agence).
     */
    public void addAgencyAdminRole(Integer userId) throws SQLException {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required.");
        }
        User u = get(userId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        LinkedHashSet<String> merged = new LinkedHashSet<>(u.getRoles() != null ? u.getRoles() : List.of());
        merged.add(UserRole.AGENCY_ADMIN.getValue());
        u.setRoles(new ArrayList<>(merged));
        update(u);
    }

    /**
     * Enregistre une nouvelle photo de profil (insert dans {@code image_asset}, met a jour {@code user.profile_image_id}, supprime l'ancienne ligne image si besoin).
     */
    public void replaceProfileImage(Integer userId, byte[] imageBytes, String mimeType) throws SQLException {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required.");
        }
        validateProfileImageUpload(imageBytes, mimeType);
        User u = get(userId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        ImageAsset asset = new ImageAsset();
        asset.setMimeType(mimeType);
        asset.setData(imageBytes);
        imageAssetService.insert(asset);
        Long previous = u.getProfileImageId();
        u.setProfileImageId(asset.getId());
        update(u);
        if (previous != null && !previous.equals(asset.getId())) {
            imageAssetService.delete(previous);
        }
    }

    /**
     * Supprime la photo de profil (met {@code user.profile_image_id} a {@code null}, supprime la ligne {@code image_asset}).
     */
    public void clearProfileImage(Integer userId) throws SQLException {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required.");
        }
        User u = get(userId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        Long previous = u.getProfileImageId();
        if (previous == null) {
            return;
        }
        u.setProfileImageId(null);
        update(u);
        imageAssetService.delete(previous);
    }

    private static void validateProfileImageUpload(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Empty image.");
        }
        if (imageBytes.length > MAX_PROFILE_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image is too large (max 5 MB).");
        }
        String mime = mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
        if (!(mime.equals("image/png") || mime.equals("image/jpeg") || mime.equals("image/webp"))) {
            throw new IllegalArgumentException("Unsupported image format (PNG, JPEG, WEBP).");
        }
    }

    /** Charge le binaire + MIME pour affichage JavaFX ({@code new Image(stream)}). */
    public Optional<ImageAsset> loadProfileImage(Integer userId) throws SQLException {
        Optional<User> u = get(userId);
        if (u.isEmpty() || u.get().getProfileImageId() == null) {
            return Optional.empty();
        }
        return imageAssetService.get(u.get().getProfileImageId());
    }

    private void fillStatementWithoutId(User entity, PreparedStatement ps) throws SQLException {
        int i = 1;
        ps.setString(i++, entity.getUsername());
        ps.setString(i++, entity.getEmail());
        ps.setString(i++, entity.getPassword());
        ps.setString(i++, toJsonRoles(entity.getRoles()));
        nullableString(ps, i++, entity.getRole());
        nullableBoolean(ps, i++, entity.getIsActive());
        nullableLong(ps, i++, entity.getProfileImageId());
        nullableString(ps, i++, entity.getPhone());
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password"));
        user.setRoles(readStringList(rs.getString("roles")));
        user.setRole(rs.getString("role"));
        user.setIsActive(readNullableBoolean(rs, "is_active"));
        long profileImg = rs.getLong("profile_image_id");
        user.setProfileImageId(rs.wasNull() ? null : profileImg);
        user.setPhone(rs.getString("phone"));
        if (rs.wasNull()) {
            user.setPhone(null);
        }
        return user;
    }

    private static String toJsonRoles(List<String> roles) throws SQLException {
        try {
            return JSON.writeValueAsString(roles != null ? roles : List.of());
        } catch (JsonProcessingException e) {
            throw new SQLException("Unable to serialize roles JSON", e);
        }
    }

    private static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private static void nullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void nullableBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
        }
    }

    private static void nullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static Boolean readNullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
