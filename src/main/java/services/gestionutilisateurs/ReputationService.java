package services.gestionutilisateurs;

import utils.DbConnexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestion centralisée des points + niveaux utilisateurs.
 */
public class ReputationService {

    public enum LevelTier {
        NOVICE("Novice", 0, 49, "🌱"),
        EXPLORATEUR("Explorateur", 50, 199, "🧭"),
        GUIDE("Guide", 200, 499, "🗺️"),
        EXPERT("Expert", 500, 999, "🏆"),
        LEGENDE("Légende", 1000, Integer.MAX_VALUE, "👑");

        private final String label;
        private final int min;
        private final int max;
        private final String icon;

        LevelTier(String label, int min, int max, String icon) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.icon = icon;
        }

        public String label() {
            return label;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        public String icon() {
            return icon;
        }
    }

    public record ReputationSnapshot(int score, LevelTier level, int nextThreshold, double progressPct) {
    }

    public record ReputationLogItem(String action, int points, java.time.LocalDateTime createdAt) {
    }

    public LevelTier levelFromScore(int score) {
        for (LevelTier t : LevelTier.values()) {
            if (score >= t.min() && score <= t.max()) {
                return t;
            }
        }
        return LevelTier.NOVICE;
    }

    public ReputationSnapshot snapshotOf(int score) {
        LevelTier t = levelFromScore(score);
        int next = switch (t) {
            case NOVICE -> 50;
            case EXPLORATEUR -> 200;
            case GUIDE -> 500;
            case EXPERT -> 1000;
            case LEGENDE -> 1000;
        };
        double pct;
        if (t == LevelTier.LEGENDE) {
            pct = 100.0;
        } else {
            int span = next - t.min();
            pct = span <= 0 ? 100.0 : Math.max(0.0, Math.min(100.0, ((score - t.min()) * 100.0) / span));
        }
        return new ReputationSnapshot(score, t, next, pct);
    }

    public ReputationSnapshot getUserSnapshot(int userId) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement("SELECT reputation_score FROM `user` WHERE id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return snapshotOf(rs.getInt(1));
                }
            }
        }
        return snapshotOf(0);
    }

    public boolean addPoints(int userId, int points, String action, String referenceType, Long referenceId) throws SQLException {
        if (points == 0) return false;
        Connection c = DbConnexion.getInstance().getConnection();
        c.setAutoCommit(false);
        try {
            int previous = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT reputation_score FROM `user` WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        previous = rs.getInt(1);
                    } else {
                        c.rollback();
                        c.setAutoCommit(true);
                        return false;
                    }
                }
            }
            int updated = Math.max(0, previous + points);
            LevelTier prevLevel = levelFromScore(previous);
            LevelTier newLevel = levelFromScore(updated);

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE `user` SET reputation_score = ?, points = ?, level = ? WHERE id = ?")) {
                ps.setInt(1, updated);
                ps.setInt(2, updated);
                ps.setString(3, newLevel.name());
                ps.setInt(4, userId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO reputation_log (user_id, action, points, reference_type, reference_id, created_at) VALUES (?, ?, ?, ?, ?, NOW())")) {
                ps.setInt(1, userId);
                ps.setString(2, action != null ? action : "UNKNOWN");
                ps.setInt(3, points);
                if (referenceType != null) {
                    ps.setString(4, referenceType);
                } else {
                    ps.setNull(4, java.sql.Types.VARCHAR);
                }
                if (referenceId != null) {
                    ps.setLong(5, referenceId);
                } else {
                    ps.setNull(5, java.sql.Types.BIGINT);
                }
                ps.executeUpdate();
            }
            c.commit();
            if (prevLevel != newLevel) {
                System.out.println("🎉 Niveau atteint user #" + userId + ": " + newLevel.label() + " (" + updated + " pts)");
                return true;
            }
            return false;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    public List<ReputationLogItem> getLatestLogs(int userId, int limit) throws SQLException {
        int safe = Math.max(1, Math.min(limit, 50));
        List<ReputationLogItem> logs = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT action, points, created_at FROM reputation_log WHERE user_id = ? ORDER BY created_at DESC LIMIT ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, safe);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp("created_at");
                    logs.add(new ReputationLogItem(
                            rs.getString("action"),
                            rs.getInt("points"),
                            ts != null ? ts.toLocalDateTime() : null));
                }
            }
        }
        return logs;
    }
}

