package services.gestionposts;

import models.gestionposts.Post;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Interface DAO pour la gestion des posts de voyage.
 * Définit les opérations CRUD et les recherches avancées.
 */
public interface PostDAO {

    /**
     * Crée un nouveau post dans la base de données.
     */
    void create(Post post) throws SQLException;

    /**
     * Met à jour un post existant.
     */
    void update(Post post) throws SQLException;

    /**
     * Supprime un post par son ID.
     */
    void delete(Long id) throws SQLException;

    /**
     * Recherche un post par son ID.
     */
    Optional<Post> findById(Long id) throws SQLException;

    /**
     * Récupère tous les posts avec pagination.
     */
    List<Post> findAll(int offset, int limit) throws SQLException;

    /**
     * Compte le nombre total de posts.
     */
    int countAll() throws SQLException;

    /**
     * Filtre les posts par pays/location.
     */
    List<Post> findByLocation(String location, int offset, int limit) throws SQLException;

    /**
     * Compte les posts par pays.
     */
    int countByLocation(String location) throws SQLException;

    /**
     * Recherche des posts par mot-clé (titre, contenu ou location).
     */
    List<Post> search(String keyword, int offset, int limit) throws SQLException;

    /**
     * Compte les résultats de recherche.
     */
    int countSearch(String keyword) throws SQLException;

    /**
     * Recherche combinée : pays + mot-clé.
     */
    List<Post> searchByLocationAndKeyword(String location, String keyword, int offset, int limit) throws SQLException;

    /**
     * Compte les résultats combinés.
     */
    int countSearchByLocationAndKeyword(String location, String keyword) throws SQLException;

    /**
     * Récupère toutes les locations distinctes (pour le filtre pays).
     */
    List<String> findAllLocations() throws SQLException;

    /**
     * Récupère tous les posts avec infos auteur pour l'admin (sans pagination).
     */
    List<Post> findAllForAdmin() throws SQLException;

    /**
     * Compte les posts par localisation (Top locations).
     */
    List<LocationCount> countByLocationGrouped() throws SQLException;

    /**
     * Compte les likes totaux sur tous les posts.
     */
    int countAllLikes() throws SQLException;

    /**
     * Récupère l'activité mensuelle (posts créés par mois).
     */
    List<MonthlyActivity> getMonthlyActivity() throws SQLException;

    /**
     * DTO pour compter posts par location.
     */
    record LocationCount(String location, int count) {}

    /**
     * DTO pour activité mensuelle.
     */
    record MonthlyActivity(String month, int posts, int comments) {}

    /**
     * Compte les interactions (likes + comments) des dernieres 24h pour un post.
     */
    int countInteractionsLast24h(Long postId) throws SQLException;

    /** Posts d'un utilisateur (pagination), du plus recent au plus ancien. */
    List<Post> findByUserId(int userId, int offset, int limit) throws SQLException;

    int countByUserId(int userId) throws SQLException;

    List<Post> findByUserIdAndLocation(int userId, String location, int offset, int limit) throws SQLException;

    int countByUserIdAndLocation(int userId, String location) throws SQLException;

    List<Post> searchByUserId(int userId, String keyword, int offset, int limit) throws SQLException;

    int countSearchByUserId(int userId, String keyword) throws SQLException;

    List<Post> searchByUserIdLocationAndKeyword(int userId, String location, String keyword, int offset, int limit) throws SQLException;

    int countSearchByUserIdLocationAndKeyword(int userId, String location, String keyword) throws SQLException;

    /** Posts géolocalisés dans un rayon (km), tri par distance croissante. */
    List<Post> findNearbyPaginated(double lat, double lng, double radiusKm, int offset, int limit) throws SQLException;

    int countNearby(double lat, double lng, double radiusKm) throws SQLException;

    /** Même logique restreinte aux posts d'un utilisateur. */
    List<Post> findMyPostsNearbyPaginated(int userId, double lat, double lng, double radiusKm, int offset, int limit) throws SQLException;

    int countMyPostsNearby(int userId, double lat, double lng, double radiusKm) throws SQLException;

    /**
     * Posts avec coordonnées pour la carte (hors pagination liste).
     * Filtres optionnels : utilisateur, pays exact (champ location), mot-clé (sous-chaîne insensible à la casse).
     */
    List<Post> findWithCoordinatesForMap(Integer restrictUserId, String locationEquals, String keywordNeedle, int limit)
            throws SQLException;

    List<Post> findTrendingTop(int limit) throws SQLException;

    List<Post> findTrendingCandidates(int maxAgeHours, int minInteractions) throws SQLException;

    void updateTrendingState(Long postId, double score, boolean trending, double growthPct) throws SQLException;

    void insertTrendingHistory(Long postId, double score, double growthPct) throws SQLException;

    void setAdminPinned(Long postId, boolean pinned) throws SQLException;

    void setTrendExcluded(Long postId, boolean excluded) throws SQLException;
}
