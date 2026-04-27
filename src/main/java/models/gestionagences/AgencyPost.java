package models.gestionagences;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AgencyPost {
    private Long id;
    private Long agencyId;
    private Integer authorId;
    private String authorUsername;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private int likesCount;
    private int commentsCount;
    private boolean likedByViewer;
    private final List<Long> imageAssetIds = new ArrayList<>();
    private final List<AgencyPostComment> comments = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(Long agencyId) {
        this.agencyId = agencyId;
    }

    public Integer getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Integer authorId) {
        this.authorId = authorId;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public void setAuthorUsername(String authorUsername) {
        this.authorUsername = authorUsername;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getLikesCount() {
        return likesCount;
    }

    public void setLikesCount(int likesCount) {
        this.likesCount = likesCount;
    }

    public int getCommentsCount() {
        return commentsCount;
    }

    public void setCommentsCount(int commentsCount) {
        this.commentsCount = commentsCount;
    }

    public boolean isLikedByViewer() {
        return likedByViewer;
    }

    public void setLikedByViewer(boolean likedByViewer) {
        this.likedByViewer = likedByViewer;
    }

    public List<Long> getImageAssetIds() {
        return imageAssetIds;
    }

    public List<AgencyPostComment> getComments() {
        return comments;
    }
}
