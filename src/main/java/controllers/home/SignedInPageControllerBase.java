package controllers.home;

import auth.AuthSession;
import enums.gestionutilisateurs.UserRole;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import models.gestionagences.ImageAsset;
import models.gestionutilisateurs.User;
import services.gestionutilisateurs.UserService;
import utils.AvatarImageCover;
import utils.NavigationManager;
import utils.SignedInNavSection;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Shared signed-in sidebar fields and navigation, injected into each page controller (sidebar is inlined in FXML).
 */
public abstract class SignedInPageControllerBase {

    /** Inner avatar square inside the 46px sidebar ring; clip + cover scaling use this size. */
    private static final double SIDEBAR_AVATAR_BOX = 42;

    /** Matches {@code signed-in-unified.css} signed-in sidebar active row. */
    private static final String ACTIVE = "sidebar-nav-item-active";
    private static final String COMPACT = "sidebar-compact";
    private static final String TIGHT = "sidebar-tight";
    private static final double SIDEBAR_COMPACT_HEIGHT_THRESHOLD = 1100.0;
    private static final double SIDEBAR_COMPACT_WIDTH_THRESHOLD = 1366.0;
    private static final double SIDEBAR_TIGHT_WIDTH_THRESHOLD = 1180.0;
    private static final double SIDEBAR_WIDE_MIN = 280.0;
    private static final double SIDEBAR_WIDE_PREF = 312.0;
    private static final double SIDEBAR_WIDE_MAX = 348.0;
    private static final double SIDEBAR_MID_MIN = 266.0;
    private static final double SIDEBAR_MID_PREF = 294.0;
    private static final double SIDEBAR_MID_MAX = 322.0;
    private static final double SIDEBAR_NARROW_MIN = 248.0;
    private static final double SIDEBAR_NARROW_PREF = 272.0;
    private static final double SIDEBAR_NARROW_MAX = 296.0;
    private static final double SIDEBAR_TIGHT_MIN = 226.0;
    private static final double SIDEBAR_TIGHT_PREF = 244.0;
    private static final double SIDEBAR_TIGHT_MAX = 260.0;
    private static final double SIDEBAR_ULTRA_MIN = 202.0;
    private static final double SIDEBAR_ULTRA_PREF = 216.0;
    private static final double SIDEBAR_ULTRA_MAX = 230.0;

    @FXML
    protected Label signedInSidebarSessionLabel;
    @FXML
    protected Button navHomeButton;
    @FXML
    protected Button navOffersButton;
    @FXML
    protected Button navAgenciesButton;
    @FXML
    protected Button navMessagesButton;
    @FXML
    protected Button navRecommendationsButton;
    @FXML
    protected Button navEventsButton;
    @FXML
    protected Button navPremiumButton;
    @FXML
    protected Button navNotificationsButton;
    @FXML
    protected Button navProfileButton;
    @FXML
    protected Button navDashboardButton;
    @FXML
    protected StackPane signedInSidebarHost;
    @FXML
    protected VBox signedInSidebarShell;
    @FXML
    protected Region signedInSidebarEdgeAura;
    @FXML
    protected Region signedInBrandUnderlineBar;
    @FXML
    protected StackPane signedInSidebarAvatarClipHost;
    @FXML
    protected ImageView signedInSidebarAvatarImage;
    @FXML
    protected Label signedInSidebarAvatarFallback;
    @FXML
    protected Label signedInSidebarEmailLabel;
    @FXML
    protected Label signedInSidebarRoleLabel;
    @FXML
    protected Button signedInLogoutIconButton;

    private Timeline signedInAuraPulseTimeline;
    private Timeline signedInUnderlineTimeline;
    private final UserService sidebarUserService = new UserService();
    private boolean compactSidebarListenersAttached;

