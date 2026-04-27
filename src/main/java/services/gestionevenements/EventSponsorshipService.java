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

public class EventSponsorshipService implements CRUD<EventSponsorship, Long> {

    private static final String INSERT = """
            INSERT INTO event_sponsorship (
                nom, email, telephone, montant_contribution, message,
                statut, is_paid, sponsored_at, evenement_id, user_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Override
    public void create(EventSponsorship entity) throws SQLException {
        insert(entity);
    }

    @Override
    public void insert(EventSponsorship entity) throws SQLException {
        validate(entity);
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entity.getNom().trim());
            ps.setString(2, entity.getEmail().trim());
            nullableString(ps, 3, trimToNull(entity.getTelephone()));
            ps.setBigDecimal(4, entity.getMontantContribution());
            nullableString(ps, 5, trimToNull(entity.getMessage()));
            ps.setString(6, entity.getStatut() == null || entity.getStatut().isBlank() ? "en_attente" : entity.getStatut());
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
        throw new UnsupportedOperationException("Update sponsorship is not supported in this flow.");
    }

    @Override
    public void delete(Long id) throws SQLException {
        throw new UnsupportedOperationException("Delete sponsorship is not supported in this flow.");
    }

    private void validate(EventSponsorship e) {
        if (e == null) {
            throw new IllegalArgumentException("Sponsorship payload is required.");
        }
        if (e.getNom() == null || e.getNom().isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (e.getEmail() == null || e.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        BigDecimal amount = e.getMontantContribution();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Contribution amount must be positive.");
        }
        if (e.getEvenementId() == null) {
            throw new IllegalArgumentException("Event ID is required.");
        }
        if (e.getSponsoredAt() == null) {
            e.setSponsoredAt(java.time.LocalDateTime.now());
        }
        if (e.getStatut() == null || e.getStatut().isBlank()) {
            e.setStatut("en_attente");
        }
        if (e.getIsPaid() == null) {
            e.setIsPaid(false);
        }
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
