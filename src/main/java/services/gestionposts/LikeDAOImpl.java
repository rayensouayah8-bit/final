package services.gestionposts;

import models.gestionposts.PostLike;
import utils.DbConnexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Implémentation DAO pour PostLike utilisant PreparedStatement.
 * Utilise la table 'post_like' existante dans la base de données.
 */
public class LikeDAOImpl implements LikeDAO {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS post_like (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                post_id BIGINT NOT NULL,
                UNIQUE KEY unique_like (user_id, post_id)
            )
            """;

    private static final String INSERT = """
            INSERT INTO post_like (user_id, post_id)
            VALUES (?, ?)
            """;

    private static final String DELETE = "DELETE FROM post_like WHERE id = ?";

    private static final String DELETE_BY_USER_AND_POST = """
            DELETE FROM post_like WHERE user_id = ? AND post_id = ?
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, user_id, post_id FROM post_like WHERE id = ?
            """;

    private static final String SELECT_BY_USER_AND_POST = """
            SELECT id, user_id, post_id
            FROM post_like
            WHERE user_id = ? AND post_id = ?
            """;

    private static final String EXISTS_BY_USER_AND_POST = """
            SELECT COUNT(*) FROM post_like WHERE user_id = ? AND post_id = ?
            """;

    private static final String COUNT_BY_POST_ID = """
            SELECT COUNT(*) FROM post_like WHERE post_id = ?
            """;

    private boolean tableChecked = false;

    private void ensureTableExists() throws SQLException {
        if (tableChecked) return;
        Connection c = DbConnexion.getInstance().getConnection();
        try (Statement stmt = c.createStatement()) {
            stmt.execute(CREATE_TABLE);
            tableChecked = true;
        } catch (SQLException e) {
            // Table might already exist or other error, continue
            tableChecked = true;
        }
    }

    @Override
    public void create(PostLike like) throws SQLException {
        ensureTableExists();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, like.getUserId());
            ps.setLong(2, like.getPostId());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    like.setId(rs.getLong(1));
                }
            }
        }
    }

    @Override
    public void delete(Long id) throws SQLException {
        ensureTableExists();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteByUserAndPost(Integer userId, Long postId) throws SQLException {
        ensureTableExists();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(DELETE_BY_USER_AND_POST)) {
            ps.setInt(1, userId);
            ps.setLong(2, postId);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<PostLike> findById(Long id) throws SQLException {
        ensureTableExists();
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

    @Override
    public Optional<PostLike> findByUserAndPost(Integer userId, Long postId) throws SQLException {
        ensureTableExists();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_USER_AND_POST)) {
            ps.setInt(1, userId);
            ps.setLong(2, postId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean existsByUserAndPost(Integer userId, Long postId) throws SQLException {
        ensureTableExists();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(EXISTS_BY_USER_AND_POST)) {
            ps.setInt(1, userId);
            ps.setLong(2, postId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    @Override
    public int countByPostId(Long postId) throws SQLException {
        ensureTableExists();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_BY_POST_ID)) {
            ps.setLong(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private PostLike mapRow(ResultSet rs) throws SQLException {
        PostLike like = new PostLike();
        like.setId(rs.getLong("id"));
        like.setUserId(rs.getInt("user_id"));
        like.setPostId(rs.getLong("post_id"));
        return like;
    }
}
