package services.gestionposts;

import models.gestionposts.PostLike;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Interface DAO pour les likes.
 * Définit les opérations de création, suppression et vérification.
 */
public interface LikeDAO {

    void create(PostLike like) throws SQLException;

    void delete(Long id) throws SQLException;

    void deleteByUserAndPost(Integer userId, Long postId) throws SQLException;

    Optional<PostLike> findById(Long id) throws SQLException;

    Optional<PostLike> findByUserAndPost(Integer userId, Long postId) throws SQLException;

    boolean existsByUserAndPost(Integer userId, Long postId) throws SQLException;

    int countByPostId(Long postId) throws SQLException;
}
