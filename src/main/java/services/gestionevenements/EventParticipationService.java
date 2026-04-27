package services.gestionevenements;

import models.gestionevenements.EventParticipation;
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

public class EventParticipationService implements CRUD<EventParticipation, Long> {

    private static final String INSERT = """
            INSERT INTO event_participation (event_id, user_id, status)
            VALUES (?, ?, ?)
            """;

    private static final String UPDATE = """
            UPDATE event_participation SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

    private static final String DELETE = "DELETE FROM event_participation WHERE id = ?";

    private static final String SELECT_BY_ID = """
            SELECT id, event_id, user_id, status, joined_at, updated_at
            FROM event_participation WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, event_id, user_id, status, joined_at, updated_at
            FROM event_participation ORDER BY id DESC
            """;

    private static final String SELECT_BY_EVENT_USER = """
            SELECT id, event_id, user_id, status, joined_at, updated_at
            FROM event_participation WHERE event_id = ? AND user_id = ?
            """;

    private static final String UPSERT_STATUS = """
            INSERT INTO event_participation (event_id, user_id, status)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE status = VALUES(status), updated_at = CURRENT_TIMESTAMP
            """;

    @Override
    public void create(EventParticipation entity) throws SQLException {
        insert(entity);
    }

    @Override
    public void insert(EventParticipation entity) throws SQLException {
        validate(entity, true);
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, entity.getEventId());
            ps.setInt(2, entity.getUserId());
            ps.setString(3, normalizeStatus(entity.getStatus()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    entity.setId(keys.getLong(1));
                }
            }
        }
    }

    @Override
    public void update(EventParticipation entity) throws SQLException {
        validate(entity, false);
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(UPDATE)) {
            ps.setString(1, normalizeStatus(entity.getStatus()));
            ps.setLong(2, entity.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(Long id) throws SQLException {
        if (id == null) {
            return;
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public Optional<EventParticipation> get(Long id) throws SQLException {
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

    public List<EventParticipation> findAll() throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        List<EventParticipation> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public void participate(Long eventId, Integer userId) throws SQLException {
        if (eventId == null || userId == null) {
            return;
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(UPSERT_STATUS)) {
            ps.setLong(1, eventId);
            ps.setInt(2, userId);
            ps.setString(3, "PARTICIPATING");
            ps.executeUpdate();
        }
    }

    public void cancelParticipation(Long eventId, Integer userId) throws SQLException {
        if (eventId == null || userId == null) {
            return;
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(UPSERT_STATUS)) {
            ps.setLong(1, eventId);
            ps.setInt(2, userId);
            ps.setString(3, "CANCELLED");
            ps.executeUpdate();
        }
    }

    public boolean isParticipating(Long eventId, Integer userId) throws SQLException {
        Optional<EventParticipation> p = findByEventAndUser(eventId, userId);
        return p.isPresent() && "PARTICIPATING".equalsIgnoreCase(p.get().getStatus());
    }

    public Optional<EventParticipation> findByEventAndUser(Long eventId, Integer userId) throws SQLException {
        if (eventId == null || userId == null) {
            return Optional.empty();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_EVENT_USER)) {
            ps.setLong(1, eventId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public int countParticipants(Long eventId) throws SQLException {
        if (eventId == null) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM event_participation WHERE event_id = ? AND status = 'PARTICIPATING'";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private EventParticipation mapRow(ResultSet rs) throws SQLException {
        EventParticipation e = new EventParticipation();
        e.setId(rs.getLong("id"));
        e.setEventId(rs.getLong("event_id"));
        e.setUserId(rs.getInt("user_id"));
        e.setStatus(rs.getString("status"));
        Timestamp joined = rs.getTimestamp("joined_at");
        e.setJoinedAt(joined != null ? joined.toLocalDateTime() : LocalDateTime.now());
        Timestamp updated = rs.getTimestamp("updated_at");
        e.setUpdatedAt(updated != null ? updated.toLocalDateTime() : LocalDateTime.now());
        return e;
    }

    private void validate(EventParticipation e, boolean insert) {
        if (e == null) {
            throw new IllegalArgumentException("participation is required");
        }
        if (!insert && e.getId() == null) {
            throw new IllegalArgumentException("id is required for update");
        }
        if (insert && (e.getEventId() == null || e.getUserId() == null)) {
            throw new IllegalArgumentException("event_id and user_id are required");
        }
    }

    private String normalizeStatus(String s) {
        if (s == null || s.isBlank()) {
            return "PARTICIPATING";
        }
        String normalized = s.trim().toUpperCase();
        if (!"PARTICIPATING".equals(normalized) && !"CANCELLED".equals(normalized)) {
            return "PARTICIPATING";
        }
        return normalized;
    }
}
