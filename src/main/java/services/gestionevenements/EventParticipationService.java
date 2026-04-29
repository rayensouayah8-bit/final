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

    public static final String STATUS_PENDING = "PENDING_PARTICIPATION";
    public static final String STATUS_APPROVED = "APPROVED_PARTICIPATION";
    public static final String STATUS_REJECTED = "REJECTED_PARTICIPATION";

    private static final String INSERT = """
            INSERT INTO event_participation (event_id, user_id, status)
            VALUES (?, ?, ?)
            """;

    private static final String INSERT_WITH_FORM = """
            INSERT INTO event_participation (event_id, user_id, status, requester_name, contact_phone, request_note)
            VALUES (?, ?, ?, ?, ?, ?)
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

    private static final String SELECT_BY_ID_FULL = """
            SELECT id, event_id, user_id, status, requester_name, contact_phone, request_note, joined_at, updated_at
            FROM event_participation WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, event_id, user_id, status, joined_at, updated_at
            FROM event_participation ORDER BY id DESC
            """;

    private static final String SELECT_ALL_FULL = """
            SELECT id, event_id, user_id, status, requester_name, contact_phone, request_note, joined_at, updated_at
            FROM event_participation ORDER BY id DESC
            """;

    private static final String SELECT_BY_EVENT_USER = """
            SELECT id, event_id, user_id, status, joined_at, updated_at
            FROM event_participation WHERE event_id = ? AND user_id = ?
            """;

            private static final String SELECT_BY_EVENT_USER_FULL = """
                    SELECT id, event_id, user_id, status, requester_name, contact_phone, request_note, joined_at, updated_at
                FROM event_participation WHERE event_id = ? AND user_id = ?
                """;

            private static final String SELECT_BY_USER_FULL = """
                    SELECT id, event_id, user_id, status, requester_name, contact_phone, request_note, joined_at, updated_at
                FROM event_participation
                WHERE user_id = ?
                ORDER BY updated_at DESC, id DESC
                """;

    private static final String UPSERT_STATUS = """
            INSERT INTO event_participation (event_id, user_id, status)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE status = VALUES(status), updated_at = CURRENT_TIMESTAMP
            """;

    private static final String UPSERT_WITH_FORM = """
            INSERT INTO event_participation (event_id, user_id, status, requester_name, contact_phone, request_note)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                requester_name = VALUES(requester_name),
                contact_phone = VALUES(contact_phone),
                request_note = VALUES(request_note),
                updated_at = CURRENT_TIMESTAMP
            """;

        private static final String SELECT_PENDING_BY_EVENT_OWNER = """
            SELECT ep.id, ep.event_id, ep.user_id, ep.status, ep.joined_at, ep.updated_at
            FROM event_participation ep
            JOIN travel_event te ON te.id = ep.event_id
            WHERE te.id = ? AND te.created_by_user_id = ? AND ep.status = ?
            ORDER BY ep.joined_at ASC
            """;

    @Override
    public void create(EventParticipation entity) throws SQLException {
        insert(entity);
    }

    @Override
    public void insert(EventParticipation entity) throws SQLException {
        validate(entity, true);
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(INSERT_WITH_FORM, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, entity.getEventId());
            ps.setInt(2, entity.getUserId());
            ps.setString(3, normalizeStatus(entity.getStatus()));
            ps.setString(4, entity.getRequesterName());
            ps.setString(5, entity.getContactPhone());
            ps.setString(6, entity.getRequestNote());
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
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_ID_FULL)) {
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
        try (PreparedStatement ps = c.prepareStatement(SELECT_ALL_FULL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public void participate(Long eventId, Integer userId) throws SQLException {
        if (eventId == null || userId == null) {
            throw new IllegalArgumentException("L'evenement et l'utilisateur sont obligatoires.");
        }
        EventParticipation request = new EventParticipation();
        request.setEventId(eventId);
        request.setUserId(userId);
        request.setStatus(STATUS_PENDING);
        request.setRequestNote("Participation request");
        request.setContactPhone(null);
        participate(request);
    }

    public void participate(EventParticipation request) throws SQLException {
        validate(request, true);
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(UPSERT_WITH_FORM)) {
            ps.setLong(1, request.getEventId());
            ps.setInt(2, request.getUserId());
            ps.setString(3, STATUS_PENDING);
            ps.setString(4, trimToNull(request.getRequesterName()));
            ps.setString(5, trimToNull(request.getContactPhone()));
            ps.setString(6, trimToNull(request.getRequestNote()));
            ps.executeUpdate();
        }
    }

    public void cancelParticipation(Long eventId, Integer userId) throws SQLException {
        if (eventId == null || userId == null) {
            return;
        }
        String sql = "DELETE FROM event_participation WHERE event_id = ? AND user_id = ?";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public boolean isParticipating(Long eventId, Integer userId) throws SQLException {
        Optional<EventParticipation> p = findByEventAndUser(eventId, userId);
        return p.isPresent() && STATUS_APPROVED.equalsIgnoreCase(p.get().getStatus());
    }

    public boolean isPending(Long eventId, Integer userId) throws SQLException {
        Optional<EventParticipation> p = findByEventAndUser(eventId, userId);
        return p.isPresent() && STATUS_PENDING.equalsIgnoreCase(p.get().getStatus());
    }

    public Optional<EventParticipation> findByEventAndUser(Long eventId, Integer userId) throws SQLException {
        if (eventId == null || userId == null) {
            return Optional.empty();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_EVENT_USER_FULL)) {
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

    public List<EventParticipation> findByUser(Integer userId) throws SQLException {
        if (userId == null) {
            return List.of();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        List<EventParticipation> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_USER_FULL)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public int countParticipants(Long eventId) throws SQLException {
        if (eventId == null) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM event_participation WHERE event_id = ? AND status = ?";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setString(2, STATUS_APPROVED);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public List<EventParticipation> findPendingByEventOwner(Long eventId, Integer ownerUserId) throws SQLException {
        if (eventId == null || ownerUserId == null) {
            return List.of();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        List<EventParticipation> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_PENDING_BY_EVENT_OWNER)) {
            ps.setLong(1, eventId);
            ps.setInt(2, ownerUserId);
            ps.setString(3, STATUS_PENDING);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public void approveParticipation(Long participationId, Integer ownerUserId) throws SQLException {
        updateByOwner(participationId, ownerUserId, STATUS_APPROVED);
    }

    public void rejectParticipation(Long participationId, Integer ownerUserId) throws SQLException {
        updateByOwner(participationId, ownerUserId, STATUS_REJECTED);
    }

    private void updateByOwner(Long participationId, Integer ownerUserId, String newStatus) throws SQLException {
        if (participationId == null || ownerUserId == null) {
            return;
        }
        String sql = """
                UPDATE event_participation ep
                JOIN travel_event te ON te.id = ep.event_id
                SET ep.status = ?, ep.updated_at = CURRENT_TIMESTAMP
                WHERE ep.id = ? AND te.created_by_user_id = ?
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, normalizeStatus(newStatus));
            ps.setLong(2, participationId);
            ps.setInt(3, ownerUserId);
            ps.executeUpdate();
        }
    }

    private EventParticipation mapRow(ResultSet rs) throws SQLException {
        EventParticipation e = new EventParticipation();
        e.setId(rs.getLong("id"));
        e.setEventId(rs.getLong("event_id"));
        e.setUserId(rs.getInt("user_id"));
        e.setStatus(rs.getString("status"));
        e.setRequesterName(readNullableColumn(rs, "requester_name"));
        e.setContactPhone(readNullableColumn(rs, "contact_phone"));
        e.setRequestNote(readNullableColumn(rs, "request_note"));
        Timestamp joined = rs.getTimestamp("joined_at");
        e.setJoinedAt(joined != null ? joined.toLocalDateTime() : LocalDateTime.now());
        Timestamp updated = rs.getTimestamp("updated_at");
        e.setUpdatedAt(updated != null ? updated.toLocalDateTime() : LocalDateTime.now());
        return e;
    }

    private void validate(EventParticipation e, boolean insert) {
        if (e == null) {
            throw new IllegalArgumentException("La participation est obligatoire.");
        }
        e.validateForPersistence(insert);
        if (!insert && e.getId() == null) {
            throw new IllegalArgumentException("L'identifiant est obligatoire pour la modification.");
        }
        if (insert && (e.getEventId() == null || e.getUserId() == null)) {
            throw new IllegalArgumentException("L'evenement et l'utilisateur sont obligatoires.");
        }
    }

    private String normalizeStatus(String s) {
        if (s == null || s.isBlank()) {
            return STATUS_PENDING;
        }
        String normalized = s.trim().toUpperCase();
        return switch (normalized) {
            case STATUS_APPROVED -> STATUS_APPROVED;
            case STATUS_REJECTED -> STATUS_REJECTED;
            default -> STATUS_PENDING;
        };
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String readNullableColumn(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}
