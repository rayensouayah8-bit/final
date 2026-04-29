package controllers.gestionposts;

import controllers.home.SignedInPageControllerBase;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import models.gestionposts.Post;
import models.gestionutilisateurs.User;
import services.geo.GeoPoint;
import services.geo.GeolocationService;
import services.geo.UserGeoSession;
import services.CloudinaryService;
import services.HuggingFaceService;
import services.gestionposts.PostService;
import services.speech.AudioRecorderService;
import services.speech.GoogleSpeechService;
import services.speech.LocalSpeechFallbackService;
import utils.AppConfig;
import utils.Countries;
import utils.NavigationManager;
import utils.StarfieldHelper;

import java.net.URL;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletionException;

/**
 * Contrôleur principal pour la vue des posts de voyage.
 * Gère l'affichage en cartes, les filtres, la recherche et la pagination.
 */
public class PostsController extends SignedInPageControllerBase implements Initializable {

    @FXML private Pane signedInCosmicStarfieldPane;
    @FXML private VBox postsDirectoryFiltersRoot;
    @FXML private Label userGreetingLabel;
    @FXML private Label roleLabel;
    @FXML private Label postsStatusLabel;
    @FXML private Label postsResultCountLabel;

    @FXML private TextField searchField;
    @FXML private Button voiceSearchButton;
    @FXML private ComboBox<String> countryComboBox;
    @FXML private Button searchButton;
    @FXML private Button clearFiltersButton;
    @FXML private Button myPostsButton;
    @FXML private Button browseAllPostsButton;
    @FXML private Button mapButton;
    @FXML private Button refreshTrendingButton;
    @FXML private TextField chatbotPromptField;
    @FXML private Button chatbotGenerateButton;
    @FXML private Button chatbotNavigateButton;
    @FXML private Label chatbotStatusLabel;
    @FXML private Label voiceSearchStatusLabel;
    @FXML private Label trendingBannerLabel;

    @FXML private FlowPane postsFlowPane;
    @FXML private ScrollPane postsScrollPane;

    @FXML private Button prevPageButton;
    @FXML private Button nextPageButton;
    @FXML private Label pageLabel;
    @FXML private Label totalPostsLabel;

    @FXML private Button addPostButton;

    // Inline Form Fields
    @FXML private StackPane contentStack;
    @FXML private VBox listView;
    @FXML private VBox formView;
    @FXML private Label formTitleLabel;
    @FXML private Label formErrorLabel;
    @FXML private TextField formTitreField;
    @FXML private ComboBox<String> formLocationCombo;
    @FXML private CheckBox attachGeoCheckBox;
    @FXML private Button detectLocationButton;
    @FXML private Label formGeoStatusLabel;
    @FXML private TextField formImageUrlField;
    @FXML private ImageView formImagePreview;
    @FXML private TextArea formContenuArea;
    @FXML private Label titreCounter;
    @FXML private Label contenuCounter;
    @FXML private Button hfImageToTextButton;
    @FXML private Button hfTextToImageButton;
    @FXML private VBox hfAiOverlay;
    @FXML private ProgressBar hfProgressBar;
    @FXML private ProgressIndicator hfSpinner;
    @FXML private Label hfOverlayTitle;
    @FXML private Label hfOverlayDetail;

    private final PostService postService;
    private final GeolocationService geolocationService = new GeolocationService();
    private final HuggingFaceService huggingFaceService = new HuggingFaceService();
    private final CloudinaryService cloudinaryService = new CloudinaryService();
    private final AudioRecorderService audioRecorderService = new AudioRecorderService();
    private final GoogleSpeechService googleSpeechService = new GoogleSpeechService();
    private final LocalSpeechFallbackService localSpeechFallbackService = new LocalSpeechFallbackService();
    private Timeline hfFakeProgressTimeline;
    private Timeline voiceListeningTimeline;
    private boolean formDragDropInstalled;
    private final ObservableList<String> countriesList;
    /** Brouillon lat/lng du formulaire (détection IP ou post existant). */
    private Double formDraftLat;
    private Double formDraftLng;

    private int currentPage = 1;
    private int totalPages = 1;
    private int totalPosts = 0;
    private String currentSearch = "";
    private String currentCountry = null;
    private Post editingPost = null;
    private boolean postsFilterChromeInstalled;
    /** true = grille limitée aux posts de l'utilisateur ; les cartes affichent Modifier / Supprimer. */
    private boolean myPostsMode;
    /** true = affichage limité aux posts tendance (max 12). */
    private boolean trendingOnlyMode;
    private java.time.LocalDateTime lastTrendingRefresh;
    public PostsController() {
        this.postService = new PostService();
        this.countriesList = FXCollections.observableArrayList();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initSignedInSidebar();
        if (signedInCosmicStarfieldPane != null) {
            StarfieldHelper.populate(signedInCosmicStarfieldPane);
        }
        setupUserInfo();
        setupCountriesComboBox();
        installPostsDirectoryFilterStyling();
        setupPostsGrid();
        setupFormCounters();
        setupImagePreview();
        installFormDragDrop();
        setupGeoUi();
        refreshViewModeToggleStyles();
        loadPosts();
    }

