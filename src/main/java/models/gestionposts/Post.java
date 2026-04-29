package models.gestionposts;

import java.time.LocalDateTime;

/**
 * Modèle représentant un post de voyage dans la base de données.
 * Correspond à la table 'post' créée par Symfony.
 */
public class Post {

    private Long id;
    private String titre;
    private String contenu;
    private String location;
    private String imageUrl;
    private Integer userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double latitude;
    private Double longitude;
    private Integer nbLikes;
    private Integer nbCommentaires;
    private Double trendingScore;
    private Boolean trending;
    private Double trendingGrowthPct;
    private Boolean adminPinned;
    private Boolean trendExcluded;
    private Integer positiveCommentsCount;

    public Post() {
        this.nbLikes = 0;
        this.nbCommentaires = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getNbLikes() {
        return nbLikes;
    }

    public void setNbLikes(Integer nbLikes) {
        this.nbLikes = nbLikes != null ? nbLikes : 0;
    }

    public Integer getNbCommentaires() {
        return nbCommentaires;
    }

    public void setNbCommentaires(Integer nbCommentaires) {
        this.nbCommentaires = nbCommentaires != null ? nbCommentaires : 0;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    /**
     * Retourne un résumé du contenu (pour l'affichage dans les cartes)
     */
    public String getContenuResume(int maxLength) {
        if (contenu == null || contenu.length() <= maxLength) {
            return contenu;
        }
        return contenu.substring(0, maxLength) + "...";
    }

    public Double getTrendingScore() {
        return trendingScore;
    }

    public void setTrendingScore(Double trendingScore) {
        this.trendingScore = trendingScore;
    }

    public Boolean getTrending() {
        return trending;
    }

    public void setTrending(Boolean trending) {
        this.trending = trending;
    }

    public Double getTrendingGrowthPct() {
        return trendingGrowthPct;
    }

    public void setTrendingGrowthPct(Double trendingGrowthPct) {
        this.trendingGrowthPct = trendingGrowthPct;
    }

    public Boolean getAdminPinned() {
        return adminPinned;
    }

    public void setAdminPinned(Boolean adminPinned) {
        this.adminPinned = adminPinned;
    }

    public Boolean getTrendExcluded() {
        return trendExcluded;
    }

    public void setTrendExcluded(Boolean trendExcluded) {
        this.trendExcluded = trendExcluded;
    }

    public Integer getPositiveCommentsCount() {
        return positiveCommentsCount;
    }

    public void setPositiveCommentsCount(Integer positiveCommentsCount) {
        this.positiveCommentsCount = positiveCommentsCount;
    }

    @Override
    public String toString() {
        return "Post{" +
                "id=" + id +
                ", titre='" + titre + '\'' +
                ", location='" + location + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", nbLikes=" + nbLikes +
                ", nbCommentaires=" + nbCommentaires +
                ", trendingScore=" + trendingScore +
                ", trending=" + trending +
                ", positiveCommentsCount=" + positiveCommentsCount +
                '}';
    }
}
