package models.gestionevenements;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class EventSponsorship {

    private Long id;
    private String nom;
    private String email;
    private String telephone;
    private BigDecimal montantContribution;
    private String message;
    private String statut;
    private Boolean isPaid;
    private LocalDateTime sponsoredAt;
    private Long evenementId;
    private Integer userId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public BigDecimal getMontantContribution() {
        return montantContribution;
    }

    public void setMontantContribution(BigDecimal montantContribution) {
        this.montantContribution = montantContribution;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public Boolean getIsPaid() {
        return isPaid;
    }

    public void setIsPaid(Boolean paid) {
        isPaid = paid;
    }

    public LocalDateTime getSponsoredAt() {
        return sponsoredAt;
    }

    public void setSponsoredAt(LocalDateTime sponsoredAt) {
        this.sponsoredAt = sponsoredAt;
    }

    public Long getEvenementId() {
        return evenementId;
    }

    public void setEvenementId(Long evenementId) {
        this.evenementId = evenementId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
}
