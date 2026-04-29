package services;

import models.gestionoffres.Reservation;
import models.gestionoffres.TravelOffer;
import utils.DbConnexion;

import java.sql.Connection;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ServiceReservation implements IService<Reservation> {
    private static final String BASE_JOIN = """
            SELECT r.*, t.id AS offer_id, t.title, t.countries, t.description,
                   t.departure_date, t.return_date, t.price, t.currency,
                   t.available_seats, t.image, t.agency_id, t.created_by_id,
                   t.created_at AS offer_created_at, t.approval_status
            FROM reservation r JOIN travel_offer t ON r.offer_id = t.id
            """;

    @Override
    public void add(Reservation reservation) throws SQLException {
        if (reservation == null || reservation.getOffer() == null || reservation.getOffer().getId() == null) {
            throw new IllegalArgumentException("Reservation and offer id are required.");
        }
        if (existsForOfferAndUser(reservation.getOffer().getId(), reservation.getUserId())) {
            throw new IllegalArgumentException("You already have a reservation for this offer");
        }
        TravelOffer dbOffer = new ServiceTravelOffer().getById(reservation.getOffer().getId());
        if (dbOffer == null) {
            throw new IllegalArgumentException("Offer not found.");
        }
        int availableSeats = dbOffer.getAvailableSeats() == null ? 0 : dbOffer.getAvailableSeats();
        int requestedSeats = reservation.getReservedSeats() == null ? 1 : reservation.getReservedSeats();
        if (availableSeats <= 0 || requestedSeats > availableSeats) {
            throw new IllegalArgumentException("Not enough available seats for this offer.");
        }
        String sql = """
                INSERT INTO reservation (offer_id, user_id, contact_info, reserved_seats, reservation_date, status, is_paid)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setLong(1, reservation.getOffer().getId());
            ps.setLong(2, reservation.getUserId());
            ps.setString(3, reservation.getContactInfo());
            ps.setInt(4, reservation.getReservedSeats() == null ? 1 : reservation.getReservedSeats());
            LocalDateTime date = reservation.getReservationDate() != null ? reservation.getReservationDate() : LocalDateTime.now();
            ps.setTimestamp(5, Timestamp.valueOf(date));
            ps.setString(6, normalizeStatus(reservation.getStatus()));
            ps.setBoolean(7, Boolean.TRUE.equals(reservation.getIsPaid()));
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException ex) {
            throw new IllegalArgumentException("You already have a reservation for this offer");
        }
    }

    @Override
    public void update(Reservation reservation) throws SQLException {
        String sql = """
                UPDATE reservation
                SET contact_info=?, reserved_seats=?, status=?, is_paid=?
                WHERE id=?
                """;
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, reservation.getContactInfo());
            ps.setInt(2, reservation.getReservedSeats() == null ? 1 : reservation.getReservedSeats());
            ps.setString(3, normalizeStatus(reservation.getStatus()));
            ps.setBoolean(4, Boolean.TRUE.equals(reservation.getIsPaid()));
            ps.setLong(5, reservation.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(Reservation reservation) throws SQLException {
        if (reservation == null || reservation.getId() == null) {
            return;
        }
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement("DELETE FROM reservation WHERE id=?")) {
            ps.setLong(1, reservation.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public List<Reservation> getAll() throws SQLException {
        return runJoinQuery(BASE_JOIN + " ORDER BY r.reservation_date DESC", null);
    }

    public List<Reservation> getByOffer(Long offerId) throws SQLException {
        if (offerId == null) {
            return List.of();
        }
        return runJoinQuery(BASE_JOIN + " WHERE r.offer_id=? ORDER BY r.reservation_date DESC", ps -> ps.setLong(1, offerId));
    }

    public List<Reservation> getByUser(Long userId) throws SQLException {
        if (userId == null) {
            return List.of();
        }
        return runJoinQuery(BASE_JOIN + " WHERE r.user_id=? ORDER BY r.reservation_date DESC", ps -> ps.setLong(1, userId));
    }

    public List<Reservation> getByAgency(Long agencyId) throws SQLException {
        if (agencyId == null) {
            return List.of();
        }
        return runJoinQuery(BASE_JOIN + " WHERE t.agency_id=? ORDER BY r.reservation_date DESC", ps -> ps.setLong(1, agencyId));
    }

    public Reservation getUserReservationForOffer(Long userId, Long offerId) throws SQLException {
        if (userId == null || offerId == null) {
            return null;
        }
        List<Reservation> list = runJoinQuery(BASE_JOIN + " WHERE r.user_id=? AND r.offer_id=? LIMIT 1", ps -> {
            ps.setLong(1, userId);
            ps.setLong(2, offerId);
        });
        return list.isEmpty() ? null : list.get(0);
    }

    public void confirm(Long id) throws SQLException {
        if (id == null) {
            return;
        }
        Reservation reservation = getById(id);
        if (reservation == null || reservation.getOffer() == null || reservation.getOffer().getId() == null) {
            return;
        }
        int seats = reservation.getReservedSeats() == null ? 0 : reservation.getReservedSeats();
        try (Connection cnx = DbConnexion.getInstance().getConnection()) {
            cnx.setAutoCommit(false);
            try (PreparedStatement psStatus = cnx.prepareStatement("UPDATE reservation SET status='confirmed' WHERE id=?");
                 PreparedStatement psSeats = cnx.prepareStatement("UPDATE travel_offer SET available_seats = GREATEST(available_seats - ?, 0) WHERE id=?")) {
                psStatus.setLong(1, id);
                psStatus.executeUpdate();
                psSeats.setInt(1, seats);
                psSeats.setLong(2, reservation.getOffer().getId());
                psSeats.executeUpdate();
            }
            cnx.commit();
        }
    }

    public void cancel(Long id) throws SQLException {
        updateStatus(id, Reservation.STATUS_CANCELLED);
    }

    private void updateStatus(Long id, String status) throws SQLException {
        if (id == null) {
            return;
        }
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement("UPDATE reservation SET status=? WHERE id=?")) {
            ps.setString(1, normalizeStatus(status));
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private boolean existsForOfferAndUser(Long offerId, Long userId) throws SQLException {
        if (offerId == null || userId == null) {
            return false;
        }
        String sql = "SELECT id FROM reservation WHERE offer_id=? AND user_id=? LIMIT 1";
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setLong(1, offerId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Reservation getById(Long id) throws SQLException {
        if (id == null) {
            return null;
        }
        List<Reservation> list = runJoinQuery(BASE_JOIN + " WHERE r.id=? LIMIT 1", ps -> ps.setLong(1, id));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<Reservation> runJoinQuery(String sql, StatementBinder binder) throws SQLException {
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Reservation> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
                return list;
            }
        }
    }

    private Reservation mapResultSet(ResultSet rs) throws SQLException {
        Timestamp depTs = rs.getTimestamp("departure_date");
        Timestamp retTs = rs.getTimestamp("return_date");
        Timestamp offerCreatedTs = rs.getTimestamp("offer_created_at");
        TravelOffer offer = new TravelOffer(
                rs.getLong("offer_id"),
                rs.getString("title"),
                rs.getString("countries"),
                rs.getString("description"),
                depTs != null ? depTs.toLocalDateTime() : null,
                retTs != null ? retTs.toLocalDateTime() : null,
                rs.getObject("price") != null ? rs.getDouble("price") : null,
                rs.getString("currency"),
                rs.getObject("available_seats") != null ? rs.getInt("available_seats") : null,
                rs.getString("image"),
                rs.getObject("agency_id") != null ? rs.getLong("agency_id") : null,
                rs.getObject("created_by_id") != null ? rs.getLong("created_by_id") : null,
                offerCreatedTs != null ? offerCreatedTs.toLocalDateTime() : null,
                rs.getString("approval_status")
        );
        Reservation reservation = new Reservation();
        reservation.setId(rs.getLong("id"));
        reservation.setOffer(offer);
        reservation.setUserId(rs.getLong("user_id"));
        reservation.setContactInfo(rs.getString("contact_info"));
        reservation.setReservedSeats(rs.getInt("reserved_seats"));
        Timestamp reservationTs = rs.getTimestamp("reservation_date");
        reservation.setReservationDate(reservationTs != null ? reservationTs.toLocalDateTime() : null);
        reservation.setStatus(normalizeStatus(rs.getString("status")));
        reservation.setIsPaid(rs.getBoolean("is_paid"));
        return reservation;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return Reservation.STATUS_PENDING;
        }
        String normalized = status.trim().toLowerCase();
        return switch (normalized) {
            case Reservation.STATUS_CONFIRMED -> Reservation.STATUS_CONFIRMED;
            case Reservation.STATUS_CANCELLED -> Reservation.STATUS_CANCELLED;
            default -> Reservation.STATUS_PENDING;
        };
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement preparedStatement) throws SQLException;
    }
}
