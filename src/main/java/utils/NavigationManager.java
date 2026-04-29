package utils;

import auth.AuthSession;
import enums.gestionutilisateurs.UserRole;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import models.gestionposts.Post;
import models.gestionutilisateurs.User;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Global scene navigation. Signed-in flows mirror Pi-java2 {@code NavigationManager} API but are backed by
 * {@link AuthSession} instead of an internal session object.
 *
 * <p><b>Signed-in stylesheets:</b> when the scene root has {@code signed-in-clean-shell}, the scene loads only
 * {@code signed-in-unified.css} (built from the clean shell, Pi-ported excerpts, and Pi-java2 excerpts — no
 * separate {@code pi-ported-theme.css} on the scene). Optional {@code admin-dashboard.css} may remain for admin.
 * Guest/welcome/auth flows use {@code styles.css} + {@code pi-ported-theme.css} only (no unified).
 */
public class NavigationManager {

    private static NavigationManager instance;
    private static final String AGENCY_PROPOSAL_FXML = "/fxml/agency/agency_proposal.fxml";

    private Stage stage;
    private Scene sharedScene;
    private boolean lightTheme;
    private Long selectedAgencyId;
    private Post selectedPost;
    /** Passé à l’écran détail ; consommé une fois au chargement (défaut : true). */
    private boolean postDetailAllowOwnerEditDelete = true;
    private String currentResourcePath;
    /** Consumed once by {@link controllers.home.SignedInPageControllerBase#initSignedInSidebar()} to mark the active row. */
    private SignedInNavSection pendingSignedInSidebarSection = SignedInNavSection.NONE;
    /** One-shot flag consumed by SignedInHomeController for post-login brand transition. */
    private boolean pendingSignedInEntryTransition;
    /** One-shot conversation selection for signed-in messages page. */
    private Long pendingConversationId;

    private NavigationManager() {
    }

