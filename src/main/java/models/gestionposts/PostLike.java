package models.gestionposts;

import java.time.LocalDateTime;

/**
 * Modèle représentant un "like" sur un post.
 * Nommé PostLike car "Like" est un mot réservé SQL.
 */
public class PostLike {
    private Long id;
    private Integer userId;
    private Long postId;
    private LocalDateTime createdAt;

    public PostLike() {
    }

    public PostLike(Long id, Integer userId, Long postId, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.postId = postId;
        this.createdAt = createdAt;
    }

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "PostLike{" +
                "id=" + id +
                ", userId=" + userId +
                ", postId=" + postId +
                ", createdAt=" + createdAt +
                '}';
    }
}
