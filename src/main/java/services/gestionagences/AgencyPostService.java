package services.gestionagences;

import models.gestionagences.AgencyPost;
import models.gestionagences.AgencyPostComment;
import utils.DbConnexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgencyPostService {

    private static final int POST_TITLE_MIN = 3;
    private static final int POST_TITLE_MAX = 255;
    private static final int POST_CONTENT_MIN = 5;
    private static final int POST_CONTENT_MAX = 65_000;

    private static final String SELECT_POSTS = """
            SELECT p.id, p.agency_id, p.author_id, p.title, p.content, p.created_at,
                   u.username AS author_username,
                   (SELECT COUNT(*) FROM agency_post_like l WHERE l.agency_post_id = p.id) AS likes_count,
                   (SELECT COUNT(*) FROM agency_post_comment c WHERE c.agency_post_id = p.id AND c.is_deleted = 0) AS comments_count,
                   (SELECT COUNT(*) FROM agency_post_like l2 WHERE l2.agency_post_id = p.id AND l2.user_id = ?) AS liked_by_viewer
            FROM agency_post p
            JOIN user u ON u.id = p.author_id
            WHERE p.agency_id = ? AND p.is_deleted = 0
            ORDER BY p.created_at DESC
            """;
    private static final String SELECT_POST_IMAGES = """
            SELECT agency_post_id, image_asset_id
            FROM agency_post_images
            WHERE agency_post_id IN (%s)
            ORDER BY agency_post_id
            """;
    private static final String SELECT_POST_COMMENTS = """
            SELECT c.id, c.agency_post_id, c.author_id, c.content, c.created_at,
                   u.username AS author_username, u.profile_image_id AS author_profile_image_id
            FROM agency_post_comment c
            JOIN user u ON u.id = c.author_id
            WHERE c.agency_post_id IN (%s) AND c.is_deleted = 0
            ORDER BY c.created_at ASC
            """;
    private static final String INSERT_COMMENT = """
            INSERT INTO agency_post_comment (content, created_at, is_deleted, agency_post_id, author_id)
            VALUES (?, ?, 0, ?, ?)
            """;
    private static final String INSERT_POST = """
            INSERT INTO agency_post (title, content, created_at, updated_at, is_deleted, agency_id, author_id)
            VALUES (?, ?, ?, ?, 0, ?, ?)
            """;
    private static final String INSERT_POST_IMAGE_LINK = """
            INSERT INTO agency_post_images (agency_post_id, image_asset_id)
            VALUES (?, ?)
            """;
    private static final String EXISTS_LIKE = """
            SELECT id FROM agency_post_like WHERE agency_post_id = ? AND user_id = ?
            """;
    private static final String INSERT_LIKE = """
            INSERT INTO agency_post_like (created_at, agency_post_id, user_id)
            VALUES (?, ?, ?)
            """;
    private static final String DELETE_LIKE = """
            DELETE FROM agency_post_like WHERE agency_post_id = ? AND user_id = ?
            """;
    private static final String COUNT_LIKES = """
            SELECT COUNT(*) AS c FROM agency_post_like WHERE agency_post_id = ?
            """;

    public List<AgencyPost> listByAgency(Long agencyId, Integer viewerUserId) throws SQLException {
        if (agencyId == null) {
            return List.of();
        }
        int viewerId = viewerUserId == null ? -1 : viewerUserId;
        Connection c = DbConnexion.getInstance().getConnection();
        List<AgencyPost> posts = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_POSTS)) {
            ps.setInt(1, viewerId);
            ps.setLong(2, agencyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AgencyPost p = new AgencyPost();
                    p.setId(rs.getLong("id"));
                    p.setAgencyId(rs.getLong("agency_id"));
                    p.setAuthorId(rs.getInt("author_id"));
                    p.setAuthorUsername(rs.getString("author_username"));
                    p.setTitle(rs.getString("title"));
                    p.setContent(rs.getString("content"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    p.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
                    p.setLikesCount(rs.getInt("likes_count"));
                    p.setCommentsCount(rs.getInt("comments_count"));
                    p.setLikedByViewer(rs.getInt("liked_by_viewer") > 0);
                    posts.add(p);
                }
            }
        }
        hydrateImages(c, posts);
        hydrateComments(c, posts);
        return posts;
    }

    public AgencyPostComment addComment(Long postId, Integer authorId, String content) throws SQLException {
        if (postId == null || authorId == null) {
            throw new IllegalArgumentException("Post and author are required.");
        }
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Comment content is required.");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        LocalDateTime now = LocalDateTime.now();
        try (PreparedStatement ps = c.prepareStatement(INSERT_COMMENT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, trimmed);
            ps.setTimestamp(2, Timestamp.valueOf(now));
            ps.setLong(3, postId);
            ps.setInt(4, authorId);
            ps.executeUpdate();
        }
        AgencyPostComment comment = new AgencyPostComment();
        comment.setAgencyPostId(postId);
        comment.setAuthorId(authorId);
        comment.setContent(trimmed);
        comment.setCreatedAt(now);
        return comment;
    }

    public Long createPost(Long agencyId, Integer authorId, String title, String content) throws SQLException {
        return createPost(agencyId, authorId, title, content, List.of());
    }

    /**
     * Validates title and content only (matches the create-post form: no phone or URL fields).
     */
    public AgencyPostValidationResult validatePostDraft(String title, String content) {
        Map<String, String> err = new LinkedHashMap<>();
        String cleanTitle = title == null ? "" : title.trim();
        String cleanContent = content == null ? "" : content.trim();

        if (cleanTitle.isEmpty()) {
            err.put(AgencyPostValidationResult.FIELD_TITLE, "Post title is required.");
        } else if (cleanTitle.length() < POST_TITLE_MIN) {
            err.put(AgencyPostValidationResult.FIELD_TITLE,
                    "Post title must be at least " + POST_TITLE_MIN + " characters.");
        } else if (cleanTitle.length() > POST_TITLE_MAX) {
            err.put(AgencyPostValidationResult.FIELD_TITLE,
                    "Post title must be at most " + POST_TITLE_MAX + " characters.");
        }

        if (cleanContent.isEmpty()) {
            err.put(AgencyPostValidationResult.FIELD_CONTENT, "Post content is required.");
        } else if (cleanContent.length() < POST_CONTENT_MIN) {
            err.put(AgencyPostValidationResult.FIELD_CONTENT,
                    "Post content must be at least " + POST_CONTENT_MIN + " characters.");
        } else if (cleanContent.length() > POST_CONTENT_MAX) {
            err.put(AgencyPostValidationResult.FIELD_CONTENT,
                    "Post content must be at most " + POST_CONTENT_MAX + " characters.");
        }

        return AgencyPostValidationResult.of(err);
    }

    public Long createPost(Long agencyId, Integer authorId, String title, String content, List<Long> imageAssetIds) throws SQLException {
        if (agencyId == null || authorId == null) {
            throw new IllegalArgumentException("Agency and author are required.");
        }
        AgencyPostValidationResult validation = validatePostDraft(title, content);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(String.join(" ", validation.getFieldErrors().values()));
        }
        String cleanTitle = title == null ? "" : title.trim();
        String cleanContent = content == null ? "" : content.trim();
        Connection c = DbConnexion.getInstance().getConnection();
        LocalDateTime now = LocalDateTime.now();
        Long postId = null;
        try (PreparedStatement ps = c.prepareStatement(INSERT_POST, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cleanTitle);
            ps.setString(2, cleanContent);
            ps.setTimestamp(3, Timestamp.valueOf(now));
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setLong(5, agencyId);
            ps.setInt(6, authorId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    postId = keys.getLong(1);
                }
            }
        }
        if (postId == null) {
            return null;
        }
        if (imageAssetIds != null && !imageAssetIds.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement(INSERT_POST_IMAGE_LINK)) {
                for (Long imageAssetId : imageAssetIds) {
                    if (imageAssetId == null) {
                        continue;
                    }
                    ps.setLong(1, postId);
                    ps.setLong(2, imageAssetId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        return postId;
    }

    public LikeResult toggleLike(Long postId, Integer userId) throws SQLException {
        if (postId == null || userId == null) {
            throw new IllegalArgumentException("Post and user are required.");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        boolean currentlyLiked;
        try (PreparedStatement ps = c.prepareStatement(EXISTS_LIKE)) {
            ps.setLong(1, postId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                currentlyLiked = rs.next();
            }
        }
        if (currentlyLiked) {
            try (PreparedStatement ps = c.prepareStatement(DELETE_LIKE)) {
                ps.setLong(1, postId);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement(INSERT_LIKE)) {
                ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                ps.setLong(2, postId);
                ps.setInt(3, userId);
                ps.executeUpdate();
            }
        }
        int likesCount = countLikes(c, postId);
        return new LikeResult(!currentlyLiked, likesCount);
    }

    private static int countLikes(Connection c, Long postId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(COUNT_LIKES)) {
            ps.setLong(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("c");
                }
            }
        }
        return 0;
    }

    private static void hydrateImages(Connection c, List<AgencyPost> posts) throws SQLException {
        if (posts.isEmpty()) {
            return;
        }
        Map<Long, AgencyPost> byId = new LinkedHashMap<>();
        for (AgencyPost p : posts) {
            byId.put(p.getId(), p);
        }
        String sql = SELECT_POST_IMAGES.formatted(questionMarks(byId.size()));
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Long id : byId.keySet()) {
                ps.setLong(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AgencyPost post = byId.get(rs.getLong("agency_post_id"));
                    if (post != null) {
                        post.getImageAssetIds().add(rs.getLong("image_asset_id"));
                    }
                }
            }
        }
    }

    private static void hydrateComments(Connection c, List<AgencyPost> posts) throws SQLException {
        if (posts.isEmpty()) {
            return;
        }
        Map<Long, AgencyPost> byId = new LinkedHashMap<>();
        for (AgencyPost p : posts) {
            byId.put(p.getId(), p);
        }
        String sql = SELECT_POST_COMMENTS.formatted(questionMarks(byId.size()));
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Long id : byId.keySet()) {
                ps.setLong(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AgencyPost post = byId.get(rs.getLong("agency_post_id"));
                    if (post == null) {
                        continue;
                    }
                    AgencyPostComment comment = new AgencyPostComment();
                    comment.setId(rs.getLong("id"));
                    comment.setAgencyPostId(rs.getLong("agency_post_id"));
                    comment.setAuthorId(rs.getInt("author_id"));
                    comment.setAuthorUsername(rs.getString("author_username"));
                    long profileImageId = rs.getLong("author_profile_image_id");
                    comment.setAuthorProfileImageId(rs.wasNull() ? null : profileImageId);
                    comment.setContent(rs.getString("content"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    comment.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
                    post.getComments().add(comment);
                }
            }
        }
    }

    private static String questionMarks(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    public record LikeResult(boolean liked, int likesCount) {}
}
