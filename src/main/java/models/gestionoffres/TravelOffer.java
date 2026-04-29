package models.gestionoffres;

import java.time.LocalDateTime;

public class TravelOffer {
    private Long id;
    private String title;
    private String countries;
    private String description;
    private LocalDateTime departureDate;
    private LocalDateTime returnDate;
    private Double price;
    private String currency;
    private Integer availableSeats;
    private String image;
    private Long agencyId;
    private Long createdById;
    private LocalDateTime createdAt;
    private String approvalStatus;

    public TravelOffer() {
    }

    public TravelOffer(Long id, String title, String countries, String description, LocalDateTime departureDate,
                       LocalDateTime returnDate, Double price, String currency, Integer availableSeats, String image,
                       Long agencyId, Long createdById, LocalDateTime createdAt, String approvalStatus) {
        this.id = id;
        this.title = title;
        this.countries = countries;
        this.description = description;
        this.departureDate = departureDate;
        this.returnDate = returnDate;
        this.price = price;
        this.currency = currency;
        this.availableSeats = availableSeats;
        this.image = image;
        this.agencyId = agencyId;
        this.createdById = createdById;
        this.createdAt = createdAt;
        this.approvalStatus = approvalStatus;
    }

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

    public String getCountries() {
        return countries;
    }

    public void setCountries(String countries) {
        this.countries = countries;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(LocalDateTime departureDate) {
        this.departureDate = departureDate;
    }

    public LocalDateTime getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDateTime returnDate) {
        this.returnDate = returnDate;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(Integer availableSeats) {
        this.availableSeats = availableSeats;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Long getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(Long agencyId) {
        this.agencyId = agencyId;
    }

    public Long getCreatedById() {
        return createdById;
    }

    public void setCreatedById(Long createdById) {
        this.createdById = createdById;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    @Override
    public String toString() {
        return "TravelOffer{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", countries='" + countries + '\'' +
                ", departureDate=" + departureDate +
                ", returnDate=" + returnDate +
                ", price=" + price +
                ", currency='" + currency + '\'' +
                ", availableSeats=" + availableSeats +
                ", agencyId=" + agencyId +
                ", createdById=" + createdById +
                ", approvalStatus='" + approvalStatus + '\'' +
                '}';
    }
}
