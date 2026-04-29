package services.gestionevenements;

import models.gestionevenements.TravelEvent;
import services.CRUD;
import utils.DbConnexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TravelEventService implements CRUD<TravelEvent, Long> {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private static final String INSERT = """
            INSERT INTO travel_event (
                title, description, location, event_date, max_participants, image_path, status, created_by_user_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE = """
            UPDATE travel_event SET
                title = ?, description = ?, location = ?, event_date = ?, max_participants = ?, image_path = ?
            WHERE id = ?
            """;

    private static final String UPDATE_BY_OWNER = """
            UPDATE travel_event SET
                title = ?, description = ?, location = ?, event_date = ?, max_participants = ?, image_path = ?
            WHERE id = ? AND created_by_user_id = ?
            """;

    private static final String DELETE = "DELETE FROM travel_event WHERE id = ?";
    private static final String DELETE_BY_OWNER = "DELETE FROM travel_event WHERE id = ? AND created_by_user_id = ?";

    private static final String SELECT_BY_ID = """
            SELECT id, title, description, location, event_date, max_participants, image_path, status,
                   created_by_user_id, created_at, updated_at
            FROM travel_event WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, title, description, location, event_date, max_participants, image_path, status,
                   created_by_user_id, created_at, updated_at
            FROM travel_event ORDER BY event_date DESC, id DESC
            """;

        private static final String SELECT_BY_CREATOR = """
             SELECT id, title, description, location, event_date, max_participants, image_path, status,
                 created_by_user_id, created_at, updated_at
             FROM travel_event
             WHERE created_by_user_id = ?
             ORDER BY event_date DESC, id DESC
             """;

        private static final String SELECT_APPROVED = """
            SELECT id, title, description, location, event_date, max_participants, image_path, status,
               created_by_user_id, created_at, updated_at
            FROM travel_event
            WHERE status = 'APPROVED'
            ORDER BY event_date DESC, id DESC
            """;

        private static final String SELECT_PENDING = """
            SELECT id, title, description, location, event_date, max_participants, image_path, status,
               created_by_user_id, created_at, updated_at
            FROM travel_event
            WHERE status = 'PENDING'
            ORDER BY created_at ASC, id ASC
            """;

        private static final String UPDATE_STATUS = """
            UPDATE travel_event
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

    @Override
    public void create(TravelEvent entity) throws SQLException {
        insert(entity);
    }

    @Override
    public void insert(TravelEvent entity) throws SQLException {
        validate(entity, true);
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entity.getTitle().trim());
            ps.setString(2, entity.getDescription());
            ps.setString(3, entity.getLocation().trim());
            ps.setTimestamp(4, Timestamp.valueOf(entity.getEventDate()));
            ps.setInt(5, sanitizeMax(entity.getMaxParticipants()));
            ps.setString(6, entity.getImagePath());
            ps.setString(7, normalizeStatus(entity.getStatus()));
            ps.setInt(8, entity.getCreatedByUserId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    entity.setId(keys.getLong(1));
                }
            }
        }
    }

    @Override
    public void update(TravelEvent entity) throws SQLException {
        validate(entity, false);
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(UPDATE)) {
            ps.setString(1, entity.getTitle().trim());
            ps.setString(2, entity.getDescription());
            ps.setString(3, entity.getLocation().trim());
            ps.setTimestamp(4, Timestamp.valueOf(entity.getEventDate()));
            ps.setInt(5, sanitizeMax(entity.getMaxParticipants()));
            ps.setString(6, entity.getImagePath());
            ps.setLong(7, entity.getId());
            ps.executeUpdate();
        }
    }

    public void updateByOwner(TravelEvent entity, Integer ownerUserId) throws SQLException {
        validate(entity, false);
        if (ownerUserId == null) {
            throw new IllegalArgumentException("L'identifiant du proprietaire est obligatoire pour modifier l'evenement.");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(UPDATE_BY_OWNER)) {
            ps.setString(1, entity.getTitle().trim());
            ps.setString(2, entity.getDescription());
            ps.setString(3, entity.getLocation().trim());
            ps.setTimestamp(4, Timestamp.valueOf(entity.getEventDate()));
            ps.setInt(5, sanitizeMax(entity.getMaxParticipants()));
            ps.setString(6, entity.getImagePath());
            ps.setLong(7, entity.getId());
            ps.setInt(8, ownerUserId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Seul le proprietaire de l'evenement peut le modifier.");
            }
        }
    }

    @Override
    public void delete(Long id) throws SQLException {
        if (id == null) {
            throw new IllegalArgumentException("id is required for delete");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void deleteByOwner(Long eventId, Integer ownerUserId) throws SQLException {
        if (eventId == null) {
            throw new IllegalArgumentException("L'identifiant de l'evenement est obligatoire pour la suppression.");
        }
        if (ownerUserId == null) {
            throw new IllegalArgumentException("L'identifiant du proprietaire est obligatoire pour supprimer l'evenement.");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(DELETE_BY_OWNER)) {
            ps.setLong(1, eventId);
            ps.setInt(2, ownerUserId);
            int deleted = ps.executeUpdate();
            if (deleted == 0) {
                throw new IllegalArgumentException("Seul le proprietaire de l'evenement peut le supprimer.");
            }
        }
    }

    public Optional<TravelEvent> get(Long id) throws SQLException {
        if (id == null) {
            return Optional.empty();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<TravelEvent> findAll() throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        List<TravelEvent> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<TravelEvent> findByCreator(Integer userId) throws SQLException {
        if (userId == null) {
            return List.of();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        List<TravelEvent> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_CREATOR)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<TravelEvent> findApproved() throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        List<TravelEvent> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_APPROVED);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<TravelEvent> findPending() throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        List<TravelEvent> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_PENDING);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public void updateStatus(Long eventId, String status) throws SQLException {
        if (eventId == null) {
            throw new IllegalArgumentException("L'identifiant de l'evenement est obligatoire.");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, normalizeStatus(status));
            ps.setLong(2, eventId);
            ps.executeUpdate();
        }
    }

    private TravelEvent mapRow(ResultSet rs) throws SQLException {
        TravelEvent e = new TravelEvent();
        e.setId(rs.getLong("id"));
        e.setTitle(rs.getString("title"));
        e.setDescription(rs.getString("description"));
        e.setLocation(rs.getString("location"));
        Timestamp eventTs = rs.getTimestamp("event_date");
        e.setEventDate(eventTs != null ? eventTs.toLocalDateTime() : LocalDateTime.now());
        e.setMaxParticipants(rs.getInt("max_participants"));
        e.setImagePath(rs.getString("image_path"));
        e.setStatus(normalizeStatus(rs.getString("status")));
        e.setCreatedByUserId(rs.getInt("created_by_user_id"));
        Timestamp created = rs.getTimestamp("created_at");
        e.setCreatedAt(created != null ? created.toLocalDateTime() : null);
        Timestamp updated = rs.getTimestamp("updated_at");
        e.setUpdatedAt(updated != null ? updated.toLocalDateTime() : null);
        return e;
    }

    private void validate(TravelEvent e, boolean insert) {
        if (e == null) {
            throw new IllegalArgumentException("event is required");
        }
        e.validateForPersistence(insert);
        if (!insert && e.getId() == null) {
            throw new IllegalArgumentException("id is required for update");
        }
        if (e.getTitle() == null || e.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required.");
        }
        String title = e.getTitle().trim();
        if (title.length() < 6 || title.length() > 80) {
            throw new IllegalArgumentException("Title must contain between 6 and 80 characters.");
        }
        if (e.getLocation() == null || e.getLocation().isBlank()) {
            throw new IllegalArgumentException("Location is required.");
        }
        String location = e.getLocation().trim();
        if (location.length() < 2 || location.length() > 80) {
            throw new IllegalArgumentException("Location must contain between 2 and 80 characters.");
        }
        if (e.getDescription() != null && e.getDescription().trim().length() > 500) {
            throw new IllegalArgumentException("Description cannot exceed 500 characters.");
        }
        if (e.getEventDate() == null) {
            throw new IllegalArgumentException("Event date is required.");
        }
        if (!e.getEventDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Event date and time must be in the future.");
        }
        if (insert && e.getCreatedByUserId() == null) {
            throw new IllegalArgumentException("Creator is required.");
        }
    }

    private int sanitizeMax(Integer max) {
        if (max == null || max < 1) {
            return 100;
        }
        return Math.min(max, 1000);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_PENDING;
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case STATUS_APPROVED -> STATUS_APPROVED;
            case STATUS_REJECTED -> STATUS_REJECTED;
            default -> STATUS_PENDING;
        };
    }
}