    /** Call from each subclass {@code initialize()} when this page includes the signed-in sidebar. */
    protected final void initSignedInSidebar() {
        if (navHomeButton == null) {
            return;
        }
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            return;
        }
        User user = AuthSession.getCurrentUser().orElse(null);
        if (signedInSidebarSessionLabel != null) {
            boolean agencyAdmin = nav.canAccessAgencyAdminFeatures();
            signedInSidebarSessionLabel.setText(agencyAdmin ? "Signed in · Agency Admin" : "Signed in · Traveler");
        }
        installCompactSidebarMode();
        ensureFooterIdentityLabels();
        populateSidebarIdentity(user, nav.canAccessAgencyAdminFeatures());
        if (navPremiumButton != null) {
            navPremiumButton.setVisible(false);
            navPremiumButton.setManaged(false);
        }
        if (navDashboardButton != null) {
            boolean showDash = canAccessAdminOrAgencyDashboard(user);
            navDashboardButton.setVisible(showDash);
            navDashboardButton.setManaged(showDash);
        }
        applySidebarIcons();
        startSignedInSidebarHeaderMotion();
        applyActiveSection(NavigationManager.getInstance().consumeSignedInSidebarSection());
        Platform.runLater(this::startSignedInSidebarHeaderMotion);
    }

    /** Reload sidebar email/role/avatar from the current session user (e.g. after profile photo change). */
    protected final void refreshSignedInSidebarIdentity() {
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            return;
        }
        User user = AuthSession.getCurrentUser().orElse(null);
        populateSidebarIdentity(user, nav.canAccessAgencyAdminFeatures());
    }

    private void applyActiveSection(SignedInNavSection section) {
        clearNavActive();
        Button target = switch (section) {
            case HOME -> navHomeButton;
            case OFFERS -> navOffersButton;
            case AGENCIES -> navAgenciesButton;
            case MESSAGES -> navMessagesButton;
            case RECOMMENDATIONS -> navRecommendationsButton;
            case EVENTS -> navEventsButton;
            case PROFILE -> navProfileButton;
            default -> null;
        };
        if (target != null && !target.getStyleClass().contains(ACTIVE)) {
            target.getStyleClass().add(ACTIVE);
        }
    }

    private void installCompactSidebarMode() {
        if (signedInSidebarHost == null || signedInSidebarShell == null || compactSidebarListenersAttached) {
            return;
        }
        signedInSidebarHost.heightProperty().addListener((o, oldH, newH) -> applyCompactSidebarMode());
        signedInSidebarHost.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.widthProperty().addListener((o, oldW, newW) -> applyCompactSidebarMode());
                newScene.heightProperty().addListener((o, oldH, newH) -> applyCompactSidebarMode());
            }
            applyCompactSidebarMode();
        });
        compactSidebarListenersAttached = true;
        applyCompactSidebarMode();
        Platform.runLater(this::applyCompactSidebarMode);
    }

    private void applyCompactSidebarMode() {
        if (signedInSidebarShell == null) {
            return;
        }
        double sceneWidth = signedInSidebarHost != null && signedInSidebarHost.getScene() != null
                ? signedInSidebarHost.getScene().getWidth()
                : -1;
        double height = -1;
        if (signedInSidebarHost != null && signedInSidebarHost.getHeight() > 0) {
            height = signedInSidebarHost.getHeight();
        } else if (signedInSidebarHost != null && signedInSidebarHost.getScene() != null) {
            height = signedInSidebarHost.getScene().getHeight();
        }
        boolean compactByHeight = height > 0 && height <= SIDEBAR_COMPACT_HEIGHT_THRESHOLD;
        boolean compactByWidth = sceneWidth > 0 && sceneWidth <= SIDEBAR_COMPACT_WIDTH_THRESHOLD;
        boolean compact = compactByHeight || compactByWidth;
        boolean tight = sceneWidth > 0 && sceneWidth <= SIDEBAR_TIGHT_WIDTH_THRESHOLD;
        applyResponsiveSidebarWidth(compact, tight, sceneWidth);
        applyResponsiveSidebarHeights(compact, tight);
        if (compact) {
            if (!signedInSidebarShell.getStyleClass().contains(COMPACT)) {
                signedInSidebarShell.getStyleClass().add(COMPACT);
            }
        } else {
            signedInSidebarShell.getStyleClass().remove(COMPACT);
        }
        if (tight) {
            if (!signedInSidebarShell.getStyleClass().contains(TIGHT)) {
                signedInSidebarShell.getStyleClass().add(TIGHT);
            }
        } else {
            signedInSidebarShell.getStyleClass().remove(TIGHT);
        }
    }

    private void applyResponsiveSidebarWidth(boolean compact, boolean tight, double sceneWidth) {
        if (signedInSidebarHost == null) {
            return;
        }
        double min = SIDEBAR_WIDE_MIN;
        double pref = SIDEBAR_WIDE_PREF;
        double max = SIDEBAR_WIDE_MAX;
        if (sceneWidth > 0 && sceneWidth <= 980) {
            min = SIDEBAR_ULTRA_MIN;
            pref = SIDEBAR_ULTRA_PREF;
            max = SIDEBAR_ULTRA_MAX;
        } else if (sceneWidth > 0 && sceneWidth <= 1180) {
            min = SIDEBAR_TIGHT_MIN;
            pref = SIDEBAR_TIGHT_PREF;
            max = SIDEBAR_TIGHT_MAX;
        } else if (sceneWidth > 0 && sceneWidth <= 1280) {
            min = SIDEBAR_NARROW_MIN;
            pref = SIDEBAR_NARROW_PREF;
            max = SIDEBAR_NARROW_MAX;
        } else if (sceneWidth > 0 && sceneWidth <= 1536) {
            min = SIDEBAR_MID_MIN;
            pref = SIDEBAR_MID_PREF;
            max = SIDEBAR_MID_MAX;
        }
        if (compact && !tight) {
            min = Math.max(216.0, min - 20.0);
            pref = Math.max(236.0, pref - 18.0);
            max = Math.max(260.0, max - 16.0);
        }
        signedInSidebarHost.setMinWidth(min);
        signedInSidebarHost.setPrefWidth(pref);
        signedInSidebarHost.setMaxWidth(max);
    }

    private void applyResponsiveSidebarHeights(boolean compact, boolean tight) {
        if (signedInSidebarShell != null) {
            signedInSidebarShell.setSpacing(compact ? 5.0 : 8.0);
        }
        if (signedInSidebarAvatarClipHost != null) {
            double size = tight ? 34.0 : (compact ? 36.0 : SIDEBAR_AVATAR_BOX);
            signedInSidebarAvatarClipHost.setMinWidth(size);
            signedInSidebarAvatarClipHost.setPrefWidth(size);
            signedInSidebarAvatarClipHost.setMaxWidth(size);
            signedInSidebarAvatarClipHost.setMinHeight(size);
            signedInSidebarAvatarClipHost.setPrefHeight(size);
            signedInSidebarAvatarClipHost.setMaxHeight(size);
            signedInSidebarAvatarClipHost.setClip(new Circle(size / 2, size / 2, size / 2));
        }
    }

    private void clearNavActive() {
        for (Button b : List.of(
                navHomeButton, navOffersButton, navAgenciesButton, navMessagesButton,
                navRecommendationsButton, navEventsButton, navNotificationsButton,
                navProfileButton, navDashboardButton)) {
            if (b != null) {
                b.getStyleClass().remove(ACTIVE);
                b.getStyleClass().remove("sv-si-nav-active");
            }
        }
    }

    private static boolean canAccessAdminOrAgencyDashboard(User user) {
        if (user == null) {
            return false;
        }
        List<String> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream().anyMatch(r ->
                UserRole.ADMIN.getValue().equals(r) || UserRole.AGENCY_ADMIN.getValue().equals(r));
    }

    @FXML
    protected void onHome() {
        NavigationManager.getInstance().showSignedInHome();
    }

    @FXML
    protected void onOffres() {
        NavigationManager.getInstance().showSignedInOffers();
    }

    @FXML
    protected void onAgences() {
        NavigationManager.getInstance().showSignedInAgencies();
    }

    @FXML
    protected void onMessagerie() {
        NavigationManager.getInstance().showSignedInMessages();
    }

    @FXML
    protected void onRecommandation() {
        NavigationManager.getInstance().showSignedInPosts();
    }

    @FXML
    protected void onEvenement() {
        NavigationManager.getInstance().showSignedInEvents();
    }

    @FXML
    protected void onPremium() {
        showPlaceholder("Premium", "Premium (signed-in) will open here.");
    }

    @FXML
    protected void onNotifications() {
        showPlaceholder("Notifications", "Notifications (signed-in) will open here.");
    }

    @FXML
    protected void onProfile() {
        NavigationManager.getInstance().showUserProfile();
    }

    @FXML
    protected void onDashboardIa() {
        User user = AuthSession.getCurrentUser().orElse(null);
        if (user == null) {
            return;
        }
        if (canAccessAdminOrAgencyDashboard(user)) {
            NavigationManager.getInstance().showAdminDashboard();
            return;
        }
        showPlaceholder("Access denied", "Dashboard IA is only available for agency admins and platform admins.");
    }

    @FXML
    protected void onThemeToggle() {
        NavigationManager.getInstance().toggleTheme();
    }

    @FXML
    protected void onLogout() {
        NavigationManager.getInstance().logoutToGuest();
    }

    private void showPlaceholder(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void populateSidebarIdentity(User user, boolean agencyAdmin) {
        if (signedInSidebarEmailLabel != null) {
            String displayName = user != null ? user.getEmail() : null;
            if (displayName == null || displayName.isBlank()) {
                displayName = user != null ? user.getUsername() : "";
            }
            signedInSidebarEmailLabel.setText(displayName == null ? "" : displayName);
        }
        if (signedInSidebarRoleLabel != null) {
            String role = agencyAdmin ? "Agency Admin" : "Traveler";
            if (user != null && user.getRoles() != null && user.getRoles().stream().anyMatch(r -> UserRole.ADMIN.getValue().equals(r))) {
                role = "Platform Admin";
            }
            signedInSidebarRoleLabel.setText(role);
        }
        ensureSidebarAvatarClipHost();
        if (signedInSidebarAvatarImage != null) {
            signedInSidebarAvatarImage.setImage(null);
            signedInSidebarAvatarImage.setClip(null);
            AvatarImageCover.resetToSquare(signedInSidebarAvatarImage, sidebarAvatarSize());
        }
        if (signedInSidebarAvatarFallback != null) {
            signedInSidebarAvatarFallback.setText(initialsFromUser(user));
            signedInSidebarAvatarFallback.setVisible(true);
            signedInSidebarAvatarFallback.setManaged(true);
        }
        if (user == null || user.getId() == null || signedInSidebarAvatarImage == null) {
            return;
        }
        try {
            var imageOpt = sidebarUserService.loadProfileImage(user.getId());
            if (imageOpt.isPresent()) {
                ImageAsset asset = imageOpt.get();
                if (asset.getData() != null && asset.getData().length > 0) {
                    Image image = new Image(new ByteArrayInputStream(asset.getData()));
                    signedInSidebarAvatarImage.setImage(image);
                    AvatarImageCover.apply(signedInSidebarAvatarImage, image, sidebarAvatarSize());
                    if (signedInSidebarAvatarFallback != null) {
                        signedInSidebarAvatarFallback.setVisible(false);
                        signedInSidebarAvatarFallback.setManaged(false);
                    }
                }
            }
        } catch (SQLException ignored) {
            // Keep initials fallback when profile image fails to load.
        }
    }

    private void ensureFooterIdentityLabels() {
        if (signedInLogoutIconButton == null) {
            return;
        }
        Parent parent = signedInLogoutIconButton.getParent();
        if (!(parent instanceof HBox footerRow)) {
            return;
        }

        if (signedInSidebarEmailLabel == null) {
            signedInSidebarEmailLabel = new Label();
            signedInSidebarEmailLabel.getStyleClass().add("signed-in-sidebar-email");
            signedInSidebarEmailLabel.setTextOverrun(OverrunStyle.CLIP);
        }
        if (signedInSidebarRoleLabel == null) {
            signedInSidebarRoleLabel = new Label();
            signedInSidebarRoleLabel.getStyleClass().add("signed-in-sidebar-role-pill");
            signedInSidebarRoleLabel.setTextOverrun(OverrunStyle.CLIP);
        }
        if (!signedInSidebarEmailLabel.getStyleClass().contains("signed-in-footer-email")) {
            signedInSidebarEmailLabel.getStyleClass().add("signed-in-footer-email");
        }
        if (!signedInSidebarRoleLabel.getStyleClass().contains("signed-in-footer-role")) {
            signedInSidebarRoleLabel.getStyleClass().add("signed-in-footer-role");
        }

        if (signedInSidebarAvatarClipHost == null) {
            signedInSidebarAvatarClipHost = new StackPane();
            signedInSidebarAvatarClipHost.getStyleClass().addAll("signed-in-sidebar-avatar-clip", "signed-in-footer-avatar-shell");
            signedInSidebarAvatarClipHost.setMinSize(48, 48);
            signedInSidebarAvatarClipHost.setPrefSize(48, 48);
            signedInSidebarAvatarClipHost.setMaxSize(48, 48);
        }
        if (signedInSidebarAvatarImage == null) {
            signedInSidebarAvatarImage = new ImageView();
            signedInSidebarAvatarImage.setPreserveRatio(true);
            signedInSidebarAvatarImage.setSmooth(true);
            signedInSidebarAvatarImage.getStyleClass().add("signed-in-footer-avatar-image");
        }
        if (signedInSidebarAvatarFallback == null) {
            signedInSidebarAvatarFallback = new Label("SV");
            signedInSidebarAvatarFallback.getStyleClass().addAll("signed-in-sidebar-avatar-fallback", "signed-in-footer-avatar-fallback");
        }
        if (!signedInSidebarAvatarClipHost.getChildren().contains(signedInSidebarAvatarFallback)) {
            signedInSidebarAvatarClipHost.getChildren().add(signedInSidebarAvatarFallback);
        }
        if (!signedInSidebarAvatarClipHost.getChildren().contains(signedInSidebarAvatarImage)) {
            signedInSidebarAvatarClipHost.getChildren().add(signedInSidebarAvatarImage);
        }

        Node existingRow = null;
        for (Node child : footerRow.getChildren()) {
            if (child instanceof HBox h && h.getStyleClass().contains("signed-in-footer-identity")) {
                existingRow = h;
                break;
            }
        }

        HBox identityRow;
        if (existingRow instanceof HBox existingBox) {
            identityRow = existingBox;
            VBox textCol = null;
            for (Node child : identityRow.getChildren()) {
                if (child instanceof VBox v && v.getStyleClass().contains("signed-in-sidebar-user-row")) {
                    textCol = v;
                    break;
                }
            }
            if (textCol == null) {
                textCol = new VBox(1.0);
                textCol.setAlignment(Pos.CENTER_LEFT);
                textCol.getStyleClass().add("signed-in-sidebar-user-row");
                identityRow.getChildren().add(textCol);
            }
            if (!identityRow.getChildren().contains(signedInSidebarAvatarClipHost)) {
                identityRow.getChildren().add(0, signedInSidebarAvatarClipHost);
            }
            if (!textCol.getChildren().contains(signedInSidebarEmailLabel)) {
                textCol.getChildren().add(0, signedInSidebarEmailLabel);
            }
            if (!textCol.getChildren().contains(signedInSidebarRoleLabel)) {
                textCol.getChildren().add(signedInSidebarRoleLabel);
            }
        } else {
            identityRow = new HBox(8.0);
            identityRow.setAlignment(Pos.CENTER_LEFT);
            identityRow.getStyleClass().add("signed-in-footer-identity");
            VBox textCol = new VBox(1.0);
            textCol.setAlignment(Pos.CENTER_LEFT);
            textCol.getStyleClass().add("signed-in-sidebar-user-row");
            textCol.getChildren().addAll(signedInSidebarEmailLabel, signedInSidebarRoleLabel);
            HBox.setHgrow(textCol, Priority.ALWAYS);
            identityRow.getChildren().addAll(signedInSidebarAvatarClipHost, textCol);
            int logoutIndex = footerRow.getChildren().indexOf(signedInLogoutIconButton);
            int insertIndex = Math.max(0, logoutIndex);
            footerRow.getChildren().add(insertIndex, identityRow);
        }
        HBox.setHgrow(identityRow, Priority.ALWAYS);
    }

    private void ensureSidebarAvatarClipHost() {
        if (signedInSidebarAvatarClipHost == null || signedInSidebarAvatarClipHost.getClip() != null) {
            return;
        }
        double r = sidebarAvatarSize() / 2;
        signedInSidebarAvatarClipHost.setClip(new Circle(r, r, r));
    }

    private double sidebarAvatarSize() {
        if (signedInSidebarAvatarClipHost != null && signedInSidebarAvatarClipHost.getPrefWidth() > 0) {
            return signedInSidebarAvatarClipHost.getPrefWidth();
        }
        return SIDEBAR_AVATAR_BOX;
    }

    private static String initialsFromUser(User user) {
        if (user == null) {
            return "SV";
        }
        String username = user.getUsername();
        if (username != null && !username.isBlank()) {
            String trimmed = username.trim();
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2) {
                return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
            }
            if (trimmed.length() >= 2) {
                return trimmed.substring(0, 2).toUpperCase();
            }
            return trimmed.substring(0, 1).toUpperCase();
        }
        String email = user.getEmail();
        if (email != null && !email.isBlank()) {
            return email.substring(0, 1).toUpperCase();
        }
        return "SV";
    }

    private void applySidebarIcons() {
        Map<Button, String> iconMap = Map.of(
                navHomeButton, "/images/nav/nav-home.png",
                navOffersButton, "/images/nav/nav-offers.png",
                navAgenciesButton, "/images/nav/nav-agencies.png",
                navMessagesButton, "/images/nav/nav-messages.png",
                navRecommendationsButton, "/images/nav/nav-recommendations.png",
                navEventsButton, "/images/nav/nav-events.png",
                navPremiumButton, "/images/nav/nav-premium-star.png",
                navNotificationsButton, "/images/nav/nav-notifications.png",
                navProfileButton, "/images/nav/nav-profile.png",
                navDashboardButton, "/images/nav/nav-dashboard.png"
        );
        for (var entry : iconMap.entrySet()) {
            Button button = entry.getKey();
            if (button == null || button.getGraphic() != null) {
                continue;
            }
            ImageView icon = createSidebarIcon(entry.getValue());
            if (icon != null) {
                button.setGraphic(icon);
            }
        }
        if (signedInLogoutIconButton != null && signedInLogoutIconButton.getGraphic() == null) {
            ImageView logoutIcon = createSidebarIcon("/images/nav/nav-logout.png");
            if (logoutIcon != null) {
                signedInLogoutIconButton.setGraphic(logoutIcon);
            }
        }
    }

    private ImageView createSidebarIcon(String path) {
        try {
            var url = SignedInPageControllerBase.class.getResource(path);
            if (url == null) {
                return null;
            }
            ImageView iv = new ImageView(new Image(url.toExternalForm(), true));
            iv.setFitWidth(28);
            iv.setFitHeight(28);
            iv.setPreserveRatio(true);
            iv.getStyleClass().add("sidebar-nav-icon-image");
            return iv;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Mirrors welcome sidebar header animation (edge aura + accent bar breathing). */
    private void startSignedInSidebarHeaderMotion() {
        if (signedInAuraPulseTimeline != null) {
            signedInAuraPulseTimeline.stop();
            signedInAuraPulseTimeline = null;
        }
        if (signedInUnderlineTimeline != null) {
            signedInUnderlineTimeline.stop();
            signedInUnderlineTimeline = null;
        }
        if (signedInSidebarEdgeAura != null) {
            signedInSidebarEdgeAura.toFront();
            signedInSidebarEdgeAura.setOpacity(0.26);
            signedInAuraPulseTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(signedInSidebarEdgeAura.opacityProperty(), 0.20)),
                    new KeyFrame(Duration.seconds(1.4), new KeyValue(signedInSidebarEdgeAura.opacityProperty(), 0.62, Interpolator.EASE_BOTH))
            );
            signedInAuraPulseTimeline.setAutoReverse(true);
            signedInAuraPulseTimeline.setCycleCount(Animation.INDEFINITE);
            signedInAuraPulseTimeline.play();
        }

        if (signedInBrandUnderlineBar != null) {
            signedInBrandUnderlineBar.setScaleX(0.35);
            signedInBrandUnderlineBar.setOpacity(0.35);
            signedInUnderlineTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(signedInBrandUnderlineBar.scaleXProperty(), 0.35),
                            new KeyValue(signedInBrandUnderlineBar.opacityProperty(), 0.35)),
                    new KeyFrame(Duration.seconds(1.2),
                            new KeyValue(signedInBrandUnderlineBar.scaleXProperty(), 1.18, Interpolator.EASE_BOTH),
                            new KeyValue(signedInBrandUnderlineBar.opacityProperty(), 1.0, Interpolator.EASE_BOTH))
            );
            signedInUnderlineTimeline.setAutoReverse(true);
            signedInUnderlineTimeline.setCycleCount(Animation.INDEFINITE);
            signedInUnderlineTimeline.play();
        }

        if (signedInSidebarShell != null && signedInSidebarEdgeAura != null) {
            signedInSidebarShell.setOnMouseEntered(e -> signedInSidebarEdgeAura.getStyleClass().add("smart-auth-edge-aura-hot"));
            signedInSidebarShell.setOnMouseExited(e -> signedInSidebarEdgeAura.getStyleClass().remove("smart-auth-edge-aura-hot"));
        }
    }
}
