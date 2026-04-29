package services.gestionposts;

import models.gestionposts.Post;
import utils.DbConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Implémentation DAO pour Post utilisant PreparedStatement.
 * Respecte le pattern DAO et utilise le singleton DbConnexion.
 */
public class PostDAOImpl implements PostDAO {
    private static final String SELECT_INTERACTION_COUNTS = """
            SELECT
                (SELECT COUNT(*) FROM post_like WHERE post_id = ?) AS likes_count,
                (SELECT COUNT(*) FROM comment WHERE post_id = ?) AS comments_count
            """;


    private static final String INSERT = """
            INSERT INTO post (title, content, location, image_url, user_id, latitude, longitude, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;

    private static final String UPDATE = """
            UPDATE post SET
                title = ?, content = ?, location = ?, image_url = ?, latitude = ?, longitude = ?, updated_at = NOW()
            WHERE id = ?
            """;

    private static final String DELETE = "DELETE FROM post WHERE id = ?";

    private static final String SELECT_BY_ID = """
            SELECT id, title, content, location, image_url, user_id,
                   created_at, updated_at, latitude, longitude
            FROM post
            WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, title, content, location, image_url, user_id,
                   created_at, updated_at, latitude, longitude
            FROM post
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_ALL = "SELECT COUNT(*) FROM post";

    private static final String SELECT_BY_LOCATION = """
            SELECT id, title, content, location, image_url, user_id,
                   created_at, updated_at, latitude, longitude
            FROM post
            WHERE location = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_BY_LOCATION = "SELECT COUNT(*) FROM post WHERE location = ?";

    private static final String SEARCH = """
            SELECT id, title, content, location, image_url, user_id,
                   created_at, updated_at, latitude, longitude
            FROM post
            WHERE title LIKE ? OR content LIKE ? OR location LIKE ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_SEARCH = """
            SELECT COUNT(*) FROM post
            WHERE title LIKE ? OR content LIKE ? OR location LIKE ?
            """;

    private static final String SEARCH_BY_LOCATION_AND_KEYWORD = """
            SELECT id, title, content, location, image_url, user_id,
                   created_at, updated_at, latitude, longitude
            FROM post
            WHERE location = ? AND (title LIKE ? OR content LIKE ?)
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_SEARCH_BY_LOCATION_AND_KEYWORD = """
            SELECT COUNT(*) FROM post
            WHERE location = ? AND (title LIKE ? OR content LIKE ?)
            """;

    private static final String SELECT_ALL_LOCATIONS = """
            SELECT DISTINCT location FROM post WHERE location IS NOT NULL ORDER BY location
            """;

    private static final String SELECT_BY_USER_ID = """
            SELECT id, title, content, location, image_url, user_id,
                   created_at, updated_at, latitude, longitude
            FROM post
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_BY_USER_ID = "SELECT COUNT(*) FROM post WHERE user_id = ?";

    private static final String SELECT_BY_USER_ID_AND_LOCATION = """
            SELECT id, title, content, location, image_url, user_id,
                   created_at, updated_at, latitude, longitude
            FROM post
            WHERE user_id = ? AND location = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_BY_USER_ID_AND_LOCATION =
            "SELECT COUNT(*) FROM post WHERE user_id = ? AND location = ?";

    private static final String SEARCH_BY_USER_ID = """
            SELECT id, title, content, location, image_url, user_id,
                   created_at, updated_at, latitude, longitude
            FROM post
            WHERE user_id = ? AND (title LIKE ? OR content LIKE ? OR location LIKE ?)
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_SEARCH_BY_USER_ID = """
            SELECT COUNT(*) FROM post
            WHERE user_id = ? AND (title LIKE ? OR content LIKE ? OR location LIKE ?)
            """;

