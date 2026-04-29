package models.gestionevenements;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EventSponsorship {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ]{6,20}$");

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

    public void validateForPersistence(boolean createMode) {
        List<String> errors = new ArrayList<>();
        String n = nom == null ? "" : nom.trim();
        String e = email == null ? "" : email.trim();
        String t = telephone == null ? "" : telephone.trim();
        String msg = message == null ? "" : message.trim();

        if (!createMode && id == null) {
            errors.add("L'identifiant du sponsoring est obligatoire.");
        }
        if (n.isEmpty() || n.length() < 3 || n.length() > 120) {
            errors.add("Le nom complet doit contenir entre 3 et 120 caracteres.");
        }
        if (e.isEmpty() || e.length() > 190 || !EMAIL_PATTERN.matcher(e).matches()) {
            errors.add("L'email est invalide.");
        }
        if (!t.isEmpty() && !PHONE_PATTERN.matcher(t).matches()) {
            errors.add("Le format du telephone est invalide.");
        }
        if (montantContribution == null || montantContribution.compareTo(BigDecimal.ONE) < 0) {
            errors.add("Le montant de contribution doit etre superieur ou egal a 1.");
        }
        if (evenementId == null) {
            errors.add("L'evenement est obligatoire.");
        }
        if (createMode && userId == null) {
            errors.add("L'utilisateur sponsor est obligatoire.");
        }
        if (msg.length() > 500) {
            errors.add("Le message ne doit pas depasser 500 caracteres.");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }
    }
}
