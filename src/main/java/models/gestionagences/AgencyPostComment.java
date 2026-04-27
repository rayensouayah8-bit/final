package models.gestionagences;

import java.time.LocalDateTime;

public class AgencyPostComment {
    private Long id;
    private Long agencyPostId;
    private Integer authorId;
    private String authorUsername;
    private Long authorProfileImageId;
    private String content;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAgencyPostId() {
        return agencyPostId;
    }

    public void setAgencyPostId(Long agencyPostId) {
        this.agencyPostId = agencyPostId;
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

    public Long getAuthorProfileImageId() {
        return authorProfileImageId;
    }

    public void setAuthorProfileImageId(Long authorProfileImageId) {
        this.authorProfileImageId = authorProfileImageId;
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
}
