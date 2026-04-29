package models.gestionevenements;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TravelEvent {

    private Long id;
    private String title;
    private String description;
    private String location;
    private LocalDateTime eventDate;
    private Integer maxParticipants;
    private String imagePath;
    private String status;
    private Integer createdByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Integer createdByUserId) {
        this.createdByUserId = createdByUserId;
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

    public void validateForPersistence(boolean createMode) {
        List<String> errors = new ArrayList<>();
        String t = title == null ? "" : title.trim();
        String l = location == null ? "" : location.trim();
        String d = description == null ? "" : description.trim();

        if (!createMode && id == null) {
            errors.add("L'identifiant de l'evenement est obligatoire.");
        }
        if (t.isEmpty() || t.length() < 6 || t.length() > 80) {
            errors.add("Le titre doit contenir entre 6 et 80 caracteres.");
        }
        if (l.isEmpty() || l.length() < 2 || l.length() > 80) {
            errors.add("La localisation doit contenir entre 2 et 80 caracteres.");
        }
        if (d.length() > 500) {
            errors.add("La description ne doit pas depasser 500 caracteres.");
        }
        if (eventDate == null || !eventDate.isAfter(LocalDateTime.now())) {
            errors.add("La date et l'heure de l'evenement doivent etre dans le futur.");
        }
        if (createMode && createdByUserId == null) {
            errors.add("Le createur de l'evenement est obligatoire.");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }
    }
}