    private void setupGeoUi() {
        if (mapButton != null) {
            mapButton.hoverProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(mapButton));
            mapButton.focusedProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(mapButton));
        }
        if (refreshTrendingButton != null) {
            refreshTrendingButton.hoverProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(refreshTrendingButton));
            refreshTrendingButton.focusedProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(refreshTrendingButton));
            applyPostsPurpleFilterButtonHardStyle(refreshTrendingButton);
        }
        if (voiceSearchButton != null) {
            voiceSearchButton.hoverProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(voiceSearchButton));
            voiceSearchButton.focusedProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(voiceSearchButton));
            applyPostsPurpleFilterButtonHardStyle(voiceSearchButton);
        }
        if (chatbotGenerateButton != null) {
            chatbotGenerateButton.hoverProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(chatbotGenerateButton));
            chatbotGenerateButton.focusedProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(chatbotGenerateButton));
            applyPostsPurpleFilterButtonHardStyle(chatbotGenerateButton);
        }
        if (chatbotNavigateButton != null) {
            chatbotNavigateButton.hoverProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(chatbotNavigateButton));
            chatbotNavigateButton.focusedProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(chatbotNavigateButton));
            applyPostsPurpleFilterButtonHardStyle(chatbotNavigateButton);
        }
        applyPostsPurpleFilterButtonHardStyle(mapButton);
    }

    private void installPostsDirectoryFilterStyling() {
        URL sheet = PostsController.class.getResource("/css/agency-directory-filters.css");
        if (postsDirectoryFiltersRoot != null && sheet != null) {
            String ext = sheet.toExternalForm();
            if (!postsDirectoryFiltersRoot.getStylesheets().contains(ext)) {
                postsDirectoryFiltersRoot.getStylesheets().add(ext);
            }
        }
        if (!postsFilterChromeInstalled) {
            postsFilterChromeInstalled = true;
            if (searchField != null) {
                searchField.focusedProperty().addListener((o, a, b) -> applyPostsSearchFieldHardStyle());
            }
            if (countryComboBox != null) {
                countryComboBox.focusedProperty().addListener((o, a, b) -> applyPostsCountryComboHardStyle());
                countryComboBox.showingProperty().addListener((o, a, b) -> applyPostsCountryComboHardStyle());
            }
            if (searchButton != null) {
                searchButton.hoverProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(searchButton));
                searchButton.focusedProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(searchButton));
            }
            if (addPostButton != null) {
                addPostButton.hoverProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(addPostButton));
                addPostButton.focusedProperty().addListener((o, a, b) -> applyPostsPurpleFilterButtonHardStyle(addPostButton));
            }
            if (clearFiltersButton != null) {
                clearFiltersButton.hoverProperty().addListener((o, a, b) -> applyPostsOutlineFilterButtonHardStyle(clearFiltersButton));
                clearFiltersButton.focusedProperty().addListener((o, a, b) -> applyPostsOutlineFilterButtonHardStyle(clearFiltersButton));
            }
            if (myPostsButton != null) {
                myPostsButton.hoverProperty().addListener((o, a, b) -> refreshViewModeToggleStyles());
                myPostsButton.focusedProperty().addListener((o, a, b) -> refreshViewModeToggleStyles());
            }
            if (browseAllPostsButton != null) {
                browseAllPostsButton.hoverProperty().addListener((o, a, b) -> refreshViewModeToggleStyles());
                browseAllPostsButton.focusedProperty().addListener((o, a, b) -> refreshViewModeToggleStyles());
            }
        }
        applyPostsSearchFieldHardStyle();
        applyPostsCountryComboHardStyle();
        applyPostsPurpleFilterButtonHardStyle(searchButton);
        applyPostsPurpleFilterButtonHardStyle(addPostButton);
        applyPostsOutlineFilterButtonHardStyle(clearFiltersButton);
        refreshViewModeToggleStyles();
        Platform.runLater(() -> {
            applyPostsSearchFieldHardStyle();
            applyPostsCountryComboHardStyle();
            applyPostsPurpleFilterButtonHardStyle(searchButton);
            applyPostsPurpleFilterButtonHardStyle(addPostButton);
            applyPostsOutlineFilterButtonHardStyle(clearFiltersButton);
            refreshViewModeToggleStyles();
        });
    }

    private void refreshViewModeToggleStyles() {
        if (myPostsButton == null || browseAllPostsButton == null) {
            return;
        }
        if (myPostsMode) {
            applyPostsPurpleFilterButtonHardStyle(myPostsButton);
            applyPostsOutlineFilterButtonHardStyle(browseAllPostsButton);
        } else {
            applyPostsOutlineFilterButtonHardStyle(myPostsButton);
            applyPostsPurpleFilterButtonHardStyle(browseAllPostsButton);
        }
    }

    private static final String POSTS_SEARCH_FIELD_STYLE_BASE =
            "-fx-background-insets: 0; -fx-border-insets: 0; "
                    + "-fx-min-height: 46; -fx-pref-height: 46; -fx-max-height: 46; "
                    + "-fx-padding: 11 14 11 14; "
                    + "-fx-background-color: rgba(8,12,28,0.92); "
                    + "-fx-control-inner-background: rgba(8,12,28,0.92); "
                    + "-fx-text-fill: #f8fafc; -fx-prompt-text-fill: rgba(203,213,225,0.55); "
                    + "-fx-border-radius: 14; -fx-background-radius: 14; -fx-border-width: 1; "
                    + "-fx-focus-color: transparent; -fx-faint-focus-color: transparent; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 18, 0.22, 0, 4), "
                    + "innershadow(gaussian, rgba(255,255,255,0.06), 12, 0.2, 0, -1); ";

    private void applyPostsSearchFieldHardStyle() {
        if (searchField == null) {
            return;
        }
        String border = searchField.isFocused()
                ? "rgba(196,181,253,0.95)"
                : "rgba(167,139,250,0.42)";
        String focusFx = searchField.isFocused()
                ? "-fx-effect: dropshadow(gaussian, rgba(139,92,246,0.38), 20, 0.28, 0, 0), "
                        + "dropshadow(gaussian, rgba(0,0,0,0.5), 16, 0.22, 0, 4); "
                : "";
        searchField.setStyle(POSTS_SEARCH_FIELD_STYLE_BASE + focusFx + "-fx-border-color: " + border + ";");
    }

    private static final String POSTS_COUNTRY_COMBO_STYLE_BASE =
            "-fx-background-insets: 0; -fx-border-insets: 0; "
                    + "-fx-min-height: 46; -fx-pref-height: 46; "
                    + "-fx-background-color: rgba(8,12,28,0.9); "
                    + "-fx-border-radius: 14; -fx-background-radius: 14; -fx-border-width: 1; "
                    + "-fx-font-size: 14px; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; "
                    + "-fx-accent: #6d28d9; -fx-mark-color: #e9d5ff; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 14, 0.2, 0, 3); ";

    private void applyPostsCountryComboHardStyle() {
        if (countryComboBox == null) {
            return;
        }
        boolean on = countryComboBox.isFocused() || countryComboBox.isShowing();
        String border = on ? "rgba(196,181,253,0.92)" : "rgba(167,139,250,0.42)";
        countryComboBox.setStyle(POSTS_COUNTRY_COMBO_STYLE_BASE + "-fx-border-color: " + border + ";");
    }

    private void applyPostsPurpleFilterButtonHardStyle(Button btn) {
        if (btn == null) {
            return;
        }
        boolean hover = btn.isHover();
        boolean focus = btn.isFocused();
        String grad = hover
                ? "linear-gradient(to right, #6d28d9, #7c3aed, #8b5cf6)"
                : "linear-gradient(to right, #5b21b6, #6d28d9, #7c3aed)";
        String border = focus ? "rgba(221,214,254,0.88)" : "rgba(196,181,253,0.58)";
        btn.setStyle(
                "-fx-background-color: " + grad + "; "
                        + "-fx-min-height: 46; -fx-pref-height: 46; -fx-max-height: 46; "
                        + "-fx-text-fill: #faf5ff; -fx-font-size: 13px; -fx-font-weight: 800; "
                        + "-fx-background-radius: 12; -fx-border-radius: 12; "
                        + "-fx-border-color: " + border + "; -fx-border-width: 1; "
                        + "-fx-padding: 12 20 12 20; -fx-background-insets: 0; -fx-border-insets: 0; "
                        + "-fx-cursor: hand; "
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 18, 0.28, 0, 4); "
                        + "-fx-focus-color: rgba(167,139,250,0.55); -fx-faint-focus-color: transparent;");
    }

    private void applyPostsOutlineFilterButtonHardStyle(Button btn) {
        if (btn == null) {
            return;
        }
        boolean hover = btn.isHover();
        boolean focus = btn.isFocused();
        String fill = hover ? "rgba(139,92,246,0.28)" : "rgba(255,255,255,0.1)";
        String border = focus ? "rgba(221,214,254,0.78)" : "rgba(196,181,253,0.5)";
        btn.setStyle(
                "-fx-background-color: " + fill + "; "
                        + "-fx-min-height: 46; -fx-pref-height: 46; -fx-max-height: 46; "
                        + "-fx-text-fill: #f8fafc; -fx-font-size: 13px; -fx-font-weight: 800; "
                        + "-fx-background-radius: 12; -fx-border-radius: 12; "
                        + "-fx-border-color: " + border + "; -fx-border-width: 1; "
                        + "-fx-padding: 12 20 12 20; -fx-background-insets: 0; -fx-border-insets: 0; "
                        + "-fx-cursor: hand; "
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 14, 0.22, 0, 3); "
                        + "-fx-focus-color: rgba(167,139,250,0.55); -fx-faint-focus-color: transparent;");
    }

    private void setupImagePreview() {
        formImageUrlField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (formImagePreview != null) {
                formImagePreview.setImage(resolveImage(newValue));
            }
        });
        if (formImagePreview != null) {
            formImagePreview.setImage(resolveImage(null));
        }
    }

    @FXML
    private void onBrowseFormImage() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        File selected = chooser.showOpenDialog(formTitreField.getScene().getWindow());
        if (selected != null) {
            formImageUrlField.setText(selected.getAbsolutePath());
        }
    }

    private Image resolveImage(String imagePath) {
        Image fallback = loadFromClasspath("/images/welcome/featured-paris-eiffel.jpg");
        if (imagePath == null || imagePath.isBlank()) {
            return fallback;
        }

        String path = imagePath.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return new Image(path, 480, 270, true, true, true);
        }

        if (path.startsWith("/")) {
            Image classpathImage = loadFromClasspath(path);
            if (classpathImage != null) {
                return classpathImage;
            }
        }

        try {
            Path p = Path.of(path);
            if (Files.exists(p)) {
                return new Image(p.toUri().toString(), 480, 270, true, true, true);
            }
        } catch (Exception ignored) {
        }

        return fallback;
    }

    private Image loadFromClasspath(String path) {
        var url = PostsController.class.getResource(path);
        if (url == null) {
            return null;
        }
        return new Image(url.toExternalForm(), 480, 270, true, true, true);
    }

    private void setupFormCounters() {
        formTitreField.textProperty().addListener((obs, oldVal, newVal) -> {
            int len = newVal != null ? newVal.length() : 0;
            titreCounter.setText(len + "/100");
            titreCounter.setStyle(len < 10 || len > 100 ? "-fx-text-fill: #e74c3c;" : "-fx-text-fill: #27ae60;");
        });

        formContenuArea.textProperty().addListener((obs, oldVal, newVal) -> {
            int len = newVal != null ? newVal.length() : 0;
            contenuCounter.setText(len + "/5000");
            contenuCounter.setStyle(len < 50 || len > 5000 ? "-fx-text-fill: #e74c3c;" : "-fx-text-fill: #27ae60;");
        });

        formLocationCombo.setItems(countriesList);
    }

    private void setupUserInfo() {
        Optional<User> user = NavigationManager.getInstance().sessionUser();
        if (user.isPresent()) {
            userGreetingLabel.setText("Bienvenue, " + user.get().getUsername());
            roleLabel.setText(user.get().getRole() != null ? user.get().getRole() : "Utilisateur");
        } else {
            userGreetingLabel.setText("Bienvenue");
            roleLabel.setText("Invité");
        }
        if (myPostsButton != null) {
            boolean loggedIn = user.isPresent();
            myPostsButton.setDisable(!loggedIn);
            if (!loggedIn && myPostsMode) {
                myPostsMode = false;
                refreshViewModeToggleStyles();
            }
        }
    }

    private void setupCountriesComboBox() {
        // Ajouter l'option "Tous les pays" en premier
        countriesList.add("Tous les pays");
        countriesList.addAll(Countries.getAllCountries());

        // Ajouter aussi les locations existantes dans la BDD
        try {
            List<String> dbLocations = postService.findAllLocationsFromPosts();
            for (String loc : dbLocations) {
                if (!countriesList.contains(loc)) {
                    countriesList.add(loc);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur chargement locations: " + e.getMessage());
        }

        Collections.sort(countriesList.subList(1, countriesList.size()));

        countryComboBox.setItems(countriesList);
        countryComboBox.getSelectionModel().selectFirst();

        countryComboBox.setOnAction(e -> {
            String selected = countryComboBox.getSelectionModel().getSelectedItem();
            currentCountry = "Tous les pays".equals(selected) ? null : selected;
            currentPage = 1;
            loadPosts();
        });
    }

    private void setupPostsGrid() {
        postsFlowPane.setHgap(28);
        postsFlowPane.setVgap(28);
        postsFlowPane.setPadding(new Insets(10, 8, 28, 8));
        postsFlowPane.prefWidthProperty().bind(postsScrollPane.widthProperty().subtract(24));
    }

    /**
     * Charge les posts selon les filtres actuels.
     */
    private void loadPosts() {
        postsFlowPane.getChildren().clear();
        System.out.println("[DEBUG] loadPosts() called - page: " + currentPage + ", myPosts: " + myPostsMode);

        try {
            autoRefreshTrendingIfNeeded();
            List<Post> posts;
            Optional<User> sessionUser = NavigationManager.getInstance().sessionUser();

            if (trendingOnlyMode) {
                posts = postService.findTrendingNow(12);
                totalPosts = posts.size();
                currentPage = 1;
                totalPages = 1;
            } else if (myPostsMode) {
                if (sessionUser.isEmpty()) {
                    myPostsMode = false;
                    refreshViewModeToggleStyles();
                    posts = postService.findAllPaginated(currentPage);
                    totalPosts = postService.countAll();
                } else {
                    int uid = sessionUser.get().getId().intValue();
                    String kw = currentSearch.isEmpty() ? null : currentSearch;
                    posts = postService.findMyPostsPaginated(uid, currentCountry, kw, currentPage);
                    totalPosts = postService.countMyPosts(uid, currentCountry, kw);
                }
            } else if (currentCountry != null && !currentSearch.isEmpty()) {
                // Filtre combiné pays + recherche
                posts = postService.searchByLocationAndKeywordPaginated(currentCountry, currentSearch, currentPage);
                totalPosts = postService.countSearchByLocationAndKeyword(currentCountry, currentSearch);
            } else if (currentCountry != null) {
                // Filtre pays uniquement
                posts = postService.findByLocationPaginated(currentCountry, currentPage);
                totalPosts = postService.countByLocation(currentCountry);
            } else if (!currentSearch.isEmpty()) {
                // Recherche uniquement
                posts = postService.searchPaginated(currentSearch, currentPage);
                totalPosts = postService.countSearch(currentSearch);
            } else {
                // Aucun filtre
                posts = postService.findAllPaginated(currentPage);
                totalPosts = postService.countAll();
            }
            applyTrendingFlags(posts);

            if (!trendingOnlyMode) {
                totalPages = Math.max(1, postService.getTotalPages(totalPosts));
            }
            System.out.println("[DEBUG] Total posts: " + totalPosts + ", Total pages: " + totalPages + ", Posts loaded: " + posts.size());

            // Mettre à jour la pagination
            updatePagination();

            // Afficher les posts
            if (posts.isEmpty()) {
                showStatus("Aucun post trouvé.");
            } else {
                hideStatus();
                for (Post post : posts) {
                    System.out.println("[DEBUG] Adding post card - ID: " + post.getId() + ", Title: " + post.getTitre() + ", Image: " + post.getImageUrl());
                    addPostCard(post);
                }
            }

            totalPostsLabel.setText("Total: " + totalPosts + " post" + (totalPosts > 1 ? "s" : ""));
            if (postsResultCountLabel != null) {
                if (posts.isEmpty()) {
                    postsResultCountLabel.setText(
                            trendingOnlyMode
                                    ? "Aucun post tendance pour le moment."
                                    : (myPostsMode ? "Aucun de vos posts ne correspond aux filtres." : "No posts match your filters."));
                } else {
                    String scope = trendingOnlyMode ? "posts tendance" : (myPostsMode ? "vos posts" : "total");
                    postsResultCountLabel.setText(
                            posts.size() + " sur cette page · " + totalPosts + " " + scope);
                }
            }
            refreshTrendingBanner();

        } catch (SQLException e) {
            showStatus("Erreur lors du chargement des posts: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addPostCard(Post post) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/posts/post_card.fxml"));
            Parent card = loader.load();

            PostCardController controller = loader.getController();
            controller.setOwnerActionsAllowed(myPostsMode);
            controller.setPost(post);
            controller.setOnEdit(this::onEditPost);
            controller.setOnDelete(this::onDeletePost);

            // Animation d'apparition
            card.setOpacity(0);
            postsFlowPane.getChildren().add(card);

            FadeTransition fade = new FadeTransition(Duration.millis(300), card);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();

        } catch (IOException e) {
            System.err.println("Erreur chargement carte post: " + e.getMessage());
        }
    }

    private void updatePagination() {
        pageLabel.setText("Page " + currentPage + " / " + totalPages);
        prevPageButton.setDisable(currentPage <= 1);
        nextPageButton.setDisable(currentPage >= totalPages);
    }

    @FXML
    private void onMyPosts() {
        Optional<User> user = NavigationManager.getInstance().sessionUser();
        if (user.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Connexion");
            a.setHeaderText(null);
            a.setContentText("Connectez-vous pour voir vos posts.");
            a.showAndWait();
            return;
        }
        myPostsMode = true;
        currentPage = 1;
        refreshViewModeToggleStyles();
        loadPosts();
    }

    @FXML
    private void onBrowseAllPosts() {
        myPostsMode = false;
        trendingOnlyMode = false;
        currentPage = 1;
        refreshViewModeToggleStyles();
        loadPosts();
    }

    @FXML
    private void onOpenPostsMap() {
        try {
            Integer restrictUserId = null;
            if (myPostsMode) {
                Optional<User> u = NavigationManager.getInstance().sessionUser();
                if (u.isEmpty()) {
                    Alert a = new Alert(Alert.AlertType.INFORMATION);
                    a.setTitle("Carte");
                    a.setHeaderText(null);
                    a.setContentText("Connectez-vous pour voir uniquement vos posts sur la carte, ou repassez en « Tous les posts ».");
                    a.showAndWait();
                    return;
                }
                restrictUserId = u.get().getId().intValue();
            }
            String loc = currentCountry;
            String kw = (currentSearch != null && !currentSearch.isBlank()) ? currentSearch : null;
            List<Post> markers = postService.findPostsForMapMarkers(restrictUserId, loc, kw);
            if (markers.isEmpty()) {
                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setTitle("Carte");
                a.setHeaderText(null);
                a.setContentText(
                        "Aucun post géolocalisé pour ces filtres. Lors de la création, cochez « Associer des coordonnées » "
                                + "ou renseignez le pays (géocodage automatique). Avec un token Hugging Face, le texte du post peut aussi suggérer un lieu.");
                a.showAndWait();
                return;
            }
            PostsMapWebStage.show(markers);
        } catch (SQLException e) {
            showStatus("Erreur chargement carte : " + e.getMessage());
        }
    }

    @FXML
    private void onRefreshTrending() {
        try {
            postService.recomputeTrendingNow();
            lastTrendingRefresh = java.time.LocalDateTime.now();
            trendingOnlyMode = true;
            myPostsMode = false;
            currentPage = 1;
            loadPosts();
        } catch (SQLException e) {
            showStatus("Erreur actualisation tendances: " + e.getMessage());
        }
    }

    @FXML
    private void onDetectFormLocation() {
        if (formGeoStatusLabel != null) {
            formGeoStatusLabel.setText("Détection…");
        }
        Task<Optional<GeoPoint>> task = new Task<>() {
            @Override
            protected Optional<GeoPoint> call() {
                return geolocationService.locateFromIp();
            }
        };
        task.setOnSucceeded(ev -> {
            Optional<GeoPoint> r = task.getValue();
            if (r.isPresent()) {
                formDraftLat = r.get().latitude();
                formDraftLng = r.get().longitude();
                UserGeoSession.getInstance().setLast(r.get());
                if (formGeoStatusLabel != null) {
                    String lbl = r.get().label() != null ? r.get().label() : "IP";
                    formGeoStatusLabel.setText(String.format("%.4f°, %.4f° — %s", formDraftLat, formDraftLng, lbl));
                }
            } else if (formGeoStatusLabel != null) {
                formGeoStatusLabel.setText("Échec — réessayez plus tard.");
            }
        });
        task.setOnFailed(ev -> {
            if (formGeoStatusLabel != null) {
                formGeoStatusLabel.setText("Erreur réseau.");
            }
        });
        new Thread(task, "smartvoyage-geo-ip").start();
    }

    @FXML
    private void onSearch() {
        currentSearch = searchField.getText().trim();
        trendingOnlyMode = false;
        currentPage = 1;
        loadPosts();
    }

    @FXML
    private void onChatbotGeneratePost() {
        if (!AppConfig.hasHuggingfaceInferenceToken()) {
            showChatbotStatus("Token Hugging Face manquant (config.properties).");
            return;
        }
        String userPrompt = chatbotPromptField != null ? chatbotPromptField.getText() : null;
        String country = userPrompt != null ? userPrompt.trim() : "";
        if (country.length() < 2) {
            showChatbotStatus("Entrez un pays. Exemple: Maroc");
            return;
        }
        if (chatbotGenerateButton != null) chatbotGenerateButton.setDisable(true);
        if (chatbotNavigateButton != null) chatbotNavigateButton.setDisable(true);
        showHfOverlay("Chatbot: génération du post…", "Création automatique du titre et de la description.");
        huggingFaceService.generatePostFromCountryAsync(country).whenComplete((draft, err) -> Platform.runLater(() -> {
            hideHfOverlay();
            if (chatbotGenerateButton != null) chatbotGenerateButton.setDisable(false);
            if (chatbotNavigateButton != null) chatbotNavigateButton.setDisable(false);
            if (err != null) {
                showChatbotStatus(errCause(err));
                return;
            }
            onAddPost();
            if (draft.country() != null && !draft.country().isBlank()) {
                formLocationCombo.setValue(draft.country());
            } else {
                formLocationCombo.setValue(country);
            }
            formTitreField.setText(draft.title());
            formContenuArea.setText(draft.content());
            formContenuArea.positionCaret(formContenuArea.getText().length());
            showChatbotStatus("Brouillon généré. Vérifiez puis cliquez sur Enregistrer.");
        }));
    }

    @FXML
    private void onChatbotNavigate() {
        if (!AppConfig.hasHuggingfaceInferenceToken()) {
            showChatbotStatus("Token Hugging Face manquant (config.properties).");
            return;
        }
        String userPrompt = chatbotPromptField != null ? chatbotPromptField.getText() : null;
        String req = userPrompt != null ? userPrompt.trim() : "";
        if (req.length() < 2) {
            showChatbotStatus("Entrez une commande. Exemple: affiche posts sur France");
            return;
        }
        if (chatbotGenerateButton != null) chatbotGenerateButton.setDisable(true);
        if (chatbotNavigateButton != null) chatbotNavigateButton.setDisable(true);
        showHfOverlay("Chatbot: navigation intelligente…", "Analyse de votre demande de recherche.");
        huggingFaceService.suggestNavigationAsync(req).whenComplete((nav, err) -> Platform.runLater(() -> {
            hideHfOverlay();
            if (chatbotGenerateButton != null) chatbotGenerateButton.setDisable(false);
            if (chatbotNavigateButton != null) chatbotNavigateButton.setDisable(false);
            if (err != null) {
                showChatbotStatus(errCause(err));
                return;
            }
            String country = nav.country() != null ? nav.country().trim() : "";
            String query = nav.query() != null ? nav.query().trim() : "";
            if (!country.isBlank() && countryComboBox.getItems() != null) {
                String match = countryComboBox.getItems().stream()
                        .filter(it -> it != null && it.equalsIgnoreCase(country))
                        .findFirst()
                        .orElse(null);
                if (match != null) {
                    countryComboBox.getSelectionModel().select(match);
                    currentCountry = match;
                } else {
                    countryComboBox.getSelectionModel().selectFirst();
                    currentCountry = null;
                }
            } else {
                countryComboBox.getSelectionModel().selectFirst();
                currentCountry = null;
            }
            searchField.setText(query);
            currentSearch = query;
            trendingOnlyMode = false;
            currentPage = 1;
            loadPosts();
            showChatbotStatus("Navigation appliquée: " + (query.isBlank() ? "filtres pays" : query));
        }));
    }

    @FXML
    private void onVoiceSearch() {
        if (voiceSearchButton != null) {
            voiceSearchButton.setDisable(true);
        }
        showVoiceStatus("En cours d'écoute...");
        startVoiceListeningAnimation();
        final java.util.concurrent.atomic.AtomicReference<String> voiceErrorRef = new java.util.concurrent.atomic.AtomicReference<>();

        Task<Optional<String>> task = new Task<>() {
            @Override
            protected Optional<String> call() throws Exception {
                AudioRecorderService.RecordingResult rec = audioRecorderService.recordToTempWav(java.time.Duration.ofSeconds(3));
                Path wav = rec.wavFile().toPath();
                LocalSpeechFallbackService.OfflineTranscription local = localSpeechFallbackService.transcribeOfflineDetailed(wav);
                if (local.text().isPresent()) {
                    return local.text();
                }
                voiceErrorRef.set(local.errorMessage());
                Optional<String> google = googleSpeechService.transcribe(wav);
                if (google.isPresent()) {
                    voiceErrorRef.set(null);
                    return google;
                }
                return Optional.empty();
            }
        };

        task.setOnSucceeded(evt -> {
            stopVoiceListeningAnimation();
            if (voiceSearchButton != null) {
                voiceSearchButton.setDisable(false);
            }
            Optional<String> transcript = task.getValue();
            if (transcript.isEmpty() || transcript.get().isBlank()) {
                String msg = "Je n'ai rien entendu.";
                String detail = voiceErrorRef.get();
                if (detail != null && !detail.isBlank()) {
                    msg = detail;
                }
                showVoiceStatus(msg);
                return;
            }
            String text = transcript.get().trim();
            searchField.setText(text);
            currentSearch = text;
            trendingOnlyMode = false;
            currentPage = 1;
            loadPosts();
            showVoiceStatus("Recherche vocale: \"" + text + "\"");
        });

        task.setOnFailed(evt -> {
            stopVoiceListeningAnimation();
            if (voiceSearchButton != null) {
                voiceSearchButton.setDisable(false);
            }
            Throwable err = task.getException();
            String msg = err != null ? err.getMessage() : "Erreur de reconnaissance vocale.";
            if (msg == null || msg.isBlank()) {
                msg = "Erreur de reconnaissance vocale.";
            }
            showVoiceStatus(msg);
        });

        Thread t = new Thread(task, "smartvoyage-voice-search");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onClearFilters() {
        searchField.clear();
        countryComboBox.getSelectionModel().selectFirst();
        currentSearch = "";
        currentCountry = null;
        trendingOnlyMode = false;
        currentPage = 1;
        loadPosts();
    }

    private void autoRefreshTrendingIfNeeded() throws SQLException {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (lastTrendingRefresh == null || java.time.Duration.between(lastTrendingRefresh, now).toHours() >= 1) {
            postService.recomputeTrendingNow();
            lastTrendingRefresh = now;
        }
    }

    private void refreshTrendingBanner() {
        if (trendingBannerLabel == null) {
            return;
        }
        if (!trendingOnlyMode) {
            trendingBannerLabel.setVisible(false);
            trendingBannerLabel.setManaged(false);
            trendingBannerLabel.setText("");
            return;
        }
        try {
            List<Post> top = postService.findTrendingNow(3);
            if (top.isEmpty()) {
                trendingBannerLabel.setVisible(false);
                trendingBannerLabel.setManaged(false);
                trendingBannerLabel.setText("");
                return;
            }
            StringBuilder sb = new StringBuilder("🔥 Tendance du moment : ");
            for (int i = 0; i < top.size(); i++) {
                Post p = top.get(i);
                if (i > 0) sb.append(" · ");
                sb.append("#").append(i + 1).append(" ").append(p.getTitre());
                int pc = p.getPositiveCommentsCount() != null ? p.getPositiveCommentsCount() : 0;
                sb.append(" (").append(pc).append(" comm. positifs)");
                if (p.getTrendingGrowthPct() != null) {
                    sb.append(" (").append(String.format("%+.0f%%", p.getTrendingGrowthPct())).append(")");
                }
            }
            trendingBannerLabel.setText(sb.toString());
            trendingBannerLabel.setStyle("-fx-background-color: rgba(249,115,22,0.18); -fx-text-fill: #fdba74; -fx-padding: 10 12; -fx-background-radius: 12;");
            trendingBannerLabel.setVisible(true);
            trendingBannerLabel.setManaged(true);
        } catch (SQLException e) {
            trendingBannerLabel.setVisible(false);
            trendingBannerLabel.setManaged(false);
        }
    }

    private void applyTrendingFlags(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        if (!trendingOnlyMode) {
            for (Post p : posts) {
                p.setTrending(false);
                p.setTrendingScore(0.0);
                p.setTrendingGrowthPct(0.0);
            }
            return;
        }
        try {
            List<Post> top = postService.findTrendingNow(50);
            java.util.Map<Long, Post> byId = new java.util.HashMap<>();
            for (Post t : top) {
                byId.put(t.getId(), t);
            }
            for (Post p : posts) {
                Post t = byId.get(p.getId());
                if (t != null) {
                    p.setTrending(true);
                    p.setTrendingScore(t.getTrendingScore());
                    p.setTrendingGrowthPct(t.getTrendingGrowthPct());
                } else {
                    p.setTrending(false);
                }
            }
        } catch (SQLException ignored) {
        }
    }

    @FXML
    private void onPrevPage() {
        if (currentPage > 1) {
            currentPage--;
            loadPosts();
        }
    }

    @FXML
    private void onNextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            loadPosts();
        }
    }

    @FXML
    private void onAddPost() {
        showInlineForm(null);
    }

    private void onEditPost(Post post) {
        showInlineForm(post);
    }

    private void showInlineForm(Post post) {
        editingPost = post;
        formErrorLabel.setVisible(false);
        formErrorLabel.setManaged(false);
        formDraftLat = null;
        formDraftLng = null;
        if (formGeoStatusLabel != null) {
            formGeoStatusLabel.setText("");
        }
        if (attachGeoCheckBox != null) {
            attachGeoCheckBox.setSelected(post == null);
        }

        if (post != null) {
            formTitleLabel.setText("Modifier le Post");
            formTitreField.setText(post.getTitre());
            formContenuArea.setText(post.getContenu());
            formImageUrlField.setText(post.getImageUrl());
            if (formImagePreview != null) {
                formImagePreview.setImage(resolveImage(post.getImageUrl()));
            }
            formLocationCombo.setValue(post.getLocation());
            if (post.getLatitude() != null) {
                formDraftLat = post.getLatitude();
            }
            if (post.getLongitude() != null) {
                formDraftLng = post.getLongitude();
            }
            if (attachGeoCheckBox != null) {
                attachGeoCheckBox.setSelected(post.getLatitude() != null && post.getLongitude() != null);
            }
            if (formGeoStatusLabel != null && formDraftLat != null && formDraftLng != null) {
                formGeoStatusLabel.setText(String.format("%.4f°, %.4f° (enregistré)", formDraftLat, formDraftLng));
            }
        } else {
            formTitleLabel.setText("Nouveau Post");
            formTitreField.clear();
            formContenuArea.clear();
            formImageUrlField.clear();
            if (formImagePreview != null) {
                formImagePreview.setImage(resolveImage(null));
            }
            formLocationCombo.getSelectionModel().clearSelection();
        }

        // Show form, hide list
        listView.setVisible(false);
        listView.setManaged(false);
        formView.setVisible(true);
        formView.setManaged(true);
    }

    @FXML
    private void onBackToList() {
        hideHfOverlay();
        listView.setVisible(true);
        listView.setManaged(true);
        formView.setVisible(false);
        formView.setManaged(false);
        editingPost = null;
    }

    @FXML
    private void onCancelForm() {
        onBackToList();
    }

    @FXML
    private void onSaveForm() {
        formErrorLabel.setVisible(false);
        formErrorLabel.setManaged(false);

        Post postToSave = editingPost != null ? editingPost : new Post();
        postToSave.setTitre(formTitreField.getText().trim());
        postToSave.setContenu(formContenuArea.getText().trim());
        String selectedLocation = formLocationCombo.getValue();
        if ((selectedLocation == null || selectedLocation.isBlank()) && formLocationCombo.isEditable() && formLocationCombo.getEditor() != null) {
            selectedLocation = formLocationCombo.getEditor().getText();
        }
        if (selectedLocation != null) {
            selectedLocation = selectedLocation.trim();
        }
        postToSave.setLocation((selectedLocation == null || selectedLocation.isBlank()) ? null : selectedLocation);
        postToSave.setImageUrl(formImageUrlField.getText().trim());
        postToSave.setLatitude(null);
        postToSave.setLongitude(null);
        boolean wantsGeo = attachGeoCheckBox == null || attachGeoCheckBox.isSelected() || editingPost == null;
        if (wantsGeo) {
            if (formDraftLat != null && formDraftLng != null) {
                postToSave.setLatitude(formDraftLat);
                postToSave.setLongitude(formDraftLng);
            } else {
                String loc = postToSave.getLocation();
                Optional<GeoPoint> fwd = loc != null && !loc.isBlank()
                        ? geolocationService.forwardGeocode(loc)
                        : Optional.empty();
                if (fwd.isEmpty()) {
                    fwd = geolocationService.geocodeFromPostNarrative(
                            postToSave.getTitre(), postToSave.getContenu());
                }
                if (fwd.isEmpty()) {
                    fwd = geolocationService.locateFromIp();
                }
                fwd.ifPresent(p -> {
                    postToSave.setLatitude(p.latitude());
                    postToSave.setLongitude(p.longitude());
                });
            }
        }

        if (editingPost == null) {
            Optional<User> user = NavigationManager.getInstance().sessionUser();
            postToSave.setUserId(user.isPresent() ? user.get().getId().intValue() : 1);
        }

        try {
            if (editingPost == null) {
                postService.create(postToSave);
                System.out.println("[DEBUG] Post created with ID: " + postToSave.getId());
            } else {
                postService.update(postToSave);
                System.out.println("[DEBUG] Post updated: " + postToSave.getId());
            }

            // Force refresh from page 1 to see the new post
            currentPage = 1;
            loadPosts();
            onBackToList();
            showStatus("Post enregistré avec succès !");
            Platform.runLater(() -> {
                FadeTransition fade = new FadeTransition(Duration.seconds(2), postsStatusLabel);
                fade.setFromValue(1);
                fade.setToValue(0);
                fade.setOnFinished(e -> hideStatus());
                fade.play();
            });

        } catch (IllegalArgumentException e) {
            formErrorLabel.setText(e.getMessage());
            formErrorLabel.setVisible(true);
            formErrorLabel.setManaged(true);
        } catch (SQLException e) {
            formErrorLabel.setText("Erreur base de données: " + e.getMessage());
            formErrorLabel.setVisible(true);
            formErrorLabel.setManaged(true);
        }
    }

    private void installFormDragDrop() {
        if (formView == null || formDragDropInstalled) {
            return;
        }
        formDragDropInstalled = true;
        formView.setOnDragOver(e -> {
            if (e.getGestureSource() != formView && e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        formView.setOnDragDropped(e -> {
            if (!e.getDragboard().hasFiles()) {
                e.setDropCompleted(false);
                e.consume();
                return;
            }
            File f = e.getDragboard().getFiles().get(0);
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp"))) {
                showFormAiError("Formats acceptés : PNG, JPG, JPEG, WEBP.");
                e.setDropCompleted(false);
                e.consume();
                return;
            }
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                runBlipFromBytes(bytes, guessMime(f.getName()));
                e.setDropCompleted(true);
            } catch (IOException ex) {
                showFormAiError("Impossible de lire le fichier : " + ex.getMessage());
                e.setDropCompleted(false);
            }
            e.consume();
        });
    }

    private static String guessMime(String filename) {
        String n = filename.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private void showHfOverlay(String title, String detail) {
        if (hfAiOverlay != null) {
            hfAiOverlay.setVisible(true);
            hfAiOverlay.setManaged(true);
        }
        if (hfOverlayTitle != null) {
            hfOverlayTitle.setText(title);
        }
        if (hfOverlayDetail != null) {
            hfOverlayDetail.setText(detail != null ? detail : "");
        }
        if (hfProgressBar != null) {
            hfProgressBar.setProgress(0);
        }
        if (hfSpinner != null) {
            hfSpinner.setVisible(true);
        }
        startFakeProgress();
        if (hfImageToTextButton != null) {
            hfImageToTextButton.setDisable(true);
        }
        if (hfTextToImageButton != null) {
            hfTextToImageButton.setDisable(true);
        }
    }

    private void hideHfOverlay() {
        stopFakeProgressTimeline();
        if (hfAiOverlay != null) {
            hfAiOverlay.setVisible(false);
            hfAiOverlay.setManaged(false);
        }
        if (hfProgressBar != null) {
            hfProgressBar.setProgress(0);
        }
        if (hfImageToTextButton != null) {
            hfImageToTextButton.setDisable(false);
        }
        if (hfTextToImageButton != null) {
            hfTextToImageButton.setDisable(false);
        }
    }

    private void startFakeProgress() {
        stopFakeProgressTimeline();
        if (hfProgressBar == null) {
            return;
        }
        hfProgressBar.setProgress(0);
        hfFakeProgressTimeline = new Timeline(new KeyFrame(Duration.millis(450), ev -> {
            double p = hfProgressBar.getProgress();
            if (p < 0.92) {
                hfProgressBar.setProgress(p + 0.018);
            }
        }));
        hfFakeProgressTimeline.setCycleCount(Timeline.INDEFINITE);
        hfFakeProgressTimeline.play();
    }

    private void stopFakeProgressTimeline() {
        if (hfFakeProgressTimeline != null) {
            hfFakeProgressTimeline.stop();
            hfFakeProgressTimeline = null;
        }
    }

    private void showFormAiError(String message) {
        if (formErrorLabel != null) {
            formErrorLabel.setText(message);
            formErrorLabel.setVisible(true);
            formErrorLabel.setManaged(true);
        }
    }

    private static String errCause(Throwable err) {
        Throwable c = err;
        while (c instanceof CompletionException && c.getCause() != null) {
            c = c.getCause();
        }
        if (c instanceof IllegalStateException) {
            return c.getMessage() != null ? c.getMessage() : "Opération impossible.";
        }
        return c != null && c.getMessage() != null ? c.getMessage() : "Erreur inconnue.";
    }

    @FXML
    private void onHfImageToText() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une image pour BLIP");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        File selected = chooser.showOpenDialog(formTitreField.getScene().getWindow());
        if (selected == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(selected.toPath());
            runBlipFromBytes(bytes, guessMime(selected.getName()));
        } catch (IOException e) {
            showFormAiError("Impossible de lire l’image : " + e.getMessage());
        }
    }

    private void runBlipFromBytes(byte[] bytes, String mime) {
        showHfOverlay("Analyse de l’image (BLIP)…", "Envoi à Hugging Face — patientez quelques secondes.");
        huggingFaceService.captionImageAsync(bytes, mime).whenComplete((caption, err) -> Platform.runLater(() -> {
            hideHfOverlay();
            if (err != null) {
                showFormAiError(errCause(err));
                return;
            }
            String cur = formContenuArea.getText() != null ? formContenuArea.getText().trim() : "";
            String next = cur.isEmpty() ? caption : cur + "\n\n" + caption;
            if (next.length() > 5000) {
                next = next.substring(0, 5000);
            }
            formContenuArea.setText(next);
            formContenuArea.positionCaret(next.length());
        }));
    }

    @FXML
    private void onHfTextToImage() {
        if (!AppConfig.hasHuggingfaceInferenceToken()) {
            showFormAiError(
                    "Token Hugging Face manquant : ajoutez huggingface.api.token dans config.properties "
                            + "ou la variable d’environnement HUGGINGFACE_API_TOKEN.");
            return;
        }
        String fromArea = formContenuArea.getText() != null ? formContenuArea.getText().trim() : "";
        String prompt = fromArea.length() >= 8 ? fromArea : null;
        if (prompt == null) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Stable Diffusion");
            dialog.setHeaderText("Description de l’image");
            dialog.setContentText("Exemple : plage au coucher du soleil");
            Optional<String> r = dialog.showAndWait();
            if (r.isEmpty() || r.get().isBlank()) {
                return;
            }
            prompt = r.get().trim();
        }
        String finalPrompt = prompt;
        boolean cloud = AppConfig.isCloudinaryConfigured();
        showHfOverlay(
                "Génération d’image (souvent 15–45 s)…",
                cloud
                        ? "Stable Diffusion puis envoi sur Cloudinary."
                        : "Stable Diffusion — l’image sera enregistrée dans %USERPROFILE%\\.smartvoyage\\generated\\");
        if (cloud) {
            huggingFaceService.textToImageUrlAsync(finalPrompt, cloudinaryService).whenComplete((url, err) -> Platform.runLater(() -> {
                hideHfOverlay();
                if (err != null) {
                    showFormAiError(errCause(err));
                    return;
                }
                applyGeneratedImageUrl(url);
            }));
        } else {
            huggingFaceService.textToImageBytesAsync(finalPrompt).whenComplete((bytes, err) -> Platform.runLater(() -> {
                hideHfOverlay();
                if (err != null) {
                    showFormAiError(errCause(err));
                    return;
                }
                try {
                    applyGeneratedImageUrl(saveStableDiffusionImageLocally(bytes));
                } catch (IOException ex) {
                    showFormAiError("Impossible d’enregistrer l’image sur le disque : " + ex.getMessage());
                }
            }));
        }
    }

    private void applyGeneratedImageUrl(String url) {
        formImageUrlField.setText(url);
        if (formImagePreview != null) {
            formImagePreview.setImage(resolveImage(url));
        }
    }

    private static String saveStableDiffusionImageLocally(byte[] bytes) throws IOException {
        Path dir = Path.of(System.getProperty("user.home"), ".smartvoyage", "generated");
        Files.createDirectories(dir);
        boolean jpeg = bytes != null && bytes.length > 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8;
        String ext = jpeg ? ".jpg" : ".png";
        Path out = dir.resolve("sd-" + System.currentTimeMillis() + ext);
        Files.write(out, bytes);
        return out.toAbsolutePath().toUri().toString();
    }

    private void onDeletePost(Post post) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Supprimer le post");
        alert.setContentText("Êtes-vous sûr de vouloir supprimer \"" + post.getTitre() + "\" ?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                postService.delete(post.getId());
                loadPosts();
                showStatus("Post supprimé avec succès !");
            } catch (SQLException e) {
                showStatus("Erreur lors de la suppression: " + e.getMessage());
            }
        }
    }

    private void showStatus(String message) {
        postsStatusLabel.setText(message);
        postsStatusLabel.setVisible(true);
        postsStatusLabel.setManaged(true);
    }

    private void hideStatus() {
        postsStatusLabel.setVisible(false);
        postsStatusLabel.setManaged(false);
    }

    private void showVoiceStatus(String message) {
        if (voiceSearchStatusLabel == null) {
            return;
        }
        voiceSearchStatusLabel.setText(message == null ? "" : message);
        boolean visible = message != null && !message.isBlank();
        voiceSearchStatusLabel.setVisible(visible);
        voiceSearchStatusLabel.setManaged(visible);
    }

    private void showChatbotStatus(String message) {
        if (chatbotStatusLabel == null) {
            return;
        }
        chatbotStatusLabel.setText(message == null ? "" : message);
        boolean visible = message != null && !message.isBlank();
        chatbotStatusLabel.setVisible(visible);
        chatbotStatusLabel.setManaged(visible);
    }

    private void startVoiceListeningAnimation() {
        if (voiceSearchStatusLabel == null) {
            return;
        }
        if (voiceListeningTimeline != null) {
            voiceListeningTimeline.stop();
        }
        final String[] frames = {"En cours d'écoute.", "En cours d'écoute..", "En cours d'écoute..."};
        final int[] idx = {0};
        voiceListeningTimeline = new Timeline(new KeyFrame(Duration.millis(350), e -> {
            voiceSearchStatusLabel.setText(frames[idx[0] % frames.length]);
            idx[0]++;
        }));
        voiceListeningTimeline.setCycleCount(Timeline.INDEFINITE);
        voiceListeningTimeline.play();
    }

    private void stopVoiceListeningAnimation() {
        if (voiceListeningTimeline != null) {
            voiceListeningTimeline.stop();
            voiceListeningTimeline = null;
        }
    }

    // ========== Navigation ==========

    @FXML protected void onHome() { NavigationManager.getInstance().showSignedInShell(); }
    @FXML protected void onOffres() { NavigationManager.getInstance().showSignedInOffers(); }
    @FXML protected void onAgences() { NavigationManager.getInstance().showSignedInAgencies(); }
    @FXML protected void onMessagerie() { }
    @FXML protected void onRecommandation() {
        NavigationManager.getInstance().showSignedInPosts();
    }
    @FXML protected void onEvenement() { NavigationManager.getInstance().showSignedInEvents(); }
    @FXML protected void onPremium() { }
    @FXML protected void onNotifications() { }
    @FXML protected void onProfile() { NavigationManager.getInstance().showUserProfile(); }
    @FXML protected void onDashboardIa() { NavigationManager.getInstance().showAdminDashboard(); }
    @FXML protected void onLogout() { NavigationManager.getInstance().logoutToGuest(); }
    @FXML protected void onThemeToggle() { NavigationManager.getInstance().toggleTheme(); }
}
