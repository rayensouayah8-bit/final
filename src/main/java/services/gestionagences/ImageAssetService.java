package services.gestionagences;

import models.gestionagences.ImageAsset;
import utils.DbConnexion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stockage / lecture des images (couverture agence, logo agence, photo profil utilisateur) via {@code image_asset}.
 */
public class ImageAssetService {

    private static final long MAX_BYTES = 8 * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final String INSERT_BINARY = """
            INSERT INTO image_asset (mime_type, data) VALUES (?, ?)
            """;
    private static final String INSERT_PATH_BASED = """
            INSERT INTO image_asset (path, thumbnail_path, file_name, mime_type, size_bytes, created_at, is_deleted, owner_id)
            VALUES (?, NULL, ?, ?, ?, ?, 0, NULL)
            """;
    private static final String SELECT_BINARY_BY_ID = """
            SELECT id, mime_type, data, created_at FROM image_asset WHERE id = ?
            """;
    private static final String SELECT_PATH_BY_ID = """
            SELECT id, mime_type, path, created_at FROM image_asset WHERE id = ?
            """;
    private static final String DELETE = """
            DELETE FROM image_asset WHERE id = ?
            """;
    private static final Path IMAGE_STORE_DIR = Paths.get(System.getProperty("user.home"), ".smartvoyage", "image-asset-store");

    public void insert(ImageAsset entity) throws SQLException {
        validatePayload(entity);
        Connection c = DbConnexion.getInstance().getConnection();
        if (hasColumn(c, "image_asset", "data")) {
            try (PreparedStatement ps = c.prepareStatement(INSERT_BINARY, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, entity.getMimeType().trim().toLowerCase(Locale.ROOT));
                ps.setBytes(2, entity.getData());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        entity.setId(keys.getLong(1));
                    }
                }
            }
        } else {
            Path storedFile = storeImageOnDisk(entity.getData(), entity.getMimeType());
            try (PreparedStatement ps = c.prepareStatement(INSERT_PATH_BASED, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, storedFile.toString());
                ps.setString(2, storedFile.getFileName().toString());
                ps.setString(3, entity.getMimeType().trim().toLowerCase(Locale.ROOT));
                ps.setLong(4, entity.getData().length);
                ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        entity.setId(keys.getLong(1));
                    }
                }
            } catch (SQLException e) {
                deleteFileQuietly(storedFile);
                throw e;
            }
        }
        refreshCreatedAt(entity);
    }

    public Optional<ImageAsset> get(Long id) throws SQLException {
        if (id == null) {
            return Optional.empty();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        boolean binaryColumn = hasColumn(c, "image_asset", "data");
        try (PreparedStatement ps = c.prepareStatement(binaryColumn ? SELECT_BINARY_BY_ID : SELECT_PATH_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                ImageAsset a = new ImageAsset();
                a.setId(rs.getLong("id"));
                a.setMimeType(rs.getString("mime_type"));
                if (binaryColumn) {
                    a.setData(rs.getBytes("data"));
                } else {
                    String path = rs.getString("path");
                    a.setData(readImageFromDisk(path));
                }
                Timestamp ts = rs.getTimestamp("created_at");
                a.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
                return Optional.of(a);
            }
        }
    }

    public void delete(Long id) throws SQLException {
        if (id == null) {
            return;
        }
        Connection c = DbConnexion.getInstance().getConnection();
        Path filePath = null;
        if (!hasColumn(c, "image_asset", "data")) {
            filePath = resolvePathById(c, id);
        }
        try (PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
        deleteFileQuietly(filePath);
    }

    private void refreshCreatedAt(ImageAsset entity) throws SQLException {
        if (entity.getId() == null) {
            return;
        }
        Optional<ImageAsset> fresh = get(entity.getId());
        fresh.ifPresent(f -> entity.setCreatedAt(f.getCreatedAt()));
    }

    private static void validatePayload(ImageAsset entity) {
        if (entity.getMimeType() == null || entity.getMimeType().isBlank()) {
            throw new IllegalArgumentException("Image MIME type is required.");
        }
        String mime = entity.getMimeType().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME.contains(mime)) {
            throw new IllegalArgumentException("Format d'image non autorise (JPEG, PNG, GIF, WebP uniquement).");
        }
        if (entity.getData() == null || entity.getData().length == 0) {
            throw new IllegalArgumentException("Le contenu de l'image est vide.");
        }
        if (entity.getData().length > MAX_BYTES) {
            throw new IllegalArgumentException("Image trop volumineuse (max 8 Mo).");
        }
        entity.setMimeType(mime);
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet cols = meta.getColumns(c.getCatalog(), null, table, column)) {
            if (cols.next()) {
                return true;
            }
        }
        try (ResultSet cols = meta.getColumns(c.getCatalog(), null, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
            return cols.next();
        }
    }

    private static Path storeImageOnDisk(byte[] data, String mime) throws SQLException {
        try {
            Files.createDirectories(IMAGE_STORE_DIR);
            String extension = switch (mime) {
                case "image/png" -> "png";
                case "image/gif" -> "gif";
                case "image/webp" -> "webp";
                default -> "jpg";
            };
            Path target = IMAGE_STORE_DIR.resolve(UUID.randomUUID() + "." + extension);
            Files.write(target, data);
            return target;
        } catch (IOException e) {
            throw new SQLException("Unable to store image on disk.", e);
        }
    }

    private static byte[] readImageFromDisk(String pathRaw) throws SQLException {
        if (pathRaw == null || pathRaw.isBlank()) {
            return null;
        }
        try {
            Path path = Paths.get(pathRaw);
            if (!Files.exists(path)) {
                return null;
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new SQLException("Unable to read image from disk.", e);
        }
    }

    private static Path resolvePathById(Connection c, Long id) throws SQLException {
        String sql = "SELECT path FROM image_asset WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String raw = rs.getString("path");
                if (raw == null || raw.isBlank()) {
                    return null;
                }
                return Paths.get(raw);
            }
        }
    }

    private static void deleteFileQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best effort only.
        }
    }
}
