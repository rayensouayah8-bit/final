package models.gestionutilisateurs;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entite {@code user}. L'{@code id} est genere par la base (AUTO_INCREMENT) : ne pas le passer au constructeur ;
 * apres {@code INSERT}, le service pose l'id via {@link #setId(Integer)}.
 */
public class User {

    private Integer id;
    private String username;
    private String email;
    private String password;
    private String profilePicture;
    /** Cle etrangere vers {@code image_asset} (photo de profil), comme {@code profileImage} Symfony. */
    private Long profileImageId;
    private List<String> roles = new ArrayList<>();
    private String role;
    private Boolean isActive;
    private Boolean faceVerified;
    private LocalDateTime faceVerifiedAt;
    private Boolean emailVerified;
    private String phone;
    private String resetToken;
    private LocalDateTime resetTokenExpiresAt;
    private Integer points;

    public User() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public Long getProfileImageId() {
        return profileImageId;
    }

    public void setProfileImageId(Long profileImageId) {
        this.profileImageId = profileImageId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles != null ? roles : new ArrayList<>();
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Boolean getFaceVerified() {
        return faceVerified;
    }

    public void setFaceVerified(Boolean faceVerified) {
        this.faceVerified = faceVerified;
    }

    public LocalDateTime getFaceVerifiedAt() {
        return faceVerifiedAt;
    }

    public void setFaceVerifiedAt(LocalDateTime faceVerifiedAt) {
        this.faceVerifiedAt = faceVerifiedAt;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getResetTokenExpiresAt() {
        return resetTokenExpiresAt;
    }

    public void setResetTokenExpiresAt(LocalDateTime resetTokenExpiresAt) {
        this.resetTokenExpiresAt = resetTokenExpiresAt;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }
}
