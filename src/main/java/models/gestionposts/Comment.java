package models.gestionposts;

import enums.gestionposts.Sentiment;

import java.time.LocalDateTime;

/**
 * Modèle représentant un commentaire sur un post.
 */
public class Comment {
    private Long id;
    private String content;
    private Integer userId;
    private Long postId;
    private LocalDateTime createdAt;
    private Sentiment sentiment = Sentiment.NEUTRAL;

    // Informations de l'auteur (pour affichage)
    private String authorUsername;
    private String authorLevel;
    private Integer authorReputationScore;

    public Comment() {
    }

    public Comment(Long id, String content, Integer userId, Long postId, LocalDateTime createdAt) {
        this.id = id;
        this.content = content;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public String getAuthorUsername() {
        return authorUsername;
    }

    public void setAuthorUsername(String authorUsername) {
        this.authorUsername = authorUsername;
    }

    public String getAuthorLevel() {
        return authorLevel;
    }

    public void setAuthorLevel(String authorLevel) {
        this.authorLevel = authorLevel;
    }

    public Integer getAuthorReputationScore() {
        return authorReputationScore;
    }

    public void setAuthorReputationScore(Integer authorReputationScore) {
        this.authorReputationScore = authorReputationScore;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public void setSentiment(Sentiment sentiment) {
        this.sentiment = sentiment != null ? sentiment : Sentiment.NEUTRAL;
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", userId=" + userId +
                ", postId=" + postId +
                ", createdAt=" + createdAt +
                ", sentiment=" + sentiment +
                '}';
    }
}
