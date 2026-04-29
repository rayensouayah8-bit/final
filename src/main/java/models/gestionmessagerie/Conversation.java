package models.gestionmessagerie;

import java.time.LocalDateTime;

public class Conversation {

    private Long id;
    private Long eventId;
    private Integer travelerUserId;
    private Integer agencyResponsableUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;
    private String eventTitle;
    private String agencyName;
    private String travelerName;

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

    public Integer getTravelerUserId() {
        return travelerUserId;
    }

    public void setTravelerUserId(Integer travelerUserId) {
        this.travelerUserId = travelerUserId;
    }

    public Integer getAgencyResponsableUserId() {
        return agencyResponsableUserId;
    }

    public void setAgencyResponsableUserId(Integer agencyResponsableUserId) {
        this.agencyResponsableUserId = agencyResponsableUserId;
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

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }

    public String getTravelerName() {
        return travelerName;
    }

    public void setTravelerName(String travelerName) {
        this.travelerName = travelerName;
    }
}
