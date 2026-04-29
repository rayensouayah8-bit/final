package services.gestionposts;

import models.gestionposts.Post;
import services.CRUD;
import services.gestionutilisateurs.ReputationService;

import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service métier pour la gestion des posts.
 * Implémente CRUD et gère les validations serveur avec messages en français.
 */
public class PostService implements CRUD<Post, Long> {

    private final PostDAO postDAO;
    private final ReputationService reputationService;
    private static final int POSTS_PER_PAGE = 12;
    private static final double TRENDING_THRESHOLD = 8.0;

    public PostService() {
        this.postDAO = new PostDAOImpl();
        this.reputationService = new ReputationService();
    }

    @Override
    public void create(Post entity) throws SQLException {
        validate(entity, true);
        postDAO.create(entity);
        if (entity.getUserId() != null) {
            int postPoints = (entity.getAdminPinned() != null && entity.getAdminPinned()) ? 20 : 10;
            reputationService.addPoints(entity.getUserId(), postPoints, "POST_CREATED", "post", entity.getId());
        }
    }

    @Override
    public void insert(Post entity) throws SQLException {
        create(entity);
    }

    @Override
    public void update(Post entity) throws SQLException {
        validate(entity, false);
        postDAO.update(entity);
    }

    @Override
    public void delete(Long id) throws SQLException {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant du post est obligatoire pour la suppression.");
        }
        postDAO.delete(id);
    }

    public Optional<Post> findById(Long id) throws SQLException {
        return postDAO.findById(id);
    }

    /**
     * Récupère les posts avec pagination.
     */
    public List<Post> findAllPaginated(int page) throws SQLException {
        int offset = (page - 1) * POSTS_PER_PAGE;
        return postDAO.findAll(offset, POSTS_PER_PAGE);
    }

    public int countAll() throws SQLException {
        return postDAO.countAll();
    }

    /**
     * Calcule le nombre total de pages.
     */
    public int getTotalPages(int totalCount) {
        return (int) Math.ceil((double) totalCount / POSTS_PER_PAGE);
    }

    /**
     * Filtre par pays avec pagination.
     */
    public List<Post> findByLocationPaginated(String location, int page) throws SQLException {
        int offset = (page - 1) * POSTS_PER_PAGE;
        return postDAO.findByLocation(location, offset, POSTS_PER_PAGE);
    }

    public int countByLocation(String location) throws SQLException {
        return postDAO.countByLocation(location);
    }

    /**
     * Recherche par mot-clé avec pagination.
     */
    public List<Post> searchPaginated(String keyword, int page) throws SQLException {
        int offset = (page - 1) * POSTS_PER_PAGE;
        return postDAO.search(keyword, offset, POSTS_PER_PAGE);
    }

    public int countSearch(String keyword) throws SQLException {
        return postDAO.countSearch(keyword);
    }

    /**
     * Recherche combinée pays + mot-clé avec pagination.
     */
    public List<Post> searchByLocationAndKeywordPaginated(String location, String keyword, int page) throws SQLException {
        int offset = (page - 1) * POSTS_PER_PAGE;
        return postDAO.searchByLocationAndKeyword(location, keyword, offset, POSTS_PER_PAGE);
    }

    public int countSearchByLocationAndKeyword(String location, String keyword) throws SQLException {
        return postDAO.countSearchByLocationAndKeyword(location, keyword);
    }

    /** Posts de l'utilisateur connecté (pagination), avec les mêmes filtres que le fil global. */
    public List<Post> findMyPostsPaginated(int userId, String countryOrNull, String keywordOrNull, int page) throws SQLException {
        int offset = (page - 1) * POSTS_PER_PAGE;
        if (countryOrNull != null && keywordOrNull != null && !keywordOrNull.isEmpty()) {
            return postDAO.searchByUserIdLocationAndKeyword(userId, countryOrNull, keywordOrNull, offset, POSTS_PER_PAGE);
        }
        if (countryOrNull != null) {
            return postDAO.findByUserIdAndLocation(userId, countryOrNull, offset, POSTS_PER_PAGE);
        }
        if (keywordOrNull != null && !keywordOrNull.isEmpty()) {
            return postDAO.searchByUserId(userId, keywordOrNull, offset, POSTS_PER_PAGE);
        }
        return postDAO.findByUserId(userId, offset, POSTS_PER_PAGE);
    }

    public int countMyPosts(int userId, String countryOrNull, String keywordOrNull) throws SQLException {
        if (countryOrNull != null && keywordOrNull != null && !keywordOrNull.isEmpty()) {
            return postDAO.countSearchByUserIdLocationAndKeyword(userId, countryOrNull, keywordOrNull);
        }
        if (countryOrNull != null) {
            return postDAO.countByUserIdAndLocation(userId, countryOrNull);
        }
        if (keywordOrNull != null && !keywordOrNull.isEmpty()) {
            return postDAO.countSearchByUserId(userId, keywordOrNull);
        }
        return postDAO.countByUserId(userId);
    }

    public List<Post> findNearbyPaginated(double lat, double lng, double radiusKm, int page) throws SQLException {
        int offset = (page - 1) * POSTS_PER_PAGE;
        return postDAO.findNearbyPaginated(lat, lng, radiusKm, offset, POSTS_PER_PAGE);
    }

    public int countNearby(double lat, double lng, double radiusKm) throws SQLException {
        return postDAO.countNearby(lat, lng, radiusKm);
    }

    public List<Post> findMyPostsNearbyPaginated(int userId, double lat, double lng, double radiusKm, int page) throws SQLException {
        int offset = (page - 1) * POSTS_PER_PAGE;
        return postDAO.findMyPostsNearbyPaginated(userId, lat, lng, radiusKm, offset, POSTS_PER_PAGE);
    }

    public int countMyPostsNearby(int userId, double lat, double lng, double radiusKm) throws SQLException {
        return postDAO.countMyPostsNearby(userId, lat, lng, radiusKm);
    }

    /**
     * Marqueurs carte : tous les posts géolocalisés correspondant aux filtres (pays / mes posts / recherche).
     */
    public List<Post> findPostsForMapMarkers(Integer restrictUserId, String locationEquals, String keywordNeedle)
            throws SQLException {
        return postDAO.findWithCoordinatesForMap(restrictUserId, locationEquals, keywordNeedle, 2000);
    }

    /**
     * Récupère toutes les locations distinctes depuis les posts.
     */
    public List<String> findAllLocationsFromPosts() throws SQLException {
        return postDAO.findAllLocations();
    }

    /**
     * Validation serveur complète avec messages en français.
     */
    public void validate(Post post, boolean isCreate) {
        List<String> errors = new ArrayList<>();

        if (post == null) {
            throw new IllegalArgumentException("Le post est obligatoire.");
        }

        // Validation ID pour update
        if (!isCreate && post.getId() == null) {
            errors.add("L'identifiant du post est obligatoire pour la modification.");
        }

        // Validation titre (min 10, max 100)
        if (post.getTitre() == null || post.getTitre().trim().isEmpty()) {
            errors.add("Le titre est obligatoire.");
        } else {
            String titre = post.getTitre().trim();
            if (titre.length() < 10) {
                errors.add("Le titre doit contenir au moins 10 caractères (actuellement : " + titre.length() + ").");
            }
            if (titre.length() > 100) {
                errors.add("Le titre ne doit pas dépasser 100 caractères (actuellement : " + titre.length() + ").");
            }
        }

        // Validation contenu (min 50, max 5000)
        if (post.getContenu() == null || post.getContenu().trim().isEmpty()) {
            errors.add("Le contenu est obligatoire.");
        } else {
            String contenu = post.getContenu().trim();
            if (contenu.length() < 50) {
                errors.add("Le contenu doit contenir au moins 50 caractères (actuellement : " + contenu.length() + ").");
            }
            if (contenu.length() > 5000) {
                errors.add("Le contenu ne doit pas dépasser 5000 caractères (actuellement : " + contenu.length() + ").");
            }
        }

        // Validation location (non vide)
        if (post.getLocation() == null || post.getLocation().trim().isEmpty()) {
            errors.add("La localisation est obligatoire.");
        }

        // Validation URL image (optionnel mais doit être valide si fourni)
        if (post.getImageUrl() != null && !post.getImageUrl().trim().isEmpty()) {
            if (!isValidImageUrl(post.getImageUrl().trim())) {
                errors.add("L'URL de l'image n'est pas valide (doit être une URL HTTP/HTTPS pointant vers une image JPG, PNG ou GIF).");
            }
        }

        // Validation user_id pour création
        if (isCreate && post.getUserId() == null) {
            errors.add("L'utilisateur créateur est obligatoire.");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }
    }

    /**
     * Vérifie si l'URL est une URL d'image valide (HTTP/HTTPS) ou un chemin local.
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return true; // Empty is valid (optional field)
        }

        String trimmed = url.trim();

        // Check if it's a local file path (Windows C:\ or absolute path /)
        if (trimmed.startsWith("C:\\") || trimmed.startsWith("/") || trimmed.contains("\\")) {
            return hasValidImageExtension(trimmed);
        }

        // Check if it's a URL
        try {
            URL u = new URL(trimmed);
            String protocol = u.getProtocol();
            if (!protocol.equals("http") && !protocol.equals("https")) {
                return false;
            }
            return hasValidImageExtension(u.getPath());
        } catch (Exception e) {
            // Not a valid URL, might still be a relative path
            return hasValidImageExtension(trimmed);
        }
    }

    private boolean hasValidImageExtension(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
               lower.endsWith(".png") || lower.endsWith(".gif") ||
               lower.endsWith(".webp") || lower.endsWith(".bmp");
    }

    public int getPostsPerPage() {
        return POSTS_PER_PAGE;
    }

    public List<Post> findTrendingNow(int limit) throws SQLException {
        return postDAO.findTrendingTop(limit);
    }

    public void recomputeTrendingNow() throws SQLException {
        List<Post> cands = postDAO.findTrendingCandidates(72, 10);
        for (Post p : cands) {
            int likes = p.getNbLikes() != null ? p.getNbLikes() : 0;
            int comments = p.getNbCommentaires() != null ? p.getNbCommentaires() : 0;
            double avgSentiment = p.getTrendingGrowthPct() != null ? p.getTrendingGrowthPct() : 0.0;
            double sentimentBoost = Math.max(0.0, avgSentiment) * 20.0;
            double base = (likes * 2.0) + (comments * 3.0) + sentimentBoost;
            double ageHours = 1.0;
            if (p.getCreatedAt() != null) {
                java.time.Duration d = java.time.Duration.between(p.getCreatedAt(), java.time.LocalDateTime.now());
                ageHours = Math.max(0.0, d.toMinutes() / 60.0);
            }
            double score = base / (ageHours + 1.0);
            boolean trending = score >= TRENDING_THRESHOLD || Boolean.TRUE.equals(p.getAdminPinned());
            double previous = p.getTrendingScore() != null ? p.getTrendingScore() : 0.0;
            double growth = previous <= 0.0001 ? 100.0 : ((score - previous) * 100.0 / previous);
            postDAO.updateTrendingState(p.getId(), round2(score), trending, round2(growth));
            postDAO.insertTrendingHistory(p.getId(), round2(score), round2(growth));
            if (trending && previous < TRENDING_THRESHOLD && p.getUserId() != null) {
                reputationService.addPoints(p.getUserId(), 50, "POST_VIRAL_BONUS", "post", p.getId());
            }
        }
    }

    public void setAdminPinned(Long postId, boolean pinned) throws SQLException {
        if (postId == null) {
            throw new IllegalArgumentException("Post id obligatoire.");
        }
        postDAO.setAdminPinned(postId, pinned);
    }

    public void setTrendExcluded(Long postId, boolean excluded) throws SQLException {
        if (postId == null) {
            throw new IllegalArgumentException("Post id obligatoire.");
        }
        postDAO.setTrendExcluded(postId, excluded);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ========== Admin Methods ==========

    /**
     * Récupère tous les posts avec statistiques pour l'admin.
     */
    public List<Post> findAllForAdmin() throws SQLException {
        return postDAO.findAllForAdmin();
    }

    /**
     * Récupère le nombre de posts par localisation (Top 8).
     */
    public List<PostDAO.LocationCount> getPostsByLocationStats() throws SQLException {
        return postDAO.countByLocationGrouped();
    }

    /**
     * Compte le nombre total de likes.
     */
    public int countAllLikes() throws SQLException {
        return postDAO.countAllLikes();
    }

    /**
     * Récupère l'activité mensuelle des posts.
     */
    public List<PostDAO.MonthlyActivity> getMonthlyActivity() throws SQLException {
        return postDAO.getMonthlyActivity();
    }

    // ========== Viral Score Algorithm ==========

    /**
     * Calcule le score viral d'un post (0-100).
     * Formule : ViralScore = min(100, (likes*2 + comments*3 + velocity*10) / maxScore * 100)
     * ou velocity = interactions des dernieres 24h.
     */
    public double calculateViralScore(Post post) throws SQLException {
        if (post == null || post.getId() == null) return 0;

        int likes = post.getNbLikes() != null ? post.getNbLikes() : 0;
        int comments = post.getNbCommentaires() != null ? post.getNbCommentaires() : 0;
        int velocity = postDAO.countInteractionsLast24h(post.getId());

        double rawScore = (likes * 2.0) + (comments * 3.0) + (velocity * 10.0);
        double maxRawScore = 500.0; // normalization threshold
        double score = Math.min(100.0, (rawScore / maxRawScore) * 100.0);

        return Math.round(score * 100.0) / 100.0;
    }

    /**
     * Calcule les scores viraux pour tous les posts admin.
     * Retourne les posts tries par score viral descendant.
     */
    public List<PostViralScore> getTopViralPosts(int limit) throws SQLException {
        List<Post> posts = findAllForAdmin();
        List<PostViralScore> scored = new ArrayList<>();

        for (Post post : posts) {
            double score = calculateViralScore(post);
            scored.add(new PostViralScore(post, score));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Predit si un post deviendra viral.
     * Si score > 60, predit +50 interactions dans les prochaines 24h.
     */
    public String getViralPrediction(double score) {
        if (score >= 80) {
            return "\uD83D\uDD25 Tendance forte ! +50 interactions prevues dans 24h";
        } else if (score >= 60) {
            return "\u26A1 Potentiel viral detecte. Croissance acceleree.";
        } else if (score >= 30) {
            return "\uD83D\uDCC8 Engagement modere. En progression.";
        } else {
            return "\uD83D\uDCC9 Engagement faible. Peu de traction.";
        }
    }

    /**
     * DTO pour le score viral d'un post.
     */
    public record PostViralScore(Post post, double score) {}
}
