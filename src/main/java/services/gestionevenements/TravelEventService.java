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

    private static final String INSERT = """
            INSERT INTO travel_event (
                title, description, location, event_date, max_participants, image_path, created_by_user_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE = """
            UPDATE travel_event SET
                title = ?, description = ?, location = ?, event_date = ?, max_participants = ?, image_path = ?
            WHERE id = ?
            """;

    private static final String DELETE = "DELETE FROM travel_event WHERE id = ?";

    private static final String SELECT_BY_ID = """
            SELECT id, title, description, location, event_date, max_participants, image_path,
                   created_by_user_id, created_at, updated_at
            FROM travel_event WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, title, description, location, event_date, max_participants, image_path,
                   created_by_user_id, created_at, updated_at
            FROM travel_event ORDER BY event_date DESC, id DESC
            """;

        private static final String SELECT_BY_CREATOR = """
             SELECT id, title, description, location, event_date, max_participants, image_path,
                 created_by_user_id, created_at, updated_at
             FROM travel_event
             WHERE created_by_user_id = ?
             ORDER BY event_date DESC, id DESC
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
            ps.setInt(7, entity.getCreatedByUserId());
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
        if (!insert && e.getId() == null) {
            throw new IllegalArgumentException("id is required for update");
        }
        if (e.getTitle() == null || e.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (e.getLocation() == null || e.getLocation().isBlank()) {
            throw new IllegalArgumentException("Location is required.");
        }
        if (e.getEventDate() == null) {
            throw new IllegalArgumentException("Event date is required.");
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
}
