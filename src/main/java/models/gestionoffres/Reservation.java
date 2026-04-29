package models.gestionoffres;

import java.time.LocalDateTime;

public class Reservation {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_CANCELLED = "cancelled";

    private Long id;
    private TravelOffer offer;
    private Long userId;
    private String contactInfo;
    private Integer reservedSeats;
    private LocalDateTime reservationDate;
    private String status;
    private Boolean isPaid;

    public Reservation() {
    }

    public Reservation(Long id, TravelOffer offer, Long userId, String contactInfo, Integer reservedSeats,
                       LocalDateTime reservationDate, String status, Boolean isPaid) {
        this.id = id;
        this.offer = offer;
        this.userId = userId;
        this.contactInfo = contactInfo;
        this.reservedSeats = reservedSeats;
        this.reservationDate = reservationDate;
        this.status = status;
        this.isPaid = isPaid;
    }

    public double getTotalPrice() {
        double offerPrice = offer != null && offer.getPrice() != null ? offer.getPrice() : 0.0;
        int seats = reservedSeats != null ? reservedSeats : 0;
        return offerPrice * seats;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TravelOffer getOffer() {
        return offer;
    }

    public void setOffer(TravelOffer offer) {
        this.offer = offer;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public Integer getReservedSeats() {
        return reservedSeats;
    }

    public void setReservedSeats(Integer reservedSeats) {
        this.reservedSeats = reservedSeats;
    }

    public LocalDateTime getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(LocalDateTime reservationDate) {
        this.reservationDate = reservationDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getPaid() {
        return isPaid;
    }

    public void setPaid(Boolean paid) {
        isPaid = paid;
    }

    public Boolean getIsPaid() {
        return isPaid;
    }

    public void setIsPaid(Boolean isPaid) {
        this.isPaid = isPaid;
    }

    @Override
    public String toString() {
        return "Reservation{" +
                "id=" + id +
                ", offerId=" + (offer != null ? offer.getId() : null) +
                ", userId=" + userId +
                ", reservedSeats=" + reservedSeats +
                ", status='" + status + '\'' +
                ", isPaid=" + isPaid +
                '}';
    }
}
