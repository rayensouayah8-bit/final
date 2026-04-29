package services.gestionposts;

import models.gestionposts.Comment;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Interface DAO pour les commentaires.
 * Définit les opérations CRUD et de recherche.
 */
public interface CommentDAO {

    void create(Comment comment) throws SQLException;

    void update(Comment comment) throws SQLException;

    void delete(Long id) throws SQLException;

    Optional<Comment> findById(Long id) throws SQLException;

    List<Comment> findByPostId(Long postId) throws SQLException;

    List<Comment> findByPostIdOrdered(Long postId) throws SQLException;

    int countByPostId(Long postId) throws SQLException;

    boolean isAuthor(Long commentId, Integer userId) throws SQLException;

    /**
     * Récupère tous les commentaires pour l'admin (avec infos auteur et post).
     */
    List<Comment> findAllForAdmin() throws SQLException;

    /**
     * Compte le nombre total de commentaires.
     */
    int countAll() throws SQLException;
}
