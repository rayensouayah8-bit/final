package controllers.gestionagences;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import models.gestionagences.AgencyAccount;
import models.gestionagences.AgencyPost;
import models.gestionagences.AgencyPostComment;
import models.gestionagences.ImageAsset;
import org.controlsfx.control.Notifications;
import services.gestionagences.AgencyAccountService;
import services.gestionagences.AgencyAccountValidationResult;
import services.gestionagences.AgencyPostService;
import services.gestionagences.ImageAssetService;
import controllers.home.SignedInPageControllerBase;
import utils.NavigationManager;
import utils.StarfieldHelper;

import java.io.ByteArrayInputStream;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MyAgencyController extends SignedInPageControllerBase {

    private static final String FALLBACK_BANNER_RESOURCE = "/images/agency/fallback-banner.jpg";
    private static final String FALLBACK_LOGO_RESOURCE = "/images/agency/fallback-logo.jpg";
    private static final String FALLBACK_BANNER_REMOTE =
            "https://images.unsplash.com/photo-1534796636912-3b95b3ab5986?auto=format&fit=crop&w=1400&q=85";
    private static final String FALLBACK_LOGO_REMOTE =
            "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=512&q=85";

    @FXML private Label agencyTitleLabel;
    @FXML private Label agencyVerifiedBadgeLabel;
    @FXML private Label agencyCountryLabel;
    @FXML private Label agencyAddressLabel;
    @FXML private Label agencyWebsiteLabel;
    @FXML private Label agencySinceLabel;
    @FXML private Label agencyAddressLineLabel;
    @FXML private Label agencyWebsiteLineLabel;
    @FXML private Label agencyCountryLineLabel;
    @FXML private Label agencyPhoneLabel;
    @FXML private Label agencyDescriptionLabel;
    @FXML private HBox descriptionRow;
    @FXML private HBox addressRow;
    @FXML private HBox websiteRow;
    @FXML private HBox phoneRow;
    @FXML private HBox countryRow;
    @FXML private Label feedbackLabel;

    @FXML private Button postsTabButton;
    @FXML private Button aboutTabButton;
    @FXML private Button offersTabButton;
    @FXML private Button addPostButton;
    @FXML private Button bookNowButton;
    @FXML private Label placeholderTitleLabel;
    @FXML private Label placeholderSubtitleLabel;
    @FXML private VBox agencyPageRoot;
    @FXML private VBox leftInfoCardRoot;
    @FXML private VBox rebuiltPostsPanel;
    @FXML private ScrollPane rebuiltPostsScrollPane;
    @FXML private VBox rebuiltPostsListBox;

    @FXML private TextField editAgencyNameField;
    @FXML private TextField editWebsiteField;
    @FXML private TextField editPhoneField;
    @FXML private TextField editAddressField;
    @FXML private TextField editCountryField;
    @FXML private TextArea editDescriptionField;
    @FXML private Label editAgencyNameErrorLabel;
    @FXML private Label editDescriptionErrorLabel;
    @FXML private Label editAddressErrorLabel;
    @FXML private Label editWebsiteErrorLabel;
    @FXML private Label editPhoneErrorLabel;
    @FXML private Label editCountryErrorLabel;
    @FXML private VBox editPanel;
    @FXML private Button editButton;
    @FXML private Button saveButton;
    @FXML private Button cancelEditButton;
    @FXML private Button editCoverButton;
    @FXML private Button editAvatarButton;
    @FXML private Button editNameFieldButton;
    @FXML private Button editDescriptionFieldButton;
    @FXML private Button editAddressFieldButton;
    @FXML private Button editWebsiteFieldButton;
    @FXML private Button editPhoneFieldButton;
    @FXML private Button editCountryFieldButton;
    @FXML private StackPane bannerStack;
    @FXML private ImageView coverImageView;
    @FXML private ImageView avatarImageView;
    @FXML private Label avatarFallbackLabel;
    @FXML private Pane signedInCosmicStarfieldPane;

    private final AgencyAccountService agencyService = new AgencyAccountService();
    private final AgencyPostService agencyPostService = new AgencyPostService();
    private final ImageAssetService imageAssetService = new ImageAssetService();
    private final DateTimeFormatter postDateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<String, Image> META_ICON_CACHE = new HashMap<>();
    private AgencyAccount currentAgency;
    private boolean canEditAgency;

    @FXML
    private void initialize() {
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            nav.showSignedInAgencies();
            return;
        }
        initSignedInSidebar();
        StarfieldHelper.populate(signedInCosmicStarfieldPane);
        loadAgencyForCurrentContext();
        configureImageNodes();
        showPostsTab();
        setEditMode(false);
        Platform.runLater(() -> {
            ensureLeftInfoCardClass();
        });
    }

    private void ensureLeftInfoCardClass() {
        if (leftInfoCardRoot != null && !leftInfoCardRoot.getStyleClass().contains("agency-profile-info-card")) {
            leftInfoCardRoot.getStyleClass().add("agency-profile-info-card");
        }
    }

    private void loadAgencyForCurrentContext() {
        Optional<Integer> userIdOpt = NavigationManager.getInstance().sessionUser().map(u -> u.getId());
        if (userIdOpt.isEmpty()) {
            NavigationManager.getInstance().showLogin();
            return;
        }
        int userId = userIdOpt.get();
        Long selectedAgencyId = NavigationManager.getInstance().selectedAgencyId().orElse(null);
        try {
            Optional<AgencyAccount> target = selectedAgencyId != null
                    ? agencyService.get(selectedAgencyId)
                    : agencyService.findByResponsableId(userId);

            if (target.isEmpty()) {
                if (selectedAgencyId != null) {
                    feedbackLabel.setText("Agency not found.");
                    NavigationManager.getInstance().showSignedInAgencies();
                } else {
                    feedbackLabel.setText("No agency found for this account.");
                    NavigationManager.getInstance().showAgencyProposal();
                }
                return;
            }

            currentAgency = target.get();
            boolean isOwnerAdmin = currentAgency.getResponsableId() != null
                    && currentAgency.getResponsableId().equals(userId)
                    && NavigationManager.getInstance().canAccessAgencyAdminFeatures();
            boolean isPlatformAdmin = NavigationManager.getInstance().canAccessAdminFeatures();
            canEditAgency = isOwnerAdmin || isPlatformAdmin;
            bindAgencyToView();
            applyEditVisibility();
            if (!canEditAgency) {
                feedbackLabel.setText("");
            } else {
                feedbackLabel.setText("");
            }
        } catch (SQLException e) {
            feedbackLabel.setText("Unable to load agency: " + e.getMessage());
        }
    }

    private void bindAgencyToView() {
        if (currentAgency == null) {
            return;
        }
        agencyTitleLabel.setText(safe(currentAgency.getAgencyName(), "My Agency"));
        boolean verified = Boolean.TRUE.equals(currentAgency.getVerified());
        agencyVerifiedBadgeLabel.setVisible(verified);
        agencyVerifiedBadgeLabel.setManaged(verified);
        editCoverButton.setText(currentAgency.getCoverImageId() != null ? "✎ Edit cover" : "+ Add cover");
        editAvatarButton.setText(currentAgency.getAgencyProfileImageId() != null ? "✎" : "+");
        bindIdentityMetaChips();
        bindFieldRow(descriptionRow, agencyDescriptionLabel, currentAgency.getDescription(), "", false);
        bindFieldRow(addressRow, agencyAddressLineLabel, currentAgency.getAddress(), "", false);
        bindFieldRow(websiteRow, agencyWebsiteLineLabel, currentAgency.getWebsiteUrl(), "", false);
        bindFieldRow(phoneRow, agencyPhoneLabel, currentAgency.getPhone(), "", false);
        bindFieldRow(countryRow, agencyCountryLineLabel, currentAgency.getCountry(), "", false);
        compactInfoRowsLayout();

        editAgencyNameField.setText(safe(currentAgency.getAgencyName(), ""));
        editWebsiteField.setText(safe(currentAgency.getWebsiteUrl(), ""));
        editPhoneField.setText(safe(currentAgency.getPhone(), ""));
        editAddressField.setText(safe(currentAgency.getAddress(), ""));
        editCountryField.setText(safe(currentAgency.getCountry(), ""));
        editDescriptionField.setText(safe(currentAgency.getDescription(), ""));
        clearEditFieldErrors();
        loadAgencyImages();
    }

    private void setEditMode(boolean enabled) {
        boolean showEditControls = canEditAgency && enabled;
        editPanel.setVisible(enabled && showEditControls);
        editPanel.setManaged(enabled && showEditControls);
        saveButton.setVisible(enabled && showEditControls);
        saveButton.setManaged(enabled && showEditControls);
        cancelEditButton.setVisible(enabled && showEditControls);
        cancelEditButton.setManaged(enabled && showEditControls);
        editButton.setVisible(!enabled && canEditAgency);
        editButton.setManaged(!enabled && canEditAgency);
    }

    private void applyEditVisibility() {
        List<Button> editControls = List.of(
                editCoverButton,
                editAvatarButton,
                editNameFieldButton,
                editDescriptionFieldButton,
                editAddressFieldButton,
                editWebsiteFieldButton,
                editPhoneFieldButton,
                editCountryFieldButton,
                editButton,
                addPostButton
        );
        for (Button b : editControls) {
            b.setVisible(canEditAgency);
            b.setManaged(canEditAgency);
        }
        syncAddPostButtonVisibility();
    }

    @FXML
    private void onEditAgency() {
        if (!canEditAgency) {
            feedbackLabel.setText("You are not allowed to edit this agency.");
            return;
        }
        setEditMode(true);
        showAboutTab();
    }

    @FXML
    private void onCancelEdit() {
        bindAgencyToView();
        setEditMode(false);
    }

    @FXML
    private void onSaveAgency() {
        feedbackLabel.setText("");
        clearEditFieldErrors();
        if (!canEditAgency) {
            feedbackLabel.setText("You are not allowed to update this agency.");
            return;
        }
        if (currentAgency == null) {
            feedbackLabel.setText("No agency loaded.");
            return;
        }
        AgencyAccount draft = new AgencyAccount();
        draft.setAgencyName(trim(editAgencyNameField.getText()));
        draft.setDescription(trim(editDescriptionField.getText()));
        draft.setWebsiteUrl(blankToNull(editWebsiteField.getText()));
        draft.setPhone(blankToNull(editPhoneField.getText()));
        draft.setAddress(blankToNull(editAddressField.getText()));
        String countryRaw = blankToNull(editCountryField.getText());
        draft.setCountry(countryRaw != null ? countryRaw.toUpperCase(Locale.ROOT) : null);
        agencyService.applyResolvedCountryIfMissing(draft);

        AgencyAccountValidationResult validation = agencyService.validateAgencyProfileFields(draft);
        if (!validation.isValid()) {
            applyEditFieldErrors(validation);
            feedbackLabel.setText("");
            return;
        }

        currentAgency.setAgencyName(draft.getAgencyName());
        currentAgency.setDescription(draft.getDescription());
        currentAgency.setPhone(draft.getPhone());
        currentAgency.setAddress(draft.getAddress());
        currentAgency.setCountry(draft.getCountry());
        currentAgency.setWebsiteUrl(normalizeWebsiteUrl(draft.getWebsiteUrl()));
        try {
            agencyService.update(currentAgency);
            bindAgencyToView();
            setEditMode(false);
            feedbackLabel.setText("Agency updated successfully.");
        } catch (SQLException | IllegalArgumentException e) {
            feedbackLabel.setText("Update failed: " + e.getMessage());
        }
    }

    private void clearEditFieldErrors() {
        setFieldError(editAgencyNameErrorLabel, null);
        setFieldError(editDescriptionErrorLabel, null);
        setFieldError(editAddressErrorLabel, null);
        setFieldError(editWebsiteErrorLabel, null);
        setFieldError(editPhoneErrorLabel, null);
        setFieldError(editCountryErrorLabel, null);
    }

    private void applyEditFieldErrors(AgencyAccountValidationResult r) {
        setFieldError(editAgencyNameErrorLabel, r.getError(AgencyAccountValidationResult.FIELD_AGENCY_NAME));
        setFieldError(editDescriptionErrorLabel, r.getError(AgencyAccountValidationResult.FIELD_DESCRIPTION));
        setFieldError(editAddressErrorLabel, r.getError(AgencyAccountValidationResult.FIELD_ADDRESS));
        setFieldError(editWebsiteErrorLabel, r.getError(AgencyAccountValidationResult.FIELD_WEBSITE_URL));
        setFieldError(editPhoneErrorLabel, r.getError(AgencyAccountValidationResult.FIELD_PHONE));
        setFieldError(editCountryErrorLabel, r.getError(AgencyAccountValidationResult.FIELD_COUNTRY));
    }

    private static void setFieldError(Label label, String message) {
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

    /** Adds https:// when missing so stored URLs are consistent after validation. */
    private static String normalizeWebsiteUrl(String urlOrNull) {
        if (urlOrNull == null || urlOrNull.isBlank()) {
            return null;
        }
        String s = urlOrNull.trim();
        if (s.matches("(?i)^https?://.*")) {
            return s;
        }
        return "https://" + s;
    }

    private void bindIdentityMetaChips() {
        if (currentAgency == null) {
            return;
        }
        setChipWithIcon(agencyAddressLabel, "/images/meta/location.png", currentAgency.getAddress());
        setChipWithIcon(agencyWebsiteLabel, "/images/meta/type.png", "Travel Agency");
        setChipWithIcon(agencyCountryLabel, "/images/meta/country.png", currentAgency.getCountry());
        String since = null;
        if (currentAgency.getCreatedAt() != null) {
            since = "Since " + currentAgency.getCreatedAt().getYear();
        }
        setChipWithIcon(agencySinceLabel, "/images/meta/since.png", since);
    }

    private static void setChipWithIcon(Label label, String iconResourcePath, String value) {
        if (label == null) {
            return;
        }
        String v = value == null ? "" : value.trim();
        boolean show = !v.isBlank();
        label.setText(show ? v : "");
        label.setGraphic(show ? buildChipIcon(iconResourcePath) : null);
        label.setVisible(show);
        label.setManaged(show);
    }

    private static ImageView buildChipIcon(String iconResourcePath) {
        if (iconResourcePath == null || iconResourcePath.isBlank()) {
            return null;
        }
        Image icon = META_ICON_CACHE.computeIfAbsent(iconResourcePath, path -> {
            URL url = MyAgencyController.class.getResource(path);
            return url == null ? null : new Image(url.toExternalForm(), 13, 13, true, true);
        });
        if (icon == null) {
            return null;
        }
        ImageView view = new ImageView(icon);
        view.setFitWidth(13);
        view.setFitHeight(13);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private static String hostOnly(String websiteUrl) {
        if (websiteUrl == null || websiteUrl.isBlank()) {
            return null;
        }
        String s = websiteUrl.trim().replaceFirst("(?i)^https?://", "");
        int slash = s.indexOf('/');
        return slash >= 0 ? s.substring(0, slash) : s;
    }

    @FXML
    private void showPostsTab() {
        setActiveTab(postsTabButton);
        syncAddPostButtonVisibility();
        placeholderTitleLabel.setText("Posts");
        placeholderSubtitleLabel.setText("Share updates, offers, and travel moments from your agency.");
        renderPostsTab();
        animateTabSwitch();
    }

    @FXML
    private void showAboutTab() {
        setActiveTab(aboutTabButton);
        syncAddPostButtonVisibility();
        placeholderTitleLabel.setText("About");
        placeholderSubtitleLabel.setText("Agency details and profile highlights.");
        renderAboutTab();
        animateTabSwitch();
    }

    @FXML
    private void showOffersTab() {
        setActiveTab(offersTabButton);
        syncAddPostButtonVisibility();
        placeholderTitleLabel.setText("Offers");
        placeholderSubtitleLabel.setText("Approved offers and promotions.");
        renderOffersTab();
        animateTabSwitch();
    }

    private void animateTabSwitch() {
        if (rebuiltPostsPanel == null) {
            return;
        }
        rebuiltPostsPanel.setOpacity(0.90);
        FadeTransition fade = new FadeTransition(Duration.millis(170), rebuiltPostsPanel);
        fade.setFromValue(0.90);
        fade.setToValue(1.0);
        fade.play();
    }

    private void renderPostsTab() {
        if (rebuiltPostsListBox == null || rebuiltPostsScrollPane == null) {
            return;
        }
        rebuiltPostsScrollPane.setVisible(true);
        rebuiltPostsScrollPane.setManaged(true);
        rebuiltPostsListBox.getChildren().clear();
        if (currentAgency == null || currentAgency.getId() == null) {
            rebuiltPostsListBox.getChildren().add(buildPlaceholder("No agency selected."));
            return;
        }
        Integer viewerId = NavigationManager.getInstance().sessionUser().map(u -> u.getId()).orElse(null);
        String viewerName = NavigationManager.getInstance().sessionUser().map(u -> u.getUsername()).orElse("You");
        Long viewerProfileImageId = NavigationManager.getInstance().sessionUser().map(u -> u.getProfileImageId()).orElse(null);
        try {
            List<AgencyPost> posts = agencyPostService.listByAgency(currentAgency.getId(), viewerId);
            if (posts.isEmpty()) {
                rebuiltPostsListBox.getChildren().add(buildPlaceholder("No agency posts yet."));
                return;
            }
            VBox feedCard = new VBox(12);
            feedCard.getStyleClass().addAll("agency-post-card", "agency-post-feed-card");
            for (AgencyPost post : posts) {
                VBox postItem = buildPostCard(post, viewerId, viewerName, viewerProfileImageId);
                postItem.getStyleClass().remove("agency-post-card");
                postItem.getStyleClass().add("agency-post-feed-item");
                feedCard.getChildren().add(postItem);
            }
            rebuiltPostsListBox.getChildren().add(feedCard);
        } catch (SQLException e) {
            rebuiltPostsListBox.getChildren().add(buildPlaceholder("Cannot load agency posts: " + e.getMessage()));
        }
    }

    private void renderAboutTab() {
        if (rebuiltPostsListBox == null || rebuiltPostsScrollPane == null) {
            return;
        }
        rebuiltPostsScrollPane.setVisible(true);
        rebuiltPostsScrollPane.setManaged(true);
        rebuiltPostsListBox.getChildren().clear();
        String description = currentAgency == null ? null : currentAgency.getDescription();
        String address = currentAgency == null ? null : currentAgency.getAddress();
        String website = currentAgency == null ? null : currentAgency.getWebsiteUrl();
        String phone = currentAgency == null ? null : currentAgency.getPhone();
        String country = currentAgency == null ? null : currentAgency.getCountry();
        String aboutText = "About this agency\n\n"
                + "Description: " + safe(description, "Not provided yet") + "\n"
                + "Address: " + safe(address, "Not provided yet") + "\n"
                + "Website: " + safe(website, "Not provided yet") + "\n"
                + "Phone: " + safe(phone, "Not provided yet") + "\n"
                + "Country: " + safe(country, "Not provided yet");
        rebuiltPostsListBox.getChildren().add(buildPlaceholder(aboutText));
    }

    private void renderOffersTab() {
        if (rebuiltPostsListBox == null || rebuiltPostsScrollPane == null) {
            return;
        }
        rebuiltPostsScrollPane.setVisible(true);
        rebuiltPostsScrollPane.setManaged(true);
        rebuiltPostsListBox.getChildren().clear();
        rebuiltPostsListBox.getChildren().add(buildPlaceholder("No approved offers published yet."));
    }

    private VBox buildPostCard(AgencyPost post, Integer viewerId, String viewerName, Long viewerProfileImageId) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("user-profile-card", "agency-post-card");

        HBox header = new HBox(8);
        header.getStyleClass().add("agency-post-header-row");
        StackPane avatarShell = new StackPane();
        avatarShell.getStyleClass().add("agency-post-avatar-shell");
        if (avatarImageView != null && avatarImageView.getImage() != null) {
            ImageView agencyAvatar = new ImageView(avatarImageView.getImage());
            agencyAvatar.setFitWidth(48);
            agencyAvatar.setFitHeight(48);
            agencyAvatar.setPreserveRatio(false);
            Circle clip = new Circle(24, 24, 24);
            agencyAvatar.setClip(clip);
            avatarShell.getChildren().add(agencyAvatar);
        } else {
            Label fallback = new Label("A");
            fallback.getStyleClass().add("agency-post-avatar-fallback");
            avatarShell.getChildren().add(fallback);
        }

        Label author = new Label(safe(currentAgency != null ? currentAgency.getAgencyName() : post.getAuthorUsername(), "Agency"));
        author.getStyleClass().add("agency-post-author");
        Label date = new Label(formatRelative(post.getCreatedAt()));
        date.getStyleClass().add("agency-post-date");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(avatarShell, author, spacer, date);

        Label title = new Label(safe(post.getTitle(), "Post"));
        title.getStyleClass().add("agency-post-title");
        title.setWrapText(true);

        Label content = new Label(safe(post.getContent(), ""));
        content.getStyleClass().add("agency-post-content");
        content.setWrapText(true);
        content.setMaxWidth(760);

        card.getChildren().addAll(header, title, content);

        if (!post.getImageAssetIds().isEmpty()) {
            StackPane media = buildPostMedia(post.getImageAssetIds());
            if (media != null) {
                card.getChildren().add(media);
            }
        } else if (coverImageView != null && coverImageView.getImage() != null) {
            StackPane media = buildAgencyCoverMedia();
            if (media != null) {
                card.getChildren().add(media);
            }
        }

        HBox actions = new HBox(10);
        actions.getStyleClass().add("agency-post-actions");
        Button likeButton = new Button(likeButtonText(post.isLikedByViewer(), post.getLikesCount()));
        likeButton.getStyleClass().add("agency-post-action-button");
        Button commentsToggleButton = new Button("💬 " + post.getCommentsCount());
        commentsToggleButton.getStyleClass().add("agency-post-action-button");
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        Button shareButton = new Button("Share");
        shareButton.getStyleClass().add("agency-post-send-button");
        actions.getChildren().addAll(likeButton, commentsToggleButton, actionSpacer, shareButton);
        card.getChildren().add(actions);

        VBox commentsBox = new VBox(8);
        commentsBox.getStyleClass().add("agency-post-comments-box");
        commentsBox.setVisible(false);
        commentsBox.setManaged(false);

        VBox commentsList = new VBox(6);
        commentsList.getStyleClass().add("agency-post-comments-list");
        for (AgencyPostComment comment : post.getComments()) {
            commentsList.getChildren().add(buildCommentNode(comment));
        }
        ScrollPane commentsScroll = new ScrollPane(commentsList);
        commentsScroll.setFitToWidth(true);
        commentsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        commentsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        commentsScroll.setPrefViewportHeight(112);
        commentsScroll.setMaxHeight(112);
        commentsScroll.getStyleClass().add("agency-comments-scroll");
        commentsBox.getChildren().add(commentsScroll);

        if (viewerId != null) {
            HBox addCommentRow = new HBox(8);
            addCommentRow.getStyleClass().add("agency-comment-input-row");
            TextField commentField = new TextField();
            commentField.setPromptText("Write a comment...");
            commentField.getStyleClass().add("agency-comment-input");
            HBox.setHgrow(commentField, Priority.ALWAYS);
            Button sendButton = new Button("Send");
            sendButton.getStyleClass().addAll("agency-post-send-button", "agency-comment-send-button");
            sendButton.setOnAction(e -> {
                String typed = trim(commentField.getText());
                if (typed.isBlank()) {
                    return;
                }
                try {
                    AgencyPostComment created = agencyPostService.addComment(post.getId(), viewerId, typed);
                    created.setAuthorUsername(viewerName);
                    created.setAuthorProfileImageId(viewerProfileImageId);
                    post.getComments().add(created);
                    post.setCommentsCount(post.getCommentsCount() + 1);
                    commentsList.getChildren().add(buildCommentNode(created));
                    commentsToggleButton.setText("💬 " + post.getCommentsCount());
                    commentField.clear();
                } catch (SQLException | IllegalArgumentException ex) {
                    feedbackLabel.setText("Comment failed: " + ex.getMessage());
                }
            });
            addCommentRow.getChildren().addAll(commentField, sendButton);
            commentsBox.getChildren().add(addCommentRow);
        }

        commentsToggleButton.setOnAction(e -> {
            boolean show = !commentsBox.isVisible();
            commentsBox.setVisible(show);
            commentsBox.setManaged(show);
        });

        if (viewerId == null) {
            likeButton.setDisable(true);
        } else {
            likeButton.setOnAction(e -> {
                try {
                    AgencyPostService.LikeResult result = agencyPostService.toggleLike(post.getId(), viewerId);
                    post.setLikedByViewer(result.liked());
                    post.setLikesCount(result.likesCount());
                    likeButton.setText(likeButtonText(post.isLikedByViewer(), post.getLikesCount()));
                } catch (SQLException ex) {
                    feedbackLabel.setText("Like action failed: " + ex.getMessage());
                }
            });
        }

        card.getChildren().add(commentsBox);
        return card;
    }

    private VBox buildPlaceholder(String text) {
        VBox box = new VBox();
        box.getStyleClass().addAll("user-profile-card", "agency-post-card");
        Label label = new Label(text);
        label.getStyleClass().add("agency-post-content");
        label.setWrapText(true);
        box.getChildren().add(label);
        return box;
    }

    private VBox buildCommentNode(AgencyPostComment comment) {
        VBox node = new VBox();
        node.getStyleClass().add("agency-post-comment");
        VBox body = new VBox(2);
        body.getStyleClass().add("agency-post-comment-body");
        String who = safe(comment.getAuthorUsername(), "User");
        String when = formatRelative(comment.getCreatedAt());
        Label meta = new Label(who + " • " + when);
        meta.getStyleClass().add("agency-post-comment-meta");
        Label content = new Label(safe(comment.getContent(), ""));
        content.getStyleClass().add("agency-post-comment-content");
        content.setWrapText(true);
        body.getChildren().addAll(meta, content);

        StackPane avatarShell = new StackPane();
        avatarShell.getStyleClass().add("agency-post-comment-avatar-shell");
        Image avatar = loadImage(comment.getAuthorProfileImageId());
        if (avatar != null) {
            ImageView avatarView = new ImageView(avatar);
            avatarView.setFitWidth(34);
            avatarView.setFitHeight(34);
            avatarView.setPreserveRatio(false);
            avatarView.setClip(new Circle(17, 17, 17));
            avatarShell.getChildren().add(avatarView);
        } else {
            Label fallback = new Label(who.substring(0, 1).toUpperCase());
            fallback.getStyleClass().add("agency-post-comment-avatar-fallback");
            avatarShell.getChildren().add(fallback);
        }
        HBox row = new HBox(8, avatarShell, body);
        node.getChildren().add(row);
        return node;
    }

    private Image loadImage(Long imageId) {
        if (imageId == null) {
            return null;
        }
        try {
            Optional<ImageAsset> image = imageAssetService.get(imageId);
            if (image.isPresent() && image.get().getData() != null && image.get().getData().length > 0) {
                Image loaded = new Image(new ByteArrayInputStream(image.get().getData()));
                return loaded.isError() ? null : loaded;
            }
        } catch (SQLException ignored) {
            // Ignore and fallback to initials.
        }
        return null;
    }

    private StackPane buildPostMedia(List<Long> imageAssetIds) {
        List<Image> images = new ArrayList<>();
        for (Long id : imageAssetIds) {
            try {
                Optional<ImageAsset> image = imageAssetService.get(id);
                if (image.isPresent() && image.get().getData() != null && image.get().getData().length > 0) {
                    images.add(new Image(new ByteArrayInputStream(image.get().getData())));
                }
            } catch (SQLException ignored) {
                // Keep carousel usable even if one asset fails.
            }
        }
        if (images.isEmpty()) {
            return null;
        }

        ImageView imageView = new ImageView(images.get(0));
        imageView.setPreserveRatio(true);
        imageView.setFitHeight(260);
        imageView.setSmooth(true);
        imageView.getStyleClass().add("agency-post-image");

        StackPane root = new StackPane(imageView);
        root.getStyleClass().add("agency-post-carousel");
        if (images.size() == 1) {
            return root;
        }

        int[] idx = {0};
        HBox dots = new HBox(6);
        dots.getStyleClass().add("agency-post-carousel-dots");
        List<Button> dotButtons = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            final int dotIndex = i;
            Button dot = new Button();
            dot.getStyleClass().add("agency-post-carousel-dot");
            dot.setOnAction(e -> {
                idx[0] = dotIndex;
                imageView.setImage(images.get(idx[0]));
                for (int j = 0; j < dotButtons.size(); j++) {
                    dotButtons.get(j).getStyleClass().remove("agency-post-carousel-dot-active");
                    if (j == idx[0]) {
                        dotButtons.get(j).getStyleClass().add("agency-post-carousel-dot-active");
                    }
                }
            });
            dotButtons.add(dot);
            dots.getChildren().add(dot);
        }
        dotButtons.get(0).getStyleClass().add("agency-post-carousel-dot-active");

        Button prev = new Button("◀");
        prev.getStyleClass().add("agency-post-carousel-nav");
        prev.setOnAction(e -> {
            idx[0] = (idx[0] - 1 + images.size()) % images.size();
            imageView.setImage(images.get(idx[0]));
            for (int j = 0; j < dotButtons.size(); j++) {
                dotButtons.get(j).getStyleClass().remove("agency-post-carousel-dot-active");
                if (j == idx[0]) {
                    dotButtons.get(j).getStyleClass().add("agency-post-carousel-dot-active");
                }
            }
        });

        Button next = new Button("▶");
        next.getStyleClass().add("agency-post-carousel-nav");
        next.setOnAction(e -> {
            idx[0] = (idx[0] + 1) % images.size();
            imageView.setImage(images.get(idx[0]));
            for (int j = 0; j < dotButtons.size(); j++) {
                dotButtons.get(j).getStyleClass().remove("agency-post-carousel-dot-active");
                if (j == idx[0]) {
                    dotButtons.get(j).getStyleClass().add("agency-post-carousel-dot-active");
                }
            }
        });

        BorderPane controls = new BorderPane();
        controls.getStyleClass().add("agency-post-carousel-controls");
        controls.setLeft(prev);
        controls.setRight(next);
        controls.setBottom(dots);
        BorderPane.setAlignment(dots, javafx.geometry.Pos.BOTTOM_CENTER);

        prev.setOpacity(0);
        next.setOpacity(0);
        root.setOnMouseEntered(e -> {
            prev.setOpacity(1);
            next.setOpacity(1);
        });
        root.setOnMouseExited(e -> {
            prev.setOpacity(0);
            next.setOpacity(0);
        });

        root.getChildren().add(controls);
        return root;
    }

    /**
     * Fallback media for posts without images: current agency cover.
     */
    private StackPane buildAgencyCoverMedia() {
        if (coverImageView == null || coverImageView.getImage() == null) {
            return null;
        }
        ImageView imageView = new ImageView(coverImageView.getImage());
        imageView.setPreserveRatio(true);
        imageView.setFitHeight(260);
        imageView.setSmooth(true);
        imageView.getStyleClass().add("agency-post-image");

        StackPane root = new StackPane(imageView);
        root.getStyleClass().add("agency-post-carousel");
        return root;
    }

    private String formatRelative(LocalDateTime ts) {
        if (ts == null) {
            return "now";
        }
        long seconds = java.time.Duration.between(ts, LocalDateTime.now()).getSeconds();
        if (seconds < 60) {
            return "just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        if (days < 7) {
            return days + "d ago";
        }
        return postDateTimeFmt.format(ts);
    }

    private String likeButtonText(boolean liked, int count) {
        return (liked ? "♥ " : "♡ ") + count;
    }

    private void setActiveTab(Button active) {
        while (postsTabButton.getStyleClass().remove("agency-text-tab-active")) { }
        while (aboutTabButton.getStyleClass().remove("agency-text-tab-active")) { }
        while (offersTabButton.getStyleClass().remove("agency-text-tab-active")) { }
        if (!active.getStyleClass().contains("agency-text-tab-active")) {
            active.getStyleClass().add("agency-text-tab-active");
        }
    }

    private void syncAddPostButtonVisibility() {
        if (addPostButton == null) {
            return;
        }
        boolean postsActive = postsTabButton != null
                && postsTabButton.getStyleClass().contains("agency-text-tab-active");
        boolean show = canEditAgency && postsActive;
        addPostButton.setVisible(show);
        addPostButton.setManaged(show);
    }

    private void configureImageNodes() {
        if (bannerStack != null && coverImageView != null) {
            coverImageView.fitWidthProperty().bind(bannerStack.widthProperty());
            coverImageView.fitHeightProperty().bind(bannerStack.heightProperty());
            Rectangle imageClip = new Rectangle();
            imageClip.widthProperty().bind(bannerStack.widthProperty());
            imageClip.heightProperty().bind(bannerStack.heightProperty());
            imageClip.setArcWidth(48);
            imageClip.setArcHeight(48);
            coverImageView.setClip(imageClip);
        }
        if (avatarImageView != null) {
            double radius = avatarImageView.getFitWidth() / 2.0;
            Circle clip = new Circle(radius, radius, radius);
            avatarImageView.setClip(clip);
        }
    }

    private void loadAgencyImages() {
        if (currentAgency == null || currentAgency.getId() == null) {
            return;
        }
        loadCoverImage();
        loadAvatarImage();
    }

    private void loadCoverImage() {
        try {
            Optional<ImageAsset> cover = agencyService.loadCoverImage(currentAgency.getId());
            if (cover.isPresent() && cover.get().getData() != null && cover.get().getData().length > 0) {
                Image image = new Image(new ByteArrayInputStream(cover.get().getData()));
                coverImageView.setImage(image);
                return;
            }
        } catch (SQLException ignored) {
            // fall through to fallback image
        }
        coverImageView.setImage(loadBundledOrRemoteImage(FALLBACK_BANNER_RESOURCE, FALLBACK_BANNER_REMOTE));
    }

    private void loadAvatarImage() {
        try {
            Optional<ImageAsset> avatar = agencyService.loadAgencyProfileImage(currentAgency.getId());
            if (avatar.isPresent() && avatar.get().getData() != null && avatar.get().getData().length > 0) {
                Image image = new Image(new ByteArrayInputStream(avatar.get().getData()));
                avatarImageView.setImage(image);
                avatarFallbackLabel.setVisible(false);
                avatarFallbackLabel.setManaged(false);
                return;
            }
        } catch (SQLException ignored) {
            // fall through to fallback label
        }
        Image fallbackImage = loadBundledOrRemoteImage(FALLBACK_LOGO_RESOURCE, FALLBACK_LOGO_REMOTE);
        if (fallbackImage != null && !fallbackImage.isError()) {
            avatarImageView.setImage(fallbackImage);
            avatarFallbackLabel.setVisible(false);
            avatarFallbackLabel.setManaged(false);
            return;
        }
        avatarImageView.setImage(null);
        if (avatarFallbackLabel != null) {
            String name = trim(agencyTitleLabel != null ? agencyTitleLabel.getText() : null);
            avatarFallbackLabel.setText(name.isBlank() ? "A" : name.substring(0, 1).toUpperCase());
            avatarFallbackLabel.setVisible(true);
            avatarFallbackLabel.setManaged(true);
        }
    }

    private static Image loadBundledOrRemoteImage(String classpathResource, String remoteUrl) {
        URL u = MyAgencyController.class.getResource(classpathResource);
        if (u != null) {
            return new Image(u.toExternalForm(), true);
        }
        return new Image(remoteUrl, true);
    }

    @FXML private void onEditAvatar() { uploadAgencyImage(false); }
    @FXML private void onEditNameField() { setEditMode(true); editAgencyNameField.requestFocus(); }
    @FXML private void onEditDescriptionField() { setEditMode(true); editDescriptionField.requestFocus(); }
    @FXML private void onEditAddressField() { setEditMode(true); editAddressField.requestFocus(); }
    @FXML private void onEditWebsiteField() { setEditMode(true); editWebsiteField.requestFocus(); }
    @FXML private void onEditPhoneField() { setEditMode(true); editPhoneField.requestFocus(); }
    @FXML private void onEditCountryField() { setEditMode(true); editCountryField.requestFocus(); }

    @FXML private void onBackToAgencies() { NavigationManager.getInstance().showSignedInAgencies(); }

    @FXML
    private void onAddPost() {
        if (!canEditAgency || currentAgency == null || currentAgency.getId() == null) {
            feedbackLabel.setText("You are not allowed to add posts for this agency.");
            return;
        }
        NavigationManager.getInstance().showAgencyPostCreate();
    }

    private String safe(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private String blankToNull(String v) {
        String t = trim(v);
        return t.isBlank() ? null : t;
    }

    private void bindFieldRow(HBox row, Label label, String value, String title, boolean showWhenBlank) {
        if (row == null || label == null) {
            return;
        }
        String safeTitle = title == null ? "" : title.trim();
        String normalized = value == null ? "" : value.trim();
        boolean hasValue = !normalized.isBlank();
        row.setVisible(showWhenBlank || hasValue);
        row.setManaged(showWhenBlank || hasValue);
        String display = hasValue ? normalized : "Not provided yet";
        label.setText(safeTitle.isBlank() ? display : (safeTitle + ": " + display));
    }

    private void compactInfoRowsLayout() {
        compactRowsAndSeparators(descriptionRow);
        compactRowsAndSeparators(addressRow, websiteRow, phoneRow, countryRow);
    }

    private void compactRowsAndSeparators(HBox... rows) {
        if (rows == null || rows.length == 0) {
            return;
        }
        Parent parent = rows[0].getParent();
        if (!(parent instanceof VBox box)) {
            return;
        }
        List<Node> children = box.getChildren();
        for (Node n : children) {
            if (n instanceof Separator s) {
                s.setVisible(false);
                s.setManaged(false);
            }
        }
        HBox previousVisibleRow = null;
        for (HBox row : rows) {
            if (row == null || !children.contains(row) || !row.isManaged()) {
                continue;
            }
            clearFieldMiniCardStyle(row);
            if (previousVisibleRow != null) {
                int prevIdx = children.indexOf(previousVisibleRow);
                int currentIdx = children.indexOf(row);
                for (int i = prevIdx + 1; i < currentIdx; i++) {
                    Node between = children.get(i);
                    if (between instanceof Separator s) {
                        s.setVisible(true);
                        s.setManaged(true);
                        break;
                    }
                }
            }
            previousVisibleRow = row;
        }
    }

    private static void clearFieldMiniCardStyle(HBox row) {
        row.setStyle("");
    }

    private void showInfoToast(String text) {
        Notifications.create()
                .title("Agency Profile")
                .text(text)
                .hideAfter(Duration.seconds(2.1))
                .showInformation();
    }

    @FXML
    private void onEditCover() {
        uploadAgencyImage(true);
    }

    private void uploadAgencyImage(boolean cover) {
        if (!canEditAgency) {
            feedbackLabel.setText("You are not allowed to update images for this agency.");
            return;
        }
        if (currentAgency == null || currentAgency.getId() == null) {
            feedbackLabel.setText("No agency loaded.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(cover ? "Choose cover image" : "Choose profile image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp")
        );
        File file = chooser.showOpenDialog(editCoverButton.getScene() != null ? editCoverButton.getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String mime = detectMime(file);
            if (mime == null) {
                feedbackLabel.setText("Unsupported image format. Use PNG/JPG/GIF/WEBP.");
                return;
            }
            if (cover) {
                agencyService.replaceCoverImage(currentAgency.getId(), bytes, mime);
            } else {
                agencyService.replaceAgencyProfileImage(currentAgency.getId(), bytes, mime);
            }
            reloadCurrentAgency();
            if (cover) {
                refreshPostsUiAfterAgencyCoverChanged();
            } else {
                refreshPostsUiAfterAgencyAvatarChanged();
            }
            feedbackLabel.setText(cover ? "Cover image updated." : "Profile image updated.");
            showInfoToast(cover ? "Cover image updated." : "Profile image updated.");
        } catch (IOException e) {
            feedbackLabel.setText("Cannot read selected file: " + e.getMessage());
        } catch (SQLException | IllegalArgumentException e) {
            feedbackLabel.setText("Image update failed: " + e.getMessage());
        }
    }

    private void reloadCurrentAgency() throws SQLException {
        if (currentAgency == null || currentAgency.getId() == null) {
            return;
        }
        Optional<AgencyAccount> refreshed = agencyService.get(currentAgency.getId());
        if (refreshed.isPresent()) {
            currentAgency = refreshed.get();
            bindAgencyToView();
        }
    }

    /**
     * Post cards use the agency cover as fallback media when a post has no images.
     */
    private void refreshPostsUiAfterAgencyCoverChanged() {
        refreshPostsTabIfPostsPaneVisible();
    }

    /**
     * Post headers clone the main agency avatar {@link ImageView} image; rebuild cards so avatars match.
     */
    private void refreshPostsUiAfterAgencyAvatarChanged() {
        refreshPostsTabIfPostsPaneVisible();
    }

    private void refreshPostsTabIfPostsPaneVisible() {
        if (rebuiltPostsScrollPane != null && rebuiltPostsScrollPane.isVisible()) {
            renderPostsTab();
        }
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