    private static final String SEARCH_BY_USER_ID_LOCATION_AND_KEYWORD = """
            SELECT id, title, content, location, image_url, user_id,
                   created_at, updated_at, latitude, longitude
            FROM post
            WHERE user_id = ? AND location = ? AND (title LIKE ? OR content LIKE ?)
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String FIND_NEARBY = """
            SELECT * FROM (
              SELECT id, title, content, location, image_url, user_id,
                     created_at, updated_at, latitude, longitude,
                     (6371 * acos(GREATEST(-1, LEAST(1,
                       cos(radians(?)) * cos(radians(latitude)) * cos(radians(longitude) - radians(?))
                     + sin(radians(?)) * sin(radians(latitude))
                     )))) AS dist_km
              FROM post
              WHERE latitude IS NOT NULL AND longitude IS NOT NULL
            ) t
            WHERE t.dist_km <= ?
            ORDER BY t.dist_km ASC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_NEARBY = """
            SELECT COUNT(*) FROM (
              SELECT id,
                     (6371 * acos(GREATEST(-1, LEAST(1,
                       cos(radians(?)) * cos(radians(latitude)) * cos(radians(longitude) - radians(?))
                     + sin(radians(?)) * sin(radians(latitude))
                     )))) AS dist_km
              FROM post
              WHERE latitude IS NOT NULL AND longitude IS NOT NULL
            ) t
            WHERE t.dist_km <= ?
            """;

