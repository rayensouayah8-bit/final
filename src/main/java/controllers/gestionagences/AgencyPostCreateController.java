package controllers.gestionagences;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import models.gestionagences.AgencyAccount;
import models.gestionagences.ImageAsset;
import services.gestionagences.AgencyAccountService;
import services.gestionagences.AgencyPostService;
import services.gestionagences.AgencyPostValidationResult;
import services.gestionagences.ImageAssetService;
import controllers.home.SignedInPageControllerBase;
import utils.NavigationManager;
import utils.StarfieldHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AgencyPostCreateController extends SignedInPageControllerBase {
    private static final int MAX_IMAGES = 10;
    private static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;
    private static final int THUMB_LOAD_W = 196;
    private static final int THUMB_LOAD_H = 148;

    @FXML private TextField titleField;
    @FXML private TextArea contentArea;
    @FXML private Label titleErrorLabel;
    @FXML private Label contentErrorLabel;
    @FXML private Label feedbackLabel;
    @FXML private VBox dropArea;
    @FXML private Label dropHintLabel;
    @FXML private FlowPane thumbnailsFlow;
    @FXML private ListView<String> uploadErrorsList;
    @FXML private Pane signedInCosmicStarfieldPane;

    private final AgencyAccountService agencyService = new AgencyAccountService();
    private final AgencyPostService agencyPostService = new AgencyPostService();
    private final ImageAssetService imageAssetService = new ImageAssetService();
    private final List<File> selectedImageFiles = new ArrayList<>();
    private AgencyAccount targetAgency;
    private boolean canCreatePost;

    @FXML
    private void initialize() {
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            nav.showLogin();
            return;
        }
        initSignedInSidebar();
        Integer userId = nav.sessionUser().map(u -> u.getId()).orElse(null);
        if (userId == null) {
            nav.showLogin();
            return;
        }
        Long selectedAgencyId = nav.selectedAgencyId().orElse(null);
        try {
            Optional<AgencyAccount> agency = selectedAgencyId != null
                    ? agencyService.get(selectedAgencyId)
                    : agencyService.findByResponsableId(userId);
            if (agency.isEmpty()) {
                feedbackLabel.setText("Agency not found.");
                nav.showSignedInAgencies();
                return;
            }
            targetAgency = agency.get();
            boolean isOwnerAdmin = targetAgency.getResponsableId() != null
                    && targetAgency.getResponsableId().equals(userId)
                    && nav.canAccessAgencyAdminFeatures();
            boolean isPlatformAdmin = nav.canAccessAdminFeatures();
            canCreatePost = isOwnerAdmin || isPlatformAdmin;
            if (!canCreatePost) {
                feedbackLabel.setText("You are not allowed to create posts for this agency.");
            }
            StarfieldHelper.populate(signedInCosmicStarfieldPane);
            configureDragAndDrop();
            clearPostFieldErrors();
        } catch (SQLException e) {
            feedbackLabel.setText("Cannot load agency context: " + e.getMessage());
        }
    }

    @FXML
    private void onChooseImages() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose post images");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp")
        );
        List<File> files = chooser.showOpenMultipleDialog(titleField.getScene() != null ? titleField.getScene().getWindow() : null);
        if (files == null || files.isEmpty()) {
            return;
        }
        addSelectedFiles(files);
    }

    @FXML
    private void onCreatePost() {
        feedbackLabel.setText("");
        clearPostFieldErrors();
        if (!canCreatePost || targetAgency == null || targetAgency.getId() == null) {
            feedbackLabel.setText("You are not allowed to create posts for this agency.");
            return;
        }
        Integer authorId = NavigationManager.getInstance().sessionUser().map(u -> u.getId()).orElse(null);
        if (authorId == null) {
            NavigationManager.getInstance().showLogin();
            return;
        }
        AgencyPostValidationResult validation = agencyPostService.validatePostDraft(
                titleField.getText(), contentArea.getText());
        if (!validation.isValid()) {
            applyPostFieldErrors(validation);
            return;
        }
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        String content = contentArea.getText() == null ? "" : contentArea.getText().trim();
        List<Long> imageIds = new ArrayList<>();
        for (File file : selectedImageFiles) {
            try {
                String mime = detectMime(file);
                if (mime == null) {
                    continue;
                }
                ImageAsset asset = new ImageAsset();
                asset.setMimeType(mime);
                asset.setData(Files.readAllBytes(file.toPath()));
                imageAssetService.insert(asset);
                imageIds.add(asset.getId());
            } catch (IOException | SQLException ignored) {
                // Continue with remaining files.
            }
        }
        try {
            agencyPostService.createPost(targetAgency.getId(), authorId, title, content, imageIds);
            NavigationManager.getInstance().showAgencyProfile(targetAgency.getId());
        } catch (SQLException | IllegalArgumentException e) {
            feedbackLabel.setText("Cannot create post: " + e.getMessage());
        }
    }

    private void clearPostFieldErrors() {
        setFieldError(titleErrorLabel, null);
        setFieldError(contentErrorLabel, null);
    }

    private void applyPostFieldErrors(AgencyPostValidationResult r) {
        setFieldError(titleErrorLabel, r.getError(AgencyPostValidationResult.FIELD_TITLE));
        setFieldError(contentErrorLabel, r.getError(AgencyPostValidationResult.FIELD_CONTENT));
    }

    private static void setFieldError(Label label, String message) {
        if (label == null) {
            return;
        }
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

    @FXML
    private void onCancel() {
        if (targetAgency != null && targetAgency.getId() != null) {
            NavigationManager.getInstance().showAgencyProfile(targetAgency.getId());
        } else {
            NavigationManager.getInstance().showSignedInAgencies();
        }
    }

    private void configureDragAndDrop() {
        if (dropArea == null) {
            return;
        }
        dropArea.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        dropArea.setOnDragEntered(event -> {
            if (event.getDragboard().hasFiles() && !dropArea.getStyleClass().contains("agency-upload-drop-area-active")) {
                dropArea.getStyleClass().add("agency-upload-drop-area-active");
            }
            event.consume();
        });
        dropArea.setOnDragExited(event -> {
            dropArea.getStyleClass().remove("agency-upload-drop-area-active");
            event.consume();
        });
        dropArea.setOnDragDropped(event -> {
            boolean ok = false;
            if (event.getDragboard().hasFiles()) {
                addSelectedFiles(event.getDragboard().getFiles());
                ok = true;
            }
            dropArea.getStyleClass().remove("agency-upload-drop-area-active");
            event.setDropCompleted(ok);
            event.consume();
        });
    }

    private void addSelectedFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        List<String> errors = new ArrayList<>();
        for (File file : files) {
            if (file == null) {
                continue;
            }
            if (selectedImageFiles.size() >= MAX_IMAGES) {
                errors.add(file.getName() + ": max " + MAX_IMAGES + " images allowed.");
                continue;
            }
            String mime = detectMime(file);
            if (mime == null) {
                errors.add(file.getName() + ": unsupported format (PNG/JPG/GIF/WEBP only).");
                continue;
            }
            long size = file.length();
            if (size > MAX_FILE_BYTES) {
                errors.add(file.getName() + ": too large (max 8 MB).");
                continue;
            }
            boolean exists = selectedImageFiles.stream()
                    .anyMatch(existing -> existing.getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath()));
            if (!exists) {
                selectedImageFiles.add(file);
            } else {
                errors.add(file.getName() + ": already selected.");
            }
        }
        refreshThumbnails();
        refreshUploadErrors(errors);
        if (!errors.isEmpty()) {
            feedbackLabel.setText("Some files were skipped. Check upload errors.");
        }
    }

    private void refreshThumbnails() {
        if (thumbnailsFlow == null) {
            return;
        }
        thumbnailsFlow.getChildren().clear();
        for (File file : selectedImageFiles) {
            thumbnailsFlow.getChildren().add(buildThumbnail(file));
        }
        if (dropHintLabel != null) {
            dropHintLabel.setText(selectedImageFiles.isEmpty()
                    ? "Drag and drop images here, or use the button above."
                    : selectedImageFiles.size() + "/" + MAX_IMAGES + " image(s) selected. Use × to remove.");
        }
    }

    private void refreshUploadErrors(List<String> errors) {
        if (uploadErrorsList == null) {
            return;
        }
        uploadErrorsList.getItems().setAll(errors);
        boolean show = errors != null && !errors.isEmpty();
        uploadErrorsList.setVisible(show);
        uploadErrorsList.setManaged(show);
    }

    private VBox buildThumbnail(File file) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(THUMB_LOAD_W);
        imageView.setFitHeight(THUMB_LOAD_H);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.getStyleClass().add("agency-upload-thumb-image");
        imageView.setImage(new Image(file.toURI().toString(), THUMB_LOAD_W, THUMB_LOAD_H, true, true, true));

        Button removeButton = new Button("×");
        removeButton.getStyleClass().add("agency-upload-thumb-remove");
        removeButton.setOnAction(e -> {
            selectedImageFiles.removeIf(f -> f.getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath()));
            refreshThumbnails();
        });

        StackPane imageWrap = new StackPane(imageView, removeButton);
        StackPane.setAlignment(removeButton, Pos.TOP_RIGHT);
        imageWrap.getStyleClass().add("agency-upload-thumb-wrap");

        Label fileName = new Label(trimFileName(file.getName()));
        fileName.getStyleClass().add("agency-upload-thumb-label");

        VBox card = new VBox(6, imageWrap, fileName);
        card.getStyleClass().add("agency-upload-thumb-card");
        return card;
    }

    private String trimFileName(String name) {
        if (name == null) {
            return "";
        }
        if (name.length() <= 28) {
            return name;
        }
        return name.substring(0, 25) + "...";
    }

    private String detectMime(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        return null;
    }
}
