package services;

import models.gestionoffres.TravelOffer;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class ServiceTravelOffer implements IService<TravelOffer> {
    private static final String OFFER_IMAGE_DIR = "src/main/resources/images/offers";

    @Override
    public void add(TravelOffer offer) throws SQLException {
        String sql = """
                INSERT INTO travel_offer (
                    title, countries, description, departure_date, return_date, price, currency,
                    available_seats, image, agency_id, created_by_id, approval_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindCommonFields(ps, offer);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    offer.setId(rs.getLong(1));
                }
            }
        }
    }

    @Override
    public void update(TravelOffer offer) throws SQLException {
        String sql = """
                UPDATE travel_offer
                SET title=?, countries=?, description=?, departure_date=?, return_date=?, price=?, currency=?,
                    available_seats=?, image=?, agency_id=?, created_by_id=?, approval_status=?
                WHERE id=?
                """;
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            bindCommonFields(ps, offer);
            ps.setLong(13, offer.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(TravelOffer offer) throws SQLException {
        if (offer == null || offer.getId() == null) {
            return;
        }
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement("DELETE FROM travel_offer WHERE id=?")) {
            ps.setLong(1, offer.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public List<TravelOffer> getAll() throws SQLException {
        return fetchMany("SELECT * FROM travel_offer ORDER BY created_at DESC");
    }

    public TravelOffer getById(Long id) throws SQLException {
        if (id == null) {
            return null;
        }
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement("SELECT * FROM travel_offer WHERE id=? LIMIT 1")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapOffer(rs) : null;
            }
        }
    }

    public List<TravelOffer> getByAgency(Long agencyId) throws SQLException {
        if (agencyId == null) {
            return List.of();
        }
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement("SELECT * FROM travel_offer WHERE agency_id=? ORDER BY created_at DESC")) {
            ps.setLong(1, agencyId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TravelOffer> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapOffer(rs));
                }
                return list;
            }
        }
    }

    public List<TravelOffer> getApproved() throws SQLException {
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement("SELECT * FROM travel_offer WHERE approval_status='approved' ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            List<TravelOffer> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapOffer(rs));
            }
            return list;
        }
    }

    private List<TravelOffer> fetchMany(String sql) throws SQLException {
        try (Connection cnx = DbConnexion.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<TravelOffer> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapOffer(rs));
            }
            return list;
        }
    }

    private void bindCommonFields(PreparedStatement ps, TravelOffer offer) throws SQLException {
        ps.setString(1, offer.getTitle());
        ps.setString(2, toJsonArray(offer.getCountries()));
        ps.setString(3, offer.getDescription());
        if (offer.getDepartureDate() != null) {
            ps.setTimestamp(4, Timestamp.valueOf(offer.getDepartureDate()));
        } else {
            ps.setTimestamp(4, null);
        }
        if (offer.getReturnDate() != null) {
            ps.setTimestamp(5, Timestamp.valueOf(offer.getReturnDate()));
        } else {
            ps.setTimestamp(5, null);
        }
        if (offer.getPrice() != null) {
            ps.setDouble(6, offer.getPrice());
        } else {
            ps.setNull(6, java.sql.Types.DOUBLE);
        }
        ps.setString(7, offer.getCurrency());
        if (offer.getAvailableSeats() != null) {
            ps.setInt(8, offer.getAvailableSeats());
        } else {
            ps.setNull(8, java.sql.Types.INTEGER);
        }
        ps.setString(9, offer.getImage());
        if (offer.getAgencyId() != null) {
            ps.setLong(10, offer.getAgencyId());
        } else {
            ps.setNull(10, java.sql.Types.BIGINT);
        }
        if (offer.getCreatedById() != null) {
            ps.setLong(11, offer.getCreatedById());
        } else {
            ps.setNull(11, java.sql.Types.BIGINT);
        }
        ps.setString(12, normalizeStatus(offer.getApprovalStatus()));
    }

    private TravelOffer mapOffer(ResultSet rs) throws SQLException {
        Timestamp departureTs = rs.getTimestamp("departure_date");
        Timestamp returnTs = rs.getTimestamp("return_date");
        Timestamp createdTs = rs.getTimestamp("created_at");
        return new TravelOffer(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("countries"),
                rs.getString("description"),
                departureTs != null ? departureTs.toLocalDateTime() : null,
                returnTs != null ? returnTs.toLocalDateTime() : null,
                rs.getObject("price") != null ? rs.getDouble("price") : null,
                rs.getString("currency"),
                rs.getObject("available_seats") != null ? rs.getInt("available_seats") : null,
                rs.getString("image"),
                rs.getObject("agency_id") != null ? rs.getLong("agency_id") : null,
                rs.getObject("created_by_id") != null ? rs.getLong("created_by_id") : null,
                createdTs != null ? createdTs.toLocalDateTime() : null,
                rs.getString("approval_status")
        );
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "pending";
        }
        String normalized = status.trim().toLowerCase();
        return switch (normalized) {
            case "approved" -> "approved";
            case "rejected" -> "rejected";
            default -> "pending";
        };
    }

    private String toJsonArray(String csv) {
        if (csv == null || csv.isBlank()) {
            return "[]";
        }
        if (csv.trim().startsWith("[")) {
            return csv;
        }
        String[] parts = csv.split(",");
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < parts.length; i++) {
            sb.append("\"").append(parts[i].trim()).append("\"");
            if (i < parts.length - 1) {
                sb.append(",");
            }
        }
        return sb.append("]").toString();
    }

    public String saveImage(File file) throws IOException {
        if (file == null || !file.exists()) {
            return null;
        }
        Path directory = Path.of(OFFER_IMAGE_DIR);
        Files.createDirectories(directory);
        String extension = "";
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        if (idx >= 0) {
            extension = name.substring(idx);
        }
        String generated = UUID.randomUUID() + extension;
        Path target = directory.resolve(generated);
        Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return "/images/offers/" + generated;
    }
}
