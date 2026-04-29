package services.gestionevenements;

import utils.DbConnexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class EventLikeService {

    private static final String COUNT_BY_EVENT = "SELECT COUNT(*) FROM event_like WHERE event_id = ?";
    private static final String EXISTS_LIKE = "SELECT 1 FROM event_like WHERE event_id = ? AND user_id = ?";
    private static final String INSERT_LIKE = "INSERT INTO event_like (event_id, user_id) VALUES (?, ?)";
    private static final String DELETE_LIKE = "DELETE FROM event_like WHERE event_id = ? AND user_id = ?";
    private static final String SELECT_LIKED_IDS_BY_USER = "SELECT event_id FROM event_like WHERE user_id = ?";

    public int countByEvent(Long eventId) throws SQLException {
        if (eventId == null) {
            return 0;
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_BY_EVENT)) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public boolean hasLiked(Long eventId, Integer userId) throws SQLException {
        if (eventId == null || userId == null) {
            return false;
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(EXISTS_LIKE)) {
            ps.setLong(1, eventId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean toggleLike(Long eventId, Integer userId) throws SQLException {
        if (eventId == null) {
            throw new IllegalArgumentException("L'identifiant de l'evenement est obligatoire.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("L'identifiant utilisateur est obligatoire.");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        if (hasLiked(eventId, userId)) {
            try (PreparedStatement ps = c.prepareStatement(DELETE_LIKE)) {
                ps.setLong(1, eventId);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
            return false;
        }
        try (PreparedStatement ps = c.prepareStatement(INSERT_LIKE)) {
            ps.setLong(1, eventId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        return true;
    }

    public Set<Long> findLikedEventIdsByUser(Integer userId) throws SQLException {
        if (userId == null) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_LIKED_IDS_BY_USER)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("event_id"));
                }
            }
        }
        return ids;
    }
}
