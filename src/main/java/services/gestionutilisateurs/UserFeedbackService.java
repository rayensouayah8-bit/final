package services.gestionutilisateurs;

import models.gestionutilisateurs.UserFeedback;
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

public class UserFeedbackService {

    private static final String INSERT = """
            INSERT INTO user_app_feedback (user_id, stars, note, created_at)
            VALUES (?, ?, ?, ?)
            """;

    private static final String SELECT_BY_USER = """
            SELECT id, user_id, stars, note, created_at
            FROM user_app_feedback
            WHERE user_id = ?
            ORDER BY created_at DESC, id DESC
            """;

    private static final String SELECT_RECENT_PUBLIC = """
            SELECT f.id, f.user_id, f.stars, f.note, f.created_at,
                   u.username, u.role
            FROM user_app_feedback f
            LEFT JOIN `user` u ON u.id = f.user_id
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT ?
            """;

    public void insert(UserFeedback feedback) throws SQLException {
        validate(feedback);
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, feedback.getUserId());
            ps.setInt(2, feedback.getStars());
            ps.setString(3, feedback.getNote());
            LocalDateTime ts = feedback.getCreatedAt() != null ? feedback.getCreatedAt() : LocalDateTime.now();
            ps.setTimestamp(4, Timestamp.valueOf(ts));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    feedback.setId(keys.getLong(1));
                }
            }
            feedback.setCreatedAt(ts);
        }
    }

    public List<UserFeedback> findByUser(Integer userId) throws SQLException {
        if (userId == null) {
            return List.of();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        List<UserFeedback> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_USER)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserFeedback f = new UserFeedback();
                    f.setId(rs.getLong("id"));
                    f.setUserId(rs.getInt("user_id"));
                    f.setStars(rs.getInt("stars"));
                    f.setNote(rs.getString("note"));
                    Timestamp cAt = rs.getTimestamp("created_at");
                    f.setCreatedAt(cAt == null ? null : cAt.toLocalDateTime());
                    list.add(f);
                }
            }
        }
        return list;
    }

    public List<PublicFeedbackView> findRecentPublic(int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(100, limit));
        Connection c = DbConnexion.getInstance().getConnection();
        List<PublicFeedbackView> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_RECENT_PUBLIC)) {
            ps.setInt(1, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PublicFeedbackView f = new PublicFeedbackView();
                    f.id = rs.getLong("id");
                    f.userId = rs.getInt("user_id");
                    f.stars = rs.getInt("stars");
                    f.note = rs.getString("note");
                    Timestamp cAt = rs.getTimestamp("created_at");
                    f.createdAt = cAt == null ? null : cAt.toLocalDateTime();
                    f.username = rs.getString("username");
                    f.role = rs.getString("role");
                    list.add(f);
                }
            }
        }
        return list;
    }

    private static void validate(UserFeedback feedback) {
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback is required.");
        }
        if (feedback.getUserId() == null) {
            throw new IllegalArgumentException("User is required.");
        }
        Integer stars = feedback.getStars();
        if (stars == null || stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        String note = feedback.getNote() == null ? "" : feedback.getNote().trim();
        if (note.isEmpty()) {
            throw new IllegalArgumentException("Feedback note is required.");
        }
        if (note.length() > 1200) {
            throw new IllegalArgumentException("Comment is too long (max 1200 characters).");
        }
        feedback.setNote(note);
    }

    public static class PublicFeedbackView {
        private Long id;
        private Integer userId;
        private Integer stars;
        private String note;
        private LocalDateTime createdAt;
        private String username;
        private String role;

        public Long getId() {
            return id;
        }

        public Integer getUserId() {
            return userId;
        }

        public Integer getStars() {
            return stars;
        }

        public String getNote() {
            return note;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }
    }
}
