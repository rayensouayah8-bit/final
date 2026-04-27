package models.gestionagences;

import java.time.LocalDateTime;

/**
 * Compte agence (table {@code agency_account}, aligne sur l'entite Symfony {@code AgencyAccount}).
 * L'{@code id} est auto-genere par la base : ne pas le passer au constructeur ; le service appelle {@link #setId(Long)} apres INSERT.
 */
public class AgencyAccount {

    private Long id;
    private String agencyName;
    private String description;
    private String websiteUrl;
    private String phone;
    private String address;
    private String country;
    private Double latitude;
    private Double longitude;
    private Boolean verified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer responsableId;
    private Long coverImageId;
    private Long agencyProfileImageId;

    public AgencyAccount() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
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

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
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

    public Integer getResponsableId() {
        return responsableId;
    }

    public void setResponsableId(Integer responsableId) {
        this.responsableId = responsableId;
    }

    public Long getCoverImageId() {
        return coverImageId;
    }

    public void setCoverImageId(Long coverImageId) {
        this.coverImageId = coverImageId;
    }

    public Long getAgencyProfileImageId() {
        return agencyProfileImageId;
    }

    public void setAgencyProfileImageId(Long agencyProfileImageId) {
        this.agencyProfileImageId = agencyProfileImageId;
    }
}
