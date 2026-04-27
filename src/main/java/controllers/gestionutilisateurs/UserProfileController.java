package controllers.gestionutilisateurs;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import models.gestionagences.ImageAsset;
import models.gestionevenements.TravelEvent;
import models.gestionutilisateurs.UserFeedback;
import models.gestionutilisateurs.User;
import services.gestionevenements.TravelEventService;
import services.gestionutilisateurs.UserFeedbackService;
import services.gestionutilisateurs.UserProfileValidationResult;
import services.gestionutilisateurs.UserService;
import controllers.home.SignedInPageControllerBase;
import utils.AvatarImageCover;
import utils.NavigationManager;
import utils.StarfieldHelper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class UserProfileController extends SignedInPageControllerBase {

    private static final double PROFILE_AVATAR_BOX = 148;
    private static final DateTimeFormatter EVENT_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");
    private static final DateTimeFormatter FEEDBACK_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @FXML private Label pageTitleLabel;
    @FXML private Label statusLabel;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private Label usernameErrorLabel;
    @FXML private Label emailErrorLabel;
    @FXML private Label phoneErrorLabel;
    @FXML private StackPane profileAvatarClipStack;
    @FXML private ImageView profileImageView;
    @FXML private Label profileImageFallbackLabel;
    @FXML private Button profilePhotoChangeButton;
    @FXML private Button profilePhotoDeleteButton;
    @FXML private VBox eventsCreatedBox;
    @FXML private Button feedbackStar1Button;
    @FXML private Button feedbackStar2Button;
    @FXML private Button feedbackStar3Button;
    @FXML private Button feedbackStar4Button;
    @FXML private Button feedbackStar5Button;
    @FXML private TextArea feedbackNoteArea;
    @FXML private Label feedbackStatusLabel;
    @FXML private VBox feedbackHistoryBox;
    @FXML private Pane signedInCosmicStarfieldPane;
    @FXML private ScrollPane userProfilePageScrollPane;

    private final UserService userService = new UserService();
    private final TravelEventService travelEventService = new TravelEventService();
    private final UserFeedbackService feedbackService = new UserFeedbackService();
    private Integer currentUserId;
    private User currentUser;
    private int selectedFeedbackStars = 5;

    @FXML
    private void initialize() {
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            nav.showLogin();
            return;
        }
        currentUser = nav.sessionUser().orElse(null);
        if (currentUser == null || currentUser.getId() == null) {
            nav.showLogin();
            return;
        }
        currentUserId = currentUser.getId();
        initSignedInSidebar();
        StarfieldHelper.populate(signedInCosmicStarfieldPane);
        refreshStarSelector(selectedFeedbackStars);
        if (profileAvatarClipStack != null) {
            profileAvatarClipStack.setClip(new Circle(PROFILE_AVATAR_BOX / 2, PROFILE_AVATAR_BOX / 2, PROFILE_AVATAR_BOX / 2));
        }
        loadUser();
        loadCreatedEvents();
        loadFeedbackHistory();
        installInvisiblePageScrollbars();
    }

    private void installInvisiblePageScrollbars() {
        if (userProfilePageScrollPane == null) {
            return;
        }
        Runnable hideBars = () -> {
            for (Node node : userProfilePageScrollPane.lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar bar) {
                    bar.setVisible(false);
                    bar.setManaged(false);
                    bar.setOpacity(0.0);
                    bar.setMinSize(0, 0);
                    bar.setPrefSize(0, 0);
                    bar.setMaxSize(0, 0);
                    bar.setMouseTransparent(true);
                } else {
                    node.setVisible(false);
                    node.setManaged(false);
                    node.setOpacity(0.0);
                    node.setMouseTransparent(true);
                }
            }
        };
        userProfilePageScrollPane.skinProperty().addListener((obs, oldSkin, newSkin) -> javafx.application.Platform.runLater(hideBars));
        userProfilePageScrollPane.viewportBoundsProperty().addListener((obs, oldB, newB) -> javafx.application.Platform.runLater(hideBars));
        javafx.application.Platform.runLater(hideBars);
    }

    private void loadUser() {
        try {
            User fromDb = userService.get(currentUserId).orElseThrow(() -> new IllegalArgumentException("User not found."));
            currentUser = fromDb;
            bindUserToForm(fromDb);
            statusLabel.setText("");
        } catch (SQLException | IllegalArgumentException e) {
            statusLabel.setText("Impossible de charger le profil: " + e.getMessage());
        }
    }

    private void bindUserToForm(User user) {
        String username = user.getUsername() == null ? "" : user.getUsername();
        String email = user.getEmail() == null ? "" : user.getEmail();
        String phone = user.getPhone() == null ? "" : user.getPhone();
        pageTitleLabel.setText(username.isBlank() ? "Mon Profil" : username);
        usernameField.setText(username);
        emailField.setText(email);
        phoneField.setText(phone);
        loadProfileImage();
        clearFieldErrors();
    }

    private void loadProfileImage() {
        profileImageView.setImage(null);
        AvatarImageCover.resetToSquare(profileImageView, PROFILE_AVATAR_BOX);
        profileImageFallbackLabel.setVisible(true);
        profileImageFallbackLabel.setManaged(true);
        profileImageFallbackLabel.setText(initialsFromUser(currentUser));
        try {
            Optional<ImageAsset> profileImage = userService.loadProfileImage(currentUserId);
            if (profileImage.isPresent() && profileImage.get().getData() != null && profileImage.get().getData().length > 0) {
                Image image = new Image(new ByteArrayInputStream(profileImage.get().getData()));
                profileImageView.setImage(image);
                AvatarImageCover.apply(profileImageView, image, PROFILE_AVATAR_BOX);
                profileImageFallbackLabel.setVisible(false);
                profileImageFallbackLabel.setManaged(false);
            }
        } catch (SQLException ignored) {
            // Keep fallback initials.
        }
        updateProfilePhotoActionButtonsVisibility();
    }

    /** With a stored profile image: only × (delete) in the FAB slot. Without: only + (add/change). Same corner placement for both. */
    private void updateProfilePhotoActionButtonsVisibility() {
        if (profilePhotoChangeButton == null || profilePhotoDeleteButton == null) {
            return;
        }
        boolean hasPhoto = currentUser != null && currentUser.getProfileImageId() != null;
        profilePhotoChangeButton.setVisible(!hasPhoto);
        profilePhotoChangeButton.setManaged(!hasPhoto);
        profilePhotoDeleteButton.setVisible(hasPhoto);
        profilePhotoDeleteButton.setManaged(hasPhoto);
    }

    private void syncSessionUserFromDb() throws SQLException {
        User refreshed = userService.get(currentUserId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        refreshed.setPassword(null);
        currentUser = refreshed;
        NavigationManager.getInstance().setSessionUser(refreshed);
    }

    @FXML
    private void onProfilePhotoChange(ActionEvent event) {
        pickAndUploadProfilePhoto();
    }

    @FXML
    private void onProfilePhotoDelete(ActionEvent event) {
        removeProfilePhoto();
    }

    private void pickAndUploadProfilePhoto() {
        setStatus(statusLabel, null);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une photo de profil");
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );
        File file = chooser.showOpenDialog(usernameField.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String mime = detectMime(file, bytes);
            userService.replaceProfileImage(currentUserId, bytes, mime);
            syncSessionUserFromDb();
            loadProfileImage();
            refreshSignedInSidebarIdentity();
            setStatus(statusLabel, "Profile photo updated.");
        } catch (IOException | SQLException | IllegalArgumentException e) {
            setStatus(statusLabel, "Invalid image: " + e.getMessage());
        }
    }

    private void removeProfilePhoto() {
        setStatus(statusLabel, null);
        if (currentUser == null || currentUser.getProfileImageId() == null) {
            return;
        }
        try {
            userService.clearProfileImage(currentUserId);
            syncSessionUserFromDb();
            loadProfileImage();
            refreshSignedInSidebarIdentity();
            setStatus(statusLabel, "Profile photo removed.");
        } catch (SQLException | IllegalArgumentException e) {
            setStatus(statusLabel, "Could not remove photo: " + e.getMessage());
        }
    }

    @FXML
    private void onSaveProfile() {
        setStatus(statusLabel, null);
        clearFieldErrors();
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        try {
            UserProfileValidationResult validation = userService.validateProfileUpdate(currentUserId, username, email, phone);
            if (!validation.isValid()) {
                applyFieldErrors(validation);
                setStatus(statusLabel, "Veuillez corriger les erreurs du formulaire.");
                return;
            }
            User updated = userService.updateProfile(currentUserId, username, email, phone);
            NavigationManager.getInstance().setSessionUser(updated);
            currentUser = updated;
            bindUserToForm(updated);
            setStatus(statusLabel, "Profil mis a jour avec succes.");
        } catch (SQLException | IllegalArgumentException e) {
            setStatus(statusLabel, "Update failed: " + e.getMessage());
        }
    }

    @FXML
    private void onSubmitFeedback() {
        setStatus(feedbackStatusLabel, null);
        int stars = selectedFeedbackStars;
        String note = feedbackNoteArea.getText() == null ? "" : feedbackNoteArea.getText().trim();
        try {
            UserFeedback feedback = new UserFeedback();
            feedback.setUserId(currentUserId);
            feedback.setStars(stars);
            feedback.setNote(note);
            feedback.setCreatedAt(LocalDateTime.now());
            feedbackService.insert(feedback);
            feedbackNoteArea.clear();
            selectedFeedbackStars = 5;
            refreshStarSelector(selectedFeedbackStars);
            loadFeedbackHistory();
            setStatus(feedbackStatusLabel, "Thanks! Your feedback has been saved.");
        } catch (SQLException | IllegalArgumentException e) {
            setStatus(feedbackStatusLabel, "Unable to save feedback: " + e.getMessage());
        }
    }

    @FXML
    private void onFeedbackStarHover(MouseEvent event) {
        Button source = (Button) event.getSource();
        int hovered = parseStarValue(source);
        refreshStarSelector(hovered);
    }

    @FXML
    private void onFeedbackStarExit() {
        refreshStarSelector(selectedFeedbackStars);
    }

    @FXML
    private void onFeedbackStarSelected(ActionEvent event) {
        if (event.getSource() instanceof Button b) {
            selectedFeedbackStars = parseStarValue(b);
        }
        refreshStarSelector(selectedFeedbackStars);
    }

    private int parseStarValue(Button button) {
        Object userData = button.getUserData();
        if (userData instanceof String s) {
            try {
                return Math.max(1, Math.min(5, Integer.parseInt(s)));
            } catch (NumberFormatException ignored) {
                return 5;
            }
        }
        return 5;
    }

    private void refreshStarSelector(int highlighted) {
        styleStar(feedbackStar1Button, highlighted >= 1);
        styleStar(feedbackStar2Button, highlighted >= 2);
        styleStar(feedbackStar3Button, highlighted >= 3);
        styleStar(feedbackStar4Button, highlighted >= 4);
        styleStar(feedbackStar5Button, highlighted >= 5);
    }

    private void styleStar(Button star, boolean active) {
        if (star == null) {
            return;
        }
        star.getStyleClass().remove("star-rating-button-active");
        if (active) {
            star.getStyleClass().add("star-rating-button-active");
        }
    }

    private void loadCreatedEvents() {
        eventsCreatedBox.getChildren().clear();
        try {
            List<TravelEvent> events = travelEventService.findByCreator(currentUserId);
            if (events.isEmpty()) {
                eventsCreatedBox.getChildren().add(buildEmptyLine("No events created yet."));
                return;
            }
            for (TravelEvent e : events) {
                VBox card = new VBox(3);
                card.getStyleClass().add("user-profile-mini-item");
                Label title = new Label(safe(e.getTitle(), "Event"));
                title.getStyleClass().add("user-profile-mini-title");
                String when = e.getEventDate() == null ? "Date non renseignee" : EVENT_DATE_FMT.format(e.getEventDate());
                Label meta = new Label(safe(e.getLocation(), "Lieu non renseigne") + " - " + when);
                meta.getStyleClass().add("user-profile-mini-meta");
                card.getChildren().addAll(title, meta);
                eventsCreatedBox.getChildren().add(card);
            }
        } catch (SQLException e) {
            eventsCreatedBox.getChildren().add(buildEmptyLine("Impossible de charger les evenements: " + e.getMessage()));
        }
    }

    private void loadFeedbackHistory() {
        feedbackHistoryBox.getChildren().clear();
        try {
            List<UserFeedback> rows = feedbackService.findByUser(currentUserId);
            if (rows.isEmpty()) {
                feedbackHistoryBox.getChildren().add(buildEmptyLine("You have not published feedback yet."));
                return;
            }
            for (UserFeedback f : rows) {
                VBox card = new VBox(4);
                card.getStyleClass().add("user-profile-mini-item");
                HBox top = new HBox();
                Label stars = new Label("★".repeat(Math.max(1, Math.min(5, f.getStars() == null ? 0 : f.getStars()))));
                stars.getStyleClass().add("user-profile-mini-stars");
                Region spacer = new Region();
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                String d = f.getCreatedAt() == null ? "" : FEEDBACK_DATE_FMT.format(f.getCreatedAt());
                Label date = new Label(d);
                date.getStyleClass().add("user-profile-mini-meta");
                top.getChildren().addAll(stars, spacer, date);
                Label note = new Label(safe(f.getNote(), ""));
                note.setWrapText(true);
                note.getStyleClass().add("user-profile-mini-note");
                card.getChildren().addAll(top, note);
                feedbackHistoryBox.getChildren().add(card);
            }
        } catch (SQLException e) {
            feedbackHistoryBox.getChildren().add(buildEmptyLine("Historique indisponible: " + e.getMessage()));
        }
    }

    private Label buildEmptyLine(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("user-profile-mini-empty");
        l.setWrapText(true);
        return l;
    }

    private static String detectMime(File file, byte[] bytes) throws IOException {
        String probe = Files.probeContentType(file.toPath());
        if (probe != null && probe.startsWith("image/")) {
            return probe;
        }
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webp")) return "image/webp";
        if (bytes.length >= 8) {
            if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
                return "image/png";
            }
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
                return "image/jpeg";
            }
        }
        throw new IllegalArgumentException("Format image non supporte.");
    }

    private void clearFieldErrors() {
        setError(usernameErrorLabel, null);
        setError(emailErrorLabel, null);
        setError(phoneErrorLabel, null);
    }

    private void applyFieldErrors(UserProfileValidationResult validation) {
        setError(usernameErrorLabel, validation.getError(UserProfileValidationResult.FIELD_USERNAME));
        setError(emailErrorLabel, validation.getError(UserProfileValidationResult.FIELD_EMAIL));
        setError(phoneErrorLabel, validation.getError(UserProfileValidationResult.FIELD_PHONE));
    }

    private static void setError(Label label, String message) {
        if (message == null || message.isBlank()) {
            label.setText("");
            label.setManaged(false);
            label.setVisible(false);
        } else {
            label.setText(message);
            label.setManaged(true);
            label.setVisible(true);
        }
    }

    private static String initialsFromUser(User user) {
        if (user == null) return "U";
        String base = user.getUsername();
        if (base == null || base.isBlank()) {
            base = user.getEmail();
        }
        if (base == null || base.isBlank()) {
            return "U";
        }
        String cleaned = base.trim();
        if (cleaned.length() == 1) {
            return cleaned.toUpperCase();
        }
        return cleaned.substring(0, 2).toUpperCase();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void setStatus(Label label, String message) {
        if (label == null) return;
        if (message == null || message.isBlank()) {
            label.setText("");
            label.setVisible(false);
            label.setManaged(false);
        } else {
            label.setText(message);
            label.setVisible(true);
            label.setManaged(true);
        }
    }

}
