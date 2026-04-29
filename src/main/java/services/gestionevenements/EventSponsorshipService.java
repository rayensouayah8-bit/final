package services.gestionevenements;

import models.gestionevenements.EventSponsorship;
import services.CRUD;
import utils.DbConnexion;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EventSponsorshipService implements CRUD<EventSponsorship, Long> {

    public static final String STATUS_PENDING = "PENDING_SPONSOR";
    public static final String STATUS_APPROVED = "APPROVED_SPONSOR";
    public static final String STATUS_REJECTED = "REJECTED_SPONSOR";

    private static final String INSERT = """
            INSERT INTO event_sponsorship (
                nom, email, telephone, montant_contribution, message,
                statut, is_paid, sponsored_at, evenement_id, user_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_PENDING_BY_OWNER_EVENT = """
            SELECT es.id, es.nom, es.email, es.telephone, es.montant_contribution, es.message,
                   es.statut, es.is_paid, es.sponsored_at, es.evenement_id, es.user_id
            FROM event_sponsorship es
            JOIN travel_event te ON te.id = es.evenement_id
            WHERE te.id = ? AND te.created_by_user_id = ? AND es.statut = ?
            ORDER BY es.sponsored_at ASC
            """;

    @Override
    public void create(EventSponsorship entity) throws SQLException {
        insert(entity);
    }

    @Override
    public void insert(EventSponsorship entity) throws SQLException {
        validate(entity, true);
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entity.getNom().trim());
            ps.setString(2, entity.getEmail().trim().toLowerCase());
            nullableString(ps, 3, trimToNull(entity.getTelephone()));
            ps.setBigDecimal(4, entity.getMontantContribution());
            nullableString(ps, 5, trimToNull(entity.getMessage()));
            ps.setString(6, normalizeStatus(entity.getStatut()));
            ps.setBoolean(7, entity.getIsPaid() != null && entity.getIsPaid());
            ps.setTimestamp(8, Timestamp.valueOf(entity.getSponsoredAt()));
            ps.setLong(9, entity.getEvenementId());
            nullableInt(ps, 10, entity.getUserId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    entity.setId(keys.getLong(1));
                }
            }
        }
    }

    @Override
    public void update(EventSponsorship entity) throws SQLException {
        validate(entity, false);
        String sql = """
                UPDATE event_sponsorship
                SET nom = ?, email = ?, telephone = ?, montant_contribution = ?, message = ?, statut = ?, is_paid = ?, sponsored_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entity.getNom().trim());
            ps.setString(2, entity.getEmail().trim().toLowerCase());
            nullableString(ps, 3, trimToNull(entity.getTelephone()));
            ps.setBigDecimal(4, entity.getMontantContribution());
            nullableString(ps, 5, trimToNull(entity.getMessage()));
            ps.setString(6, normalizeStatus(entity.getStatut()));
            ps.setBoolean(7, entity.getIsPaid() != null && entity.getIsPaid());
            ps.setLong(8, entity.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(Long id) throws SQLException {
        if (id == null) {
            return;
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM event_sponsorship WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public int countApprovedByEvent(Long eventId) throws SQLException {
        if (eventId == null) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM event_sponsorship WHERE evenement_id = ? AND statut = ?";
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

    public List<EventSponsorship> findPendingByEventOwner(Long eventId, Integer ownerUserId) throws SQLException {
        if (eventId == null || ownerUserId == null) {
            return List.of();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        List<EventSponsorship> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_PENDING_BY_OWNER_EVENT)) {
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

    public List<EventSponsorship> findByUser(Integer userId) throws SQLException {
        if (userId == null) {
            return List.of();
        }
        String sql = """
                SELECT id, nom, email, telephone, montant_contribution, message,
                       statut, is_paid, sponsored_at, evenement_id, user_id
                FROM event_sponsorship
                WHERE user_id = ?
                ORDER BY sponsored_at DESC, id DESC
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        List<EventSponsorship> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public Optional<EventSponsorship> findLatestByEventAndUser(Long eventId, Integer userId) throws SQLException {
        if (eventId == null || userId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, nom, email, telephone, montant_contribution, message,
                       statut, is_paid, sponsored_at, evenement_id, user_id
                FROM event_sponsorship
                WHERE evenement_id = ? AND user_id = ?
                ORDER BY sponsored_at DESC, id DESC
                LIMIT 1
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
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

    public void deleteByEventAndUser(Long eventId, Integer userId) throws SQLException {
        if (eventId == null || userId == null) {
            throw new IllegalArgumentException("Evenement et utilisateur requis.");
        }
        String sql = "DELETE FROM event_sponsorship WHERE evenement_id = ? AND user_id = ?";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void approveSponsorship(Long sponsorshipId, Integer ownerUserId) throws SQLException {
        updateByOwner(sponsorshipId, ownerUserId, STATUS_APPROVED);
    }

    public void rejectSponsorship(Long sponsorshipId, Integer ownerUserId) throws SQLException {
        updateByOwner(sponsorshipId, ownerUserId, STATUS_REJECTED);
    }

    private void updateByOwner(Long sponsorshipId, Integer ownerUserId, String status) throws SQLException {
        if (sponsorshipId == null || ownerUserId == null) {
            return;
        }
        String sql = """
                UPDATE event_sponsorship es
                JOIN travel_event te ON te.id = es.evenement_id
                SET es.statut = ?
                WHERE es.id = ? AND te.created_by_user_id = ?
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, normalizeStatus(status));
            ps.setLong(2, sponsorshipId);
            ps.setInt(3, ownerUserId);
            ps.executeUpdate();
        }
    }

    private void validate(EventSponsorship e, boolean createMode) {
        if (e == null) {
            throw new IllegalArgumentException("Les donnees du sponsoring sont obligatoires.");
        }
        e.validateForPersistence(createMode);
        if (e.getNom() == null || e.getNom().isBlank()) {
            throw new IllegalArgumentException("Le nom complet est obligatoire.");
        }
        if (e.getEmail() == null || e.getEmail().isBlank()) {
            throw new IllegalArgumentException("L'email est obligatoire.");
        }
        BigDecimal amount = e.getMontantContribution();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant de contribution doit etre positif.");
        }
        if (e.getEvenementId() == null) {
            throw new IllegalArgumentException("L'identifiant de l'evenement est obligatoire.");
        }
        e.setNom(e.getNom().trim());
        e.setEmail(e.getEmail().trim().toLowerCase());
        e.setTelephone(trimToNull(e.getTelephone()));
        e.setMessage(trimToNull(e.getMessage()));
        if (e.getSponsoredAt() == null) {
            e.setSponsoredAt(java.time.LocalDateTime.now());
        }
        if (e.getStatut() == null || e.getStatut().isBlank()) {
            e.setStatut(STATUS_PENDING);
        } else {
            e.setStatut(normalizeStatus(e.getStatut()));
        }
        if (e.getIsPaid() == null) {
            e.setIsPaid(false);
        }
    }

    private EventSponsorship mapRow(ResultSet rs) throws SQLException {
        EventSponsorship e = new EventSponsorship();
        e.setId(rs.getLong("id"));
        e.setNom(rs.getString("nom"));
        e.setEmail(rs.getString("email"));
        e.setTelephone(rs.getString("telephone"));
        e.setMontantContribution(rs.getBigDecimal("montant_contribution"));
        e.setMessage(rs.getString("message"));
        e.setStatut(normalizeStatus(rs.getString("statut")));
        e.setIsPaid(rs.getBoolean("is_paid"));
        Timestamp ts = rs.getTimestamp("sponsored_at");
        e.setSponsoredAt(ts != null ? ts.toLocalDateTime() : LocalDateTime.now());
        e.setEvenementId(rs.getLong("evenement_id"));
        int uid = rs.getInt("user_id");
        e.setUserId(rs.wasNull() ? null : uid);
        return e;
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

    private static void nullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void nullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
