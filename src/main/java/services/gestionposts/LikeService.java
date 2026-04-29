package services.gestionposts;

import models.gestionposts.PostLike;
import services.gestionutilisateurs.ReputationService;

import java.sql.SQLException;

/**
 * Service pour la gestion des likes avec validation.
 */
public class LikeService {

    private final LikeDAO likeDAO;
    private final PostDAO postDAO;
    private final ReputationService reputationService;

    public LikeService() {
        this.likeDAO = new LikeDAOImpl();
        this.postDAO = new PostDAOImpl();
        this.reputationService = new ReputationService();
    }

    /**
     * Ajoute un like si l'utilisateur n'a pas déjà liké le post.
     *
     * @param userId L'ID de l'utilisateur
     * @param postId L'ID du post
     * @return true si le like a été ajouté, false si déjà existant
     * @throws IllegalArgumentException Si les données sont invalides
     * @throws SQLException En cas d'erreur base de données
     */
    public boolean addLike(Integer userId, Long postId) throws IllegalArgumentException, SQLException {
        if (userId == null) {
            throw new IllegalArgumentException("L'utilisateur est obligatoire.");
        }
        if (postId == null) {
            throw new IllegalArgumentException("Le post est obligatoire.");
        }

        // Vérifier si l'utilisateur a déjà liké
        if (likeDAO.existsByUserAndPost(userId, postId)) {
            return false; // Déjà liké
        }

        PostLike like = new PostLike();
        like.setUserId(userId);
        like.setPostId(postId);

        likeDAO.create(like);
        var postOpt = postDAO.findById(postId);
        if (postOpt.isPresent() && postOpt.get().getUserId() != null && !postOpt.get().getUserId().equals(userId)) {
            reputationService.addPoints(postOpt.get().getUserId(), 2, "POST_LIKE_RECEIVED", "post", postId);
        }
        return true;
    }

    /**
     * Supprime un like (unlike).
     *
     * @param userId L'ID de l'utilisateur
     * @param postId L'ID du post
     * @throws SQLException En cas d'erreur base de données
     */
    public void removeLike(Integer userId, Long postId) throws SQLException {
        likeDAO.deleteByUserAndPost(userId, postId);
    }

    /**
     * Vérifie si l'utilisateur a déjà liké le post.
     *
     * @param userId L'ID de l'utilisateur
     * @param postId L'ID du post
     * @return true si déjà liké
     * @throws SQLException En cas d'erreur base de données
     */
    public boolean hasLiked(Integer userId, Long postId) throws SQLException {
        if (userId == null || postId == null) {
            return false;
        }
        return likeDAO.existsByUserAndPost(userId, postId);
    }

    /**
     * Compte les likes d'un post.
     *
     * @param postId L'ID du post
     * @return Le nombre de likes
     * @throws SQLException En cas d'erreur base de données
     */
    public int countByPostId(Long postId) throws SQLException {
        return likeDAO.countByPostId(postId);
    }
}