    private static final String FIND_MY_POSTS_NEARBY = """
            SELECT * FROM (
              SELECT id, title, content, location, image_url, user_id,
                     created_at, updated_at, latitude, longitude,
                     (6371 * acos(GREATEST(-1, LEAST(1,
                       cos(radians(?)) * cos(radians(latitude)) * cos(radians(longitude) - radians(?))
                     + sin(radians(?)) * sin(radians(latitude))
                     )))) AS dist_km
              FROM post
              WHERE user_id = ? AND latitude IS NOT NULL AND longitude IS NOT NULL
            ) t
            WHERE t.dist_km <= ?
            ORDER BY t.dist_km ASC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_MY_POSTS_NEARBY = """
            SELECT COUNT(*) FROM (
              SELECT id,
                     (6371 * acos(GREATEST(-1, LEAST(1,
                       cos(radians(?)) * cos(radians(latitude)) * cos(radians(longitude) - radians(?))
                     + sin(radians(?)) * sin(radians(latitude))
                     )))) AS dist_km
              FROM post
              WHERE user_id = ? AND latitude IS NOT NULL AND longitude IS NOT NULL
            ) t
            WHERE t.dist_km <= ?
            """;

    private static final String COUNT_SEARCH_BY_USER_ID_LOCATION_AND_KEYWORD = """
            SELECT COUNT(*) FROM post
            WHERE user_id = ? AND location = ? AND (title LIKE ? OR content LIKE ?)
            """;

    @Override
    public void create(Post post) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, post.getTitre());
            ps.setString(2, post.getContenu());
            ps.setString(3, post.getLocation());
            ps.setString(4, post.getImageUrl());
            ps.setInt(5, post.getUserId() != null ? post.getUserId() : 1);
            if (post.getLatitude() != null) {
                ps.setDouble(6, post.getLatitude());
            } else {
                ps.setNull(6, Types.DOUBLE);
            }
            if (post.getLongitude() != null) {
                ps.setDouble(7, post.getLongitude());
            } else {
                ps.setNull(7, Types.DOUBLE);
            }
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    post.setId(keys.getLong(1));
                }
            }
        }
    }

    @Override
    public void update(Post post) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(UPDATE)) {
            ps.setString(1, post.getTitre());
            ps.setString(2, post.getContenu());
            ps.setString(3, post.getLocation());
            ps.setString(4, post.getImageUrl());
            if (post.getLatitude() != null) {
                ps.setDouble(5, post.getLatitude());
            } else {
                ps.setNull(5, Types.DOUBLE);
            }
            if (post.getLongitude() != null) {
                ps.setDouble(6, post.getLongitude());
            } else {
                ps.setNull(6, Types.DOUBLE);
            }
            ps.setLong(7, post.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(Long id) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Post> findById(Long id) throws SQLException {
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
    public List<Post> findAll(int offset, int limit) throws SQLException {
        List<Post> posts = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_ALL)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public int countAll() throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_ALL);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    @Override
    public List<Post> findByLocation(String location, int offset, int limit) throws SQLException {
        List<Post> posts = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_LOCATION)) {
            ps.setString(1, location);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public int countByLocation(String location) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_BY_LOCATION)) {
            ps.setString(1, location);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<Post> search(String keyword, int offset, int limit) throws SQLException {
        List<Post> posts = new ArrayList<>();
        String searchPattern = "%" + keyword + "%";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SEARCH)) {
            ps.setString(1, searchPattern);
            ps.setString(2, searchPattern);
            ps.setString(3, searchPattern);
            ps.setInt(4, limit);
            ps.setInt(5, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public int countSearch(String keyword) throws SQLException {
        String searchPattern = "%" + keyword + "%";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_SEARCH)) {
            ps.setString(1, searchPattern);
            ps.setString(2, searchPattern);
            ps.setString(3, searchPattern);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<Post> searchByLocationAndKeyword(String location, String keyword, int offset, int limit) throws SQLException {
        List<Post> posts = new ArrayList<>();
        String searchPattern = "%" + keyword + "%";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SEARCH_BY_LOCATION_AND_KEYWORD)) {
            ps.setString(1, location);
            ps.setString(2, searchPattern);
            ps.setString(3, searchPattern);
            ps.setInt(4, limit);
            ps.setInt(5, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public int countSearchByLocationAndKeyword(String location, String keyword) throws SQLException {
        String searchPattern = "%" + keyword + "%";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_SEARCH_BY_LOCATION_AND_KEYWORD)) {
            ps.setString(1, location);
            ps.setString(2, searchPattern);
            ps.setString(3, searchPattern);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<String> findAllLocations() throws SQLException {
        List<String> locations = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_ALL_LOCATIONS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                locations.add(rs.getString("location"));
            }
        }
        return locations;
    }

    @Override
    public List<Post> findAllForAdmin() throws SQLException {
        List<Post> posts = new ArrayList<>();
        String sql = """
                SELECT p.id, p.title, p.content, p.location, p.image_url, p.user_id,
                       p.created_at, p.updated_at, p.latitude, p.longitude,
                       COALESCE(u.username, 'User #' || p.user_id) as author_name,
                       (SELECT COUNT(*) FROM `like` l WHERE l.post_id = p.id) as likes_count,
                       (SELECT COUNT(*) FROM comment c WHERE c.post_id = p.id) as comments_count
                FROM post p
                LEFT JOIN `user` u ON p.user_id = u.id
                ORDER BY p.created_at DESC
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Post post = mapRow(rs);
                post.setNbLikes(rs.getInt("likes_count"));
                post.setNbCommentaires(rs.getInt("comments_count"));
                posts.add(post);
            }
        }
        return posts;
    }

    @Override
    public List<LocationCount> countByLocationGrouped() throws SQLException {
        List<LocationCount> counts = new ArrayList<>();
        String sql = """
                SELECT location, COUNT(*) as cnt
                FROM post
                WHERE location IS NOT NULL
                GROUP BY location
                ORDER BY cnt DESC
                LIMIT 8
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                counts.add(new LocationCount(rs.getString("location"), rs.getInt("cnt")));
            }
        }
        return counts;
    }

    @Override
    public int countAllLikes() throws SQLException {
        String sql = "SELECT COUNT(*) FROM `like`";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    @Override
    public List<MonthlyActivity> getMonthlyActivity() throws SQLException {
        List<MonthlyActivity> activity = new ArrayList<>();
        String sql = """
                SELECT DATE_FORMAT(created_at, '%b %Y') as month,
                       COUNT(*) as posts_count
                FROM post
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
                GROUP BY DATE_FORMAT(created_at, '%Y-%m'), DATE_FORMAT(created_at, '%b %Y')
                ORDER BY DATE_FORMAT(created_at, '%Y-%m')
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                activity.add(new MonthlyActivity(rs.getString("month"), rs.getInt("posts_count"), 0));
            }
        }
        return activity;
    }

    @Override
    public List<Post> findByUserId(int userId, int offset, int limit) throws SQLException {
        List<Post> posts = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_USER_ID)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public int countByUserId(int userId) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_BY_USER_ID)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<Post> findByUserIdAndLocation(int userId, String location, int offset, int limit) throws SQLException {
        List<Post> posts = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_USER_ID_AND_LOCATION)) {
            ps.setInt(1, userId);
            ps.setString(2, location);
            ps.setInt(3, limit);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public int countByUserIdAndLocation(int userId, String location) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_BY_USER_ID_AND_LOCATION)) {
            ps.setInt(1, userId);
            ps.setString(2, location);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<Post> searchByUserId(int userId, String keyword, int offset, int limit) throws SQLException {
        List<Post> posts = new ArrayList<>();
        String searchPattern = "%" + keyword + "%";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SEARCH_BY_USER_ID)) {
            ps.setInt(1, userId);
            ps.setString(2, searchPattern);
            ps.setString(3, searchPattern);
            ps.setString(4, searchPattern);
            ps.setInt(5, limit);
            ps.setInt(6, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public int countSearchByUserId(int userId, String keyword) throws SQLException {
        String searchPattern = "%" + keyword + "%";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_SEARCH_BY_USER_ID)) {
            ps.setInt(1, userId);
            ps.setString(2, searchPattern);
            ps.setString(3, searchPattern);
            ps.setString(4, searchPattern);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<Post> searchByUserIdLocationAndKeyword(int userId, String location, String keyword, int offset, int limit)
            throws SQLException {
        List<Post> posts = new ArrayList<>();
        String searchPattern = "%" + keyword + "%";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SEARCH_BY_USER_ID_LOCATION_AND_KEYWORD)) {
            ps.setInt(1, userId);
            ps.setString(2, location);
            ps.setString(3, searchPattern);
            ps.setString(4, searchPattern);
            ps.setInt(5, limit);
            ps.setInt(6, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public int countSearchByUserIdLocationAndKeyword(int userId, String location, String keyword) throws SQLException {
        String searchPattern = "%" + keyword + "%";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_SEARCH_BY_USER_ID_LOCATION_AND_KEYWORD)) {
            ps.setInt(1, userId);
            ps.setString(2, location);
            ps.setString(3, searchPattern);
            ps.setString(4, searchPattern);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public int countInteractionsLast24h(Long postId) throws SQLException {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM `like` l WHERE l.post_id = ? AND l.created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR))
                    +
                    (SELECT COUNT(*) FROM comment c WHERE c.post_id = ? AND c.created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR))
                AS total
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ps.setLong(2, postId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            // Tables might not exist yet, return 0
            return 0;
        }
        return 0;
    }

    @Override
    public List<Post> findNearbyPaginated(double lat, double lng, double radiusKm, int offset, int limit) throws SQLException {
        List<Post> posts = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(FIND_NEARBY)) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lng);
            ps.setDouble(3, lat);
            ps.setDouble(4, radiusKm);
            ps.setInt(5, limit);
            ps.setInt(6, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public int countNearby(double lat, double lng, double radiusKm) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_NEARBY)) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lng);
            ps.setDouble(3, lat);
            ps.setDouble(4, radiusKm);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<Post> findMyPostsNearbyPaginated(int userId, double lat, double lng, double radiusKm, int offset, int limit)
            throws SQLException {
        List<Post> posts = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(FIND_MY_POSTS_NEARBY)) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lng);
            ps.setDouble(3, lat);
            ps.setInt(4, userId);
            ps.setDouble(5, radiusKm);
            ps.setInt(6, limit);
            ps.setInt(7, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public int countMyPostsNearby(int userId, double lat, double lng, double radiusKm) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(COUNT_MY_POSTS_NEARBY)) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lng);
            ps.setDouble(3, lat);
            ps.setInt(4, userId);
            ps.setDouble(5, radiusKm);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<Post> findWithCoordinatesForMap(Integer restrictUserId, String locationEquals, String keywordNeedle,
                                                int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT id, title, content, location, image_url, user_id,
                       created_at, updated_at, latitude, longitude
                FROM post
                WHERE latitude IS NOT NULL AND longitude IS NOT NULL
                """);
        List<Object> params = new ArrayList<>();
        if (restrictUserId != null) {
            sql.append(" AND user_id = ?");
            params.add(restrictUserId);
        }
        if (locationEquals != null && !locationEquals.isBlank()) {
            sql.append(" AND location = ?");
            params.add(locationEquals);
        }
        if (keywordNeedle != null && !keywordNeedle.isBlank()) {
            String needle = keywordNeedle.trim().toLowerCase(Locale.ROOT);
            sql.append("""
                     AND (INSTR(LOWER(title), ?) > 0
                       OR INSTR(LOWER(content), ?) > 0
                       OR INSTR(LOWER(location), ?) > 0)
                    """);
            params.add(needle);
            params.add(needle);
            params.add(needle);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(Math.min(Math.max(limit, 1), 2500));

        List<Post> posts = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public List<Post> findTrendingTop(int limit) throws SQLException {
        int safe = Math.max(1, Math.min(limit, 20));
        List<Post> posts = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        String sql = """
                SELECT p.id, p.title, p.content, p.location, p.image_url, p.user_id,
                       p.created_at, p.updated_at, p.latitude, p.longitude,
                       p.trending_score, p.is_trending, p.trending_growth_pct, p.admin_pinned, p.trend_excluded,
                       COALESCE(pc.positive_comments_count, 0) AS positive_comments_count
                FROM post p
                LEFT JOIN (
                    SELECT post_id, COUNT(*) AS positive_comments_count
                    FROM comment
                    WHERE sentiment = 'POSITIVE'
                    GROUP BY post_id
                ) pc ON pc.post_id = p.id
                WHERE (p.trend_excluded = 0 OR p.trend_excluded IS NULL)
                ORDER BY p.admin_pinned DESC, positive_comments_count DESC, p.trending_score DESC, p.created_at DESC
                LIMIT ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, safe);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRow(rs));
                }
            }
        }
        return posts;
    }

    @Override
    public List<Post> findTrendingCandidates(int maxAgeHours, int minInteractions) throws SQLException {
        List<Post> posts = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        String sql = """
                SELECT p.id, p.title, p.content, p.location, p.image_url, p.user_id,
                       p.created_at, p.updated_at, p.latitude, p.longitude,
                       p.trending_score, p.is_trending, p.trending_growth_pct, p.admin_pinned, p.trend_excluded,
                       COALESCE(pl.cnt,0) likes_count,
                       COALESCE(cm.cnt,0) comments_count,
                       COALESCE(cm.avg_sent,0) avg_sentiment
                FROM post p
                LEFT JOIN (
                    SELECT post_id, COUNT(*) cnt
                    FROM post_like
                    GROUP BY post_id
                ) pl ON pl.post_id = p.id
                LEFT JOIN (
                    SELECT post_id,
                           COUNT(*) cnt,
                           AVG(
                               CASE sentiment
                                   WHEN 'POSITIVE' THEN 1
                                   WHEN 'NEGATIVE' THEN -1
                                   ELSE 0
                               END
                           ) avg_sent
                    FROM comment
                    GROUP BY post_id
                ) cm ON cm.post_id = p.id
                WHERE p.created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)
                  AND (COALESCE(pl.cnt,0) + COALESCE(cm.cnt,0)) >= ?
                  AND (p.trend_excluded = 0 OR p.trend_excluded IS NULL)
                ORDER BY p.created_at DESC
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, maxAgeHours));
            ps.setInt(2, Math.max(0, minInteractions));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Post p = mapRow(rs);
                    p.setNbLikes(rs.getInt("likes_count"));
                    p.setNbCommentaires(rs.getInt("comments_count"));
                    // Temporary: store avg sentiment in growth field for service computation context.
                    p.setTrendingGrowthPct(rs.getDouble("avg_sentiment"));
                    posts.add(p);
                }
            }
        }
        return posts;
    }

    @Override
    public void updateTrendingState(Long postId, double score, boolean trending, double growthPct) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE post SET trending_score = ?, is_trending = ?, trending_growth_pct = ? WHERE id = ?")) {
            ps.setDouble(1, score);
            ps.setBoolean(2, trending);
            ps.setDouble(3, growthPct);
            ps.setLong(4, postId);
            ps.executeUpdate();
        }
    }

    @Override
    public void insertTrendingHistory(Long postId, double score, double growthPct) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO trending_history (post_id, score, growth_pct, created_at) VALUES (?, ?, ?, NOW())")) {
            ps.setLong(1, postId);
            ps.setDouble(2, score);
            ps.setDouble(3, growthPct);
            ps.executeUpdate();
        }
    }

    @Override
    public void setAdminPinned(Long postId, boolean pinned) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement("UPDATE post SET admin_pinned = ? WHERE id = ?")) {
            ps.setBoolean(1, pinned);
            ps.setLong(2, postId);
            ps.executeUpdate();
        }
    }

    @Override
    public void setTrendExcluded(Long postId, boolean excluded) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement("UPDATE post SET trend_excluded = ? WHERE id = ?")) {
            ps.setBoolean(1, excluded);
            ps.setLong(2, postId);
            ps.executeUpdate();
        }
    }

    private Post mapRow(ResultSet rs) throws SQLException {
        Post post = new Post();
        post.setId(rs.getLong("id"));
        post.setTitre(rs.getString("title"));
        post.setContenu(rs.getString("content"));
        post.setLocation(rs.getString("location"));
        post.setImageUrl(rs.getString("image_url"));
        post.setUserId(rs.getInt("user_id"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        post.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        post.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);

        // Likes / commentaires : lire les alias si présents, sinon fallback DB.
        boolean countsLoaded = false;
        try {
            int likes = rs.getInt("likes_count");
            int comments = rs.getInt("comments_count");
            post.setNbLikes(likes);
            post.setNbCommentaires(comments);
            countsLoaded = true;
        } catch (SQLException ignored) {
            // alias absents dans certaines requêtes => fallback ci-dessous
        }
        if (!countsLoaded && post.getId() != null) {
            loadInteractionCounts(post);
        }

        // Try to read latitude/longitude if columns exist
        try {
            double lat = rs.getDouble("latitude");
            if (!rs.wasNull()) post.setLatitude(lat);
            double lng = rs.getDouble("longitude");
            if (!rs.wasNull()) post.setLongitude(lng);
        } catch (SQLException ignored) {}
        try {
            double ts = rs.getDouble("trending_score");
            post.setTrendingScore(rs.wasNull() ? 0.0 : ts);
            boolean it = rs.getBoolean("is_trending");
            post.setTrending(rs.wasNull() ? Boolean.FALSE : it);
            double gp = rs.getDouble("trending_growth_pct");
            post.setTrendingGrowthPct(rs.wasNull() ? 0.0 : gp);
            boolean pinned = rs.getBoolean("admin_pinned");
            post.setAdminPinned(rs.wasNull() ? Boolean.FALSE : pinned);
            boolean excluded = rs.getBoolean("trend_excluded");
            post.setTrendExcluded(rs.wasNull() ? Boolean.FALSE : excluded);
        } catch (SQLException ignored) {}
        try {
            int pc = rs.getInt("positive_comments_count");
            post.setPositiveCommentsCount(rs.wasNull() ? 0 : pc);
        } catch (SQLException ignored) {}

        return post;
    }

    private void loadInteractionCounts(Post post) {
        try {
            Connection c = DbConnexion.getInstance().getConnection();
            try (PreparedStatement ps = c.prepareStatement(SELECT_INTERACTION_COUNTS)) {
                ps.setLong(1, post.getId());
                ps.setLong(2, post.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        post.setNbLikes(rs.getInt("likes_count"));
                        post.setNbCommentaires(rs.getInt("comments_count"));
                        return;
                    }
                }
            }
        } catch (SQLException ignored) {
            // Keep resilient: fallback to zeros if count query fails.
        }
        post.setNbLikes(0);
        post.setNbCommentaires(0);
    }
}
