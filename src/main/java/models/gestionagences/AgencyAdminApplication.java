package models.gestionagences;

import enums.gestionagences.AgencyApplicationStatus;

import java.time.LocalDateTime;

/**
 * Demande pour devenir administrateur d'agence (table {@code agency_admin_application}).
 * L'{@code id} est auto-genere ; ne pas le passer au constructeur.
 */
public class AgencyAdminApplication {

    private Long id;
    private AgencyApplicationStatus status = AgencyApplicationStatus.PENDING;
    private String agencyNameRequested;
    private String country;
    private String messageToAdmin;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private Integer applicantId;
    private Integer reviewedById;
    private Long createdAgencyAccountId;

    public AgencyAdminApplication() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AgencyApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(AgencyApplicationStatus status) {
        this.status = status != null ? status : AgencyApplicationStatus.PENDING;
    }

    public String getAgencyNameRequested() {
        return agencyNameRequested;
    }

    public void setAgencyNameRequested(String agencyNameRequested) {
        this.agencyNameRequested = agencyNameRequested;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getMessageToAdmin() {
        return messageToAdmin;
    }

    public void setMessageToAdmin(String messageToAdmin) {
        this.messageToAdmin = messageToAdmin;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Integer getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Integer applicantId) {
        this.applicantId = applicantId;
    }

    public Integer getReviewedById() {
        return reviewedById;
    }

    public void setReviewedById(Integer reviewedById) {
        this.reviewedById = reviewedById;
    }

    public Long getCreatedAgencyAccountId() {
        return createdAgencyAccountId;
    }

    public void setCreatedAgencyAccountId(Long createdAgencyAccountId) {
        this.createdAgencyAccountId = createdAgencyAccountId;
    }
}