    public static NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }

    public static final double DESKTOP_STAGE_WIDTH = 1600;
    public static final double DESKTOP_STAGE_HEIGHT = 900;

    public void configure(Stage primaryStage) {
        this.stage = primaryStage;
        this.stage.setMinWidth(900);
        this.stage.setMinHeight(620);
        this.stage.setFullScreen(false);
    }

    public void showWelcome() {
        loadScene("/fxml/welcome/welcome.fxml", "SmartVoyage Desktop");
    }

    /** Pi-java2 alias: post-login home shell. */
    public void showPostLoginHome() {
        showSignedInHome();
    }

    /** Pi-java2 alias: signed-in shell FXML. */
    public void showSignedInShell() {
        showSignedInHome();
    }

    public void showSignedInHome() {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        pendingSignedInEntryTransition = !isSignedInResource(currentResourcePath);
        loadScene("/fxml/home/signed-in-home.fxml", "SmartVoyage — Home");
    }

    public void showAdminDashboard() {
        loadScene("/fxml/admin/admin-dashboard.fxml", "Connect Sales — Admin");
    }

    public void showLogin() {
        showWelcome();
    }

    /** Guest offers page may reference sign-up; desktop shell uses welcome auth dock. */
    public void showRegister() {
        showWelcome();
    }

    public void showGuestFeedbacks() {
        showWelcome();
    }

    public void showGuestCrew() {
        showWelcome();
    }

    public Optional<User> sessionUser() {
        return AuthSession.getCurrentUser();
    }

    public void setSessionUser(User user) {
        AuthSession.setCurrentUser(user);
    }

    public void logoutToGuest() {
        AuthSession.clear();
        selectedAgencyId = null;
        showWelcome();
    }

    public Optional<Long> selectedAgencyId() {
        return Optional.ofNullable(selectedAgencyId);
    }

    public void setSelectedAgencyId(Long agencyId) {
        this.selectedAgencyId = agencyId;
    }

    public boolean canAccessSignedInShell() {
        Optional<User> current = sessionUser();
        if (current.isEmpty()) {
            return false;
        }
        User u = current.get();
        Boolean active = u.getIsActive();
        if (active != null && !active) {
            return false;
        }
        return hasRole(u, UserRole.USER.getValue())
                || hasRole(u, UserRole.AGENCY_ADMIN.getValue())
                || hasRole(u, UserRole.ADMIN.getValue());
    }

    public boolean canAccessAgencyAdminFeatures() {
        return sessionUser().map(this::userHasAgencyAdmin).orElse(false);
    }

    public boolean canAccessAdminFeatures() {
        return sessionUser().map(u -> hasRole(u, UserRole.ADMIN.getValue())).orElse(false);
    }

    private boolean userHasAgencyAdmin(User u) {
        return hasRole(u, UserRole.AGENCY_ADMIN.getValue());
    }

    private boolean hasRole(User user, String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String target = role.trim().toUpperCase(Locale.ROOT);
        List<String> roles = user.getRoles();
        if (roles != null) {
            for (String r : roles) {
                if (r != null && r.trim().toUpperCase(Locale.ROOT).equals(target)) {
                    return true;
                }
            }
        }
        String single = user.getRole();
        return single != null && single.trim().toUpperCase(Locale.ROOT).equals(target);
    }

    public void showSignedInAgencies() {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        loadScene("/fxml/agency/agencies_signed_in.fxml", "SmartVoyage — Agencies");
    }

    public void showSignedInEvents() {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        loadScene("/fxml/user/events_signed_in.fxml", "SmartVoyage — Events");
    }

    public void showSignedInOffers() {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        loadScene("/fxml/gestionoffres/Offers.fxml", "SmartVoyage — Offers");
    }

    public void showSignedInPosts() {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        loadSceneWithPostsCss("/fxml/posts/posts_view.fxml", "SmartVoyage — Recommendations");
    }

    public void showSignedInMessages() {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        loadScene("/fxml/user/messages_signed_in.fxml", "SmartVoyage — Messages");
    }

    public void showUserProfile() {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        loadScene("/fxml/user/user_profile.fxml", "SmartVoyage — Profile");
    }

    public void showAgencyProfile(Long agencyId) {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        this.selectedAgencyId = agencyId;
        loadScene("/fxml/agency/my_agency.fxml", "SmartVoyage — Agency");
    }

    public void showAgencyProposal() {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        loadScene("/fxml/agency/agency_proposal.fxml", "SmartVoyage — Agency proposal");
    }

    public void showMyAgency() {
        if (!canAccessAgencyAdminFeatures()) {
            showSignedInAgencies();
            return;
        }
        selectedAgencyId = null;
        loadScene("/fxml/agency/my_agency.fxml", "SmartVoyage — My agency");
    }

    public void showAgencyPostCreate() {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        loadScene("/fxml/agency/agency_post_create.fxml", "SmartVoyage — New post");
    }

    public Optional<Post> selectedPost() {
        return Optional.ofNullable(selectedPost);
    }

    public Post getSelectedPost() {
        return selectedPost;
    }

    public void setSelectedPost(Post post) {
        this.selectedPost = post;
    }

    public void showPostDetail(Post post) {
        showPostDetail(post, true);
    }

    /**
     * @param allowOwnerEditDelete si false (ex. ouverture depuis « Tous les posts »), masque Modifier/Supprimer le post
     *                             sur l’écran détail même pour l’auteur.
     */
    public void showPostDetail(Post post, boolean allowOwnerEditDelete) {
        if (!canAccessSignedInShell()) {
            AuthSession.clear();
            showWelcome();
            return;
        }
        this.postDetailAllowOwnerEditDelete = allowOwnerEditDelete;
        this.selectedPost = post;
        loadSceneWithPostsCss("/fxml/posts/post_detail.fxml", "SmartVoyage — Post detail");
    }

    /** À appeler une fois au chargement du détail ; réinitialise le drapeau pour la navigation suivante. */
    public boolean consumePostDetailAllowOwnerEditDelete() {
        boolean v = postDetailAllowOwnerEditDelete;
        postDetailAllowOwnerEditDelete = true;
        return v;
    }

    public void toggleTheme() {
        if (sharedScene == null || sharedScene.getRoot() == null) {
            return;
        }
        Parent root = sharedScene.getRoot();
        /*
         * Do NOT fade the entire scene root: FadeTransition sets Node.opacity on the root, which applies a
         * uniform semi-transparent "veil" over the whole signed-in shell (sidebar + cosmic + text).
         * Interrupted/double toggles can leave opacity < 1.0 and match the reported pale wash.
         */
        root.setOpacity(1.0);
        lightTheme = !lightTheme;
        applyThemeClass(root);
    }

    private void applyThemeClass(Parent root) {
        if (root == null) {
            return;
        }
        if (lightTheme) {
            if (!root.getStyleClass().contains("theme-light")) {
                root.getStyleClass().add("theme-light");
            }
        } else {
            root.getStyleClass().remove("theme-light");
        }
    }

    /**
     * Sidebar fragment reads this once per scene load to apply {@code sidebar-nav-item-active}.
     */
    public SignedInNavSection consumeSignedInSidebarSection() {
        SignedInNavSection s = pendingSignedInSidebarSection;
        pendingSignedInSidebarSection = SignedInNavSection.NONE;
        return s;
    }

    /** Signed-in home reads this once to decide whether to play the login transition overlay. */
    public boolean consumeSignedInEntryTransition() {
        boolean show = pendingSignedInEntryTransition;
        pendingSignedInEntryTransition = false;
        return show;
    }

    public void setPendingConversationId(Long conversationId) {
        this.pendingConversationId = conversationId;
    }

    public Long consumePendingConversationId() {
        Long value = pendingConversationId;
        pendingConversationId = null;
        return value;
    }

    private static boolean isSignedInResource(String resource) {
        if (resource == null || resource.isBlank()) {
            return false;
        }
        return "/fxml/home/signed-in-home.fxml".equals(resource)
                || "/fxml/user/offers_signed_in.fxml".equals(resource)
                || "/fxml/gestionoffres/Offers.fxml".equals(resource)
                || "/fxml/gestionoffres/OfferForm.fxml".equals(resource)
                || "/fxml/gestionoffres/ReservationForm.fxml".equals(resource)
                || "/fxml/gestionoffres/MyReservations.fxml".equals(resource)
                || "/fxml/gestionoffres/AgencyReservations.fxml".equals(resource)
                || "/fxml/gestionoffres/OfferReservations.fxml".equals(resource)
                || "/fxml/agency/agencies_signed_in.fxml".equals(resource)
                || "/fxml/agency/my_agency.fxml".equals(resource)
                || "/fxml/agency/agency_proposal.fxml".equals(resource)
                || "/fxml/agency/agency_post_create.fxml".equals(resource)
                || "/fxml/user/user_profile.fxml".equals(resource)
                || "/fxml/user/events_signed_in.fxml".equals(resource)
                || "/fxml/user/messages_signed_in.fxml".equals(resource)
                || "/fxml/posts/posts_view.fxml".equals(resource)
                || "/fxml/posts/post_detail.fxml".equals(resource);
    }

    private void prepareSignedInSidebarSection(String resource) {
        if (!canAccessSignedInShell()) {
            pendingSignedInSidebarSection = SignedInNavSection.NONE;
            return;
        }
        if ("/fxml/home/signed-in-home.fxml".equals(resource)) {
            pendingSignedInSidebarSection = SignedInNavSection.HOME;
        } else if ("/fxml/user/offers_signed_in.fxml".equals(resource)
                || "/fxml/gestionoffres/Offers.fxml".equals(resource)) {
            pendingSignedInSidebarSection = SignedInNavSection.OFFERS;
        } else if ("/fxml/agency/agencies_signed_in.fxml".equals(resource)
                || "/fxml/agency/my_agency.fxml".equals(resource)
                || "/fxml/agency/agency_proposal.fxml".equals(resource)
                || "/fxml/agency/agency_post_create.fxml".equals(resource)) {
            pendingSignedInSidebarSection = SignedInNavSection.AGENCIES;
        } else if ("/fxml/user/user_profile.fxml".equals(resource)) {
            pendingSignedInSidebarSection = SignedInNavSection.PROFILE;
        } else if ("/fxml/user/events_signed_in.fxml".equals(resource)) {
            pendingSignedInSidebarSection = SignedInNavSection.EVENTS;
        } else if ("/fxml/user/messages_signed_in.fxml".equals(resource)) {
            pendingSignedInSidebarSection = SignedInNavSection.MESSAGES;
        } else if ("/fxml/posts/posts_view.fxml".equals(resource)
                || "/fxml/posts/post_detail.fxml".equals(resource)) {
            pendingSignedInSidebarSection = SignedInNavSection.RECOMMENDATIONS;
        } else {
            pendingSignedInSidebarSection = SignedInNavSection.NONE;
        }
    }

    private void loadSceneWithPostsCss(String resource, String title) {
        try {
            prepareSignedInSidebarSection(resource);
            URL fxmlUrl = Objects.requireNonNull(NavigationManager.class.getResource(resource));
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            currentResourcePath = resource;
            applyScene(root, title);
            ensurePostsStylesheet();
        } catch (IOException e) {
            throw new RuntimeException("Unable to load scene: " + resource, e);
        }
    }

    private void loadScene(String resource, String title) {
        try {
            prepareSignedInSidebarSection(resource);
            URL fxmlUrl = Objects.requireNonNull(NavigationManager.class.getResource(resource));
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            currentResourcePath = resource;
            applyScene(root, title);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load scene: " + resource, e);
        }
    }

    private void applyScene(Parent root, String title) {
        if (sharedScene == null) {
            sharedScene = new Scene(root);
            URL css = NavigationManager.class.getResource("/css/styles.css");
            if (css != null) {
                sharedScene.getStylesheets().add(css.toExternalForm());
            }
            stage.setScene(sharedScene);
        } else {
            sharedScene.setRoot(root);
        }
        /* Default Scene fill is white; any transparent region on the root shows through as a global wash. */
        sharedScene.setFill(Color.web("#05010D"));
        root.setOpacity(1.0);
        stage.setOpacity(1.0);
        applyThemeClass(root);
        syncSignedInStylesheetPolicy(root);
        stage.setTitle(title);
        stage.setFullScreen(false);
        if (!stage.isShowing()) {
            stage.show();
        }
        if (!stage.isMaximized()) {
            Platform.runLater(() -> {
                if (!stage.isMaximized()) {
                    stage.setMaximized(true);
                }
            });
        }
    }

    private void ensurePiPortedThemeStylesheet() {
        if (sharedScene == null) {
            return;
        }
        URL pi = NavigationManager.class.getResource("/css/pi-ported-theme.css");
        if (pi == null) {
            return;
        }
        String ext = pi.toExternalForm();
        if (!sharedScene.getStylesheets().contains(ext)) {
            sharedScene.getStylesheets().add(ext);
        }
    }

    private void ensurePostsStylesheet() {
        if (sharedScene == null) {
            return;
        }
        URL posts = NavigationManager.class.getResource("/css/posts_styles.css");
        if (posts == null) {
            return;
        }
        String ext = posts.toExternalForm();
        if (!sharedScene.getStylesheets().contains(ext)) {
            sharedScene.getStylesheets().add(ext);
        }
    }

    private static String stylesheetUrl(String classpath) {
        URL u = NavigationManager.class.getResource(classpath);
        return u == null ? null : u.toExternalForm();
    }

    /**
     * Signed-in routes ({@code signed-in-clean-shell} on the root) load both {@code styles.css} (welcome typography)
     * and {@code signed-in-unified.css} (signed-in skin), with unified loaded last so signed-in overrides still win.
     * Guest/welcome restores base + Pi theme in a stable order. Non–signed-in routes do not load unified.
     */
    private void syncSignedInStylesheetPolicy(Parent root) {
        if (sharedScene == null) {
            return;
        }
        String base = stylesheetUrl("/css/styles.css");
        String pi = stylesheetUrl("/css/pi-ported-theme.css");
        String unified = stylesheetUrl("/css/signed-in-unified.css");
        String admin = stylesheetUrl("/css/admin-dashboard.css");
        String legacyCleanShell = stylesheetUrl("/css/signed-in-clean-shell.css");
        String legacySidebarClean = stylesheetUrl("/css/signed-in-sidebar-clean.css");
        var sheets = sharedScene.getStylesheets();
        if (legacyCleanShell != null) {
            sheets.remove(legacyCleanShell);
        }
        if (legacySidebarClean != null) {
            sheets.remove(legacySidebarClean);
        }
        boolean signedInUnified = AGENCY_PROPOSAL_FXML.equals(currentResourcePath) || isSignedInUnifiedRoute(root);
        boolean isAdminRoute = root != null && hasStyleClass(root, "admin-dashboard-root");
        if (signedInUnified) {
            if (pi != null) {
                sheets.remove(pi);
            }
            if (admin != null && !isAdminRoute) {
                sheets.remove(admin);
            }
            sheets.clear();
            if (base != null) {
                sheets.add(base);
            }
            if (unified != null) {
                sheets.add(unified);
            }
            removeForbiddenSubtreeStylesheets(root);
        } else {
            if (unified != null) {
                sheets.remove(unified);
            }
            ensureAdminDashboardStylesheet(root);
            if (base != null) {
                sheets.remove(base);
                sheets.add(0, base);
            }
            ensurePiPortedThemeStylesheet();
        }
    }

    private void ensureAdminDashboardStylesheet(Parent root) {
        if (sharedScene == null) {
            return;
        }
        URL adminCss = NavigationManager.class.getResource("/css/admin-dashboard.css");
        if (adminCss == null) {
            return;
        }
        String ext = adminCss.toExternalForm();
        boolean isAdminRoute = root != null && hasStyleClass(root, "admin-dashboard-root");
        var sheets = sharedScene.getStylesheets();
        if (isAdminRoute) {
            if (!sheets.contains(ext)) {
                sheets.add(ext);
            }
            return;
        }
        sheets.remove(ext);
    }

    private static boolean isSignedInUnifiedRoute(Parent root) {
        if (root == null) {
            return false;
        }
        if (hasStyleClass(root, "signed-in-clean-shell") || hasStyleClass(root, "signed-in-shell-root")) {
            return true;
        }
        return root.lookup(".signed-in-clean-shell") != null || root.lookup(".signed-in-shell-root") != null;
    }

    private static boolean hasStyleClass(Node node, String className) {
        if (node == null || className == null || className.isBlank()) {
            return false;
        }
        for (String styleClass : node.getStyleClass()) {
            if (className.equals(styleClass)) {
                return true;
            }
            if (styleClass != null && !styleClass.isBlank()) {
                for (String token : styleClass.trim().split("\\s+")) {
                    if (className.equals(token)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void removeForbiddenSubtreeStylesheets(Parent root) {
        if (root == null) {
            return;
        }
        Set<String> forbiddenNames = new LinkedHashSet<>();
        forbiddenNames.add("styles.css");
        forbiddenNames.add("pi-ported-theme.css");
        forbiddenNames.add("admin-dashboard.css");
        forbiddenNames.add("agency-proposal.css");
        forbiddenNames.add("agency_proposal.css");
        forbiddenNames.add("proposal.css");
        forbiddenNames.add("signed-in-clean-shell.css");
        forbiddenNames.add("signed-in-sidebar-clean.css");
        stripSubtreeStylesheets(root, forbiddenNames);
    }

    private static void stripSubtreeStylesheets(Parent parent, Set<String> forbiddenNames) {
        parent.getStylesheets().removeIf(sheet -> matchesAnyStylesheetName(sheet, forbiddenNames));
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Parent childParent) {
                stripSubtreeStylesheets(childParent, forbiddenNames);
            }
        }
    }

    private static boolean matchesAnyStylesheetName(String stylesheetUrl, Set<String> names) {
        if (stylesheetUrl == null) {
            return false;
        }
        for (String name : names) {
            if (stylesheetUrl.endsWith("/" + name) || stylesheetUrl.endsWith("\\" + name) || stylesheetUrl.contains(name)) {
                return true;
            }
        }
        return false;
    }

}
