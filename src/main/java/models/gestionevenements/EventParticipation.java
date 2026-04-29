package models.gestionevenements;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EventParticipation {

    private Long id;
    private Long eventId;
    private Integer userId;
    private String status;
    private String requesterName;
    private String contactPhone;
    private String requestNote;
    private LocalDateTime joinedAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getRequestNote() {
        return requestNote;
    }

    public void setRequestNote(String requestNote) {
        this.requestNote = requestNote;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void validateForPersistence(boolean createMode) {
        List<String> errors = new ArrayList<>();
        if (!createMode && id == null) {
            errors.add("L'identifiant de participation est obligatoire.");
        }
        if (createMode && eventId == null) {
            errors.add("L'evenement est obligatoire.");
        }
        if (createMode && userId == null) {
            errors.add("L'utilisateur est obligatoire.");
        }
        String phone = contactPhone == null ? "" : contactPhone.trim();
        if (!phone.isEmpty() && !phone.matches("^\\+?[0-9 ]{6,20}$")) {
            errors.add("Le format du telephone est invalide.");
        }
        String name = requesterName == null ? "" : requesterName.trim();
        if (createMode && (name.length() < 3 || name.length() > 120)) {
            errors.add("Le nom complet doit contenir entre 3 et 120 caracteres.");
        }
        String note = requestNote == null ? "" : requestNote.trim();
        if (createMode && note.length() < 8) {
            errors.add("La note de participation doit contenir au moins 8 caracteres.");
        }
        if (note.length() > 500) {
            errors.add("La note de participation ne doit pas depasser 500 caracteres.");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }
    }
}
