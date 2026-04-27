package controllers.gestionagences;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import models.gestionagences.AgencyAccount;
import models.gestionagences.ImageAsset;
import services.geo.CountryCatalog;
import services.gestionagences.AgencyAccountService;
import controllers.home.SignedInPageControllerBase;
import utils.NavigationManager;
import utils.StarfieldHelper;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class AgenciesSignedInController extends SignedInPageControllerBase {

    private static final double AGENCY_CARD_WIDTH = 416;
    /**
     * Bundled placeholders (dark cosmic banner + travel road logo) sourced from Unsplash, shipped offline.
     * Remote URLs match the same assets if the classpath files are missing from the build.
     */
    private static final String FALLBACK_BANNER_RESOURCE = "/images/agency/fallback-banner.jpg";
    private static final String FALLBACK_LOGO_RESOURCE = "/images/agency/fallback-logo.jpg";
    private static final String FALLBACK_BANNER_REMOTE =
            "https://images.unsplash.com/photo-1534796636912-3b95b3ab5986?auto=format&fit=crop&w=1400&q=85";
    private static final String FALLBACK_LOGO_REMOTE =
            "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=512&q=85";

    /** Cover banner height (image only; title and meta live in the body below). */
    private static final double AGENCY_IMG_HEIGHT = 168;
    /**
     * Fixed card height so grid rows align; middle column (body) grows so the CTA sits on one baseline
     * and the description area always occupies the same vertical band.
     * Keep in sync with {@code .user-profile-card.agency-directory-card} min/pref/max height in signed-in-unified.css.
     */
    private static final double AGENCY_CARD_FIXED_HEIGHT = 500;
    /** Minimum height for logo + title + location + optional status so short titles do not shrink the block. */
    private static final double AGENCY_CARD_IDENTITY_MIN_HEIGHT = 118;
    /** Description is clamped to two lines so every card keeps the same rhythm. */
    private static final int AGENCY_CARD_DESC_MAX_LINES = 2;
    private static final double AGENCY_CARD_DESC_FIXED_HEIGHT = 44;
    /** Circular agency logo beside the title (profile image or bundled theme fallback). */
    private static final double DIRECTORY_LOGO_OUTER = 56;
    /** Card title/description: set in code so fonts apply (scene CSS often loses to Modena on Labels in ScrollPane+TilePane). */
    private static final float AGENCY_CARD_TITLE_FONT = 34f;
    private static final float AGENCY_CARD_DESC_FONT = 15f;
    private static final float AGENCY_CARD_META_FONT = 12f;
    private static final float VERIFICATION_GLYPH_PX = 14f;

    /** First entry in the country ChoiceBox; must match REST-backed labels in {@link #applyCountryCatalog}. */
    private static final String LABEL_ALL_COUNTRIES = "All countries";

    private PauseTransition searchDebounce;
    private boolean agencyFilterChromeListenersInstalled;

    @FXML
    private VBox agencyDirectoryFiltersRoot;
    @FXML
    private TextField searchField;
    @FXML
    private ChoiceBox<String> countryFilter;
    @FXML
    private Button viewOnMapButton;
    @FXML
    private TilePane agenciesGrid;
    @FXML
    private Label resultCountLabel;
    @FXML
    private Button myAgencyButton;
    @FXML
    private Pane signedInCosmicStarfieldPane;
    @FXML
    private ScrollPane agencyPageScrollPane;

    private final AgencyAccountService agencyService = new AgencyAccountService();
    private final List<AgencyAccount> allAgencies = new ArrayList<>();
    private final List<CountryCatalog.CountryRow> countryRows = new ArrayList<>();

    @FXML
    private void initialize() {
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            nav.showLogin();
            return;
        }
        initSignedInSidebar();

        boolean agencyAdmin = nav.canAccessAgencyAdminFeatures();
        myAgencyButton.setVisible(agencyAdmin);
        myAgencyButton.setManaged(agencyAdmin);

        StarfieldHelper.populate(signedInCosmicStarfieldPane);

        setupFilters();
        installAgencyDirectoryFilterStyling();
        bindSearchDebounce();
        loadAgencies();
        bindResponsiveAgencyGrid();
        applyFilters();
        installInvisiblePageScrollbars();
    }

    private void installInvisiblePageScrollbars() {
        if (agencyPageScrollPane == null) {
            return;
        }
        Runnable hideBars = () -> {
            for (Node node : agencyPageScrollPane.lookupAll(".scroll-bar")) {
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
        agencyPageScrollPane.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(hideBars));
        agencyPageScrollPane.viewportBoundsProperty().addListener((obs, oldB, newB) -> Platform.runLater(hideBars));
        Platform.runLater(hideBars);
    }

    /**
     * Debounces text search so the grid updates shortly after typing stops (Symfony-like dynamic filters).
     */
    private void bindSearchDebounce() {
        searchDebounce = new PauseTransition(Duration.millis(280));
        searchDebounce.setOnFinished(e -> applyFilters());
        searchField.textProperty().addListener((obs, prev, cur) -> {
            searchDebounce.stop();
            searchDebounce.playFromStart();
        });
    }

    private void bindResponsiveAgencyGrid() {
        if (agenciesGrid == null) {
            return;
        }
        agenciesGrid.setAlignment(Pos.TOP_CENTER);
        agenciesGrid.setMaxWidth(Double.MAX_VALUE);
        Runnable updateColumns = () -> {
            double w = agenciesGrid.getWidth();
            if (w <= 1 && agenciesGrid.getScene() != null) {
                w = agenciesGrid.getScene().getWidth() - 320;
            }
            double gap = 28;
            double card = AGENCY_CARD_WIDTH;
            if (w < card + gap) {
                agenciesGrid.setPrefColumns(1);
                return;
            }
            int cols = (int) Math.floor((w + gap) / (card + gap));
            cols = Math.max(1, Math.min(4, cols));
            agenciesGrid.setPrefColumns(cols);
        };
        agenciesGrid.sceneProperty().addListener((obs, oldSc, newSc) -> {
            if (newSc != null) {
                newSc.widthProperty().addListener((o, a, b) -> updateColumns.run());
                updateColumns.run();
            }
        });
        agenciesGrid.widthProperty().addListener((o, a, b) -> updateColumns.run());
        Platform.runLater(updateColumns);
    }

    private void setupFilters() {
        countryFilter.getItems().setAll(LABEL_ALL_COUNTRIES);
        countryFilter.setValue(LABEL_ALL_COUNTRIES);
        countryFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> applyFilters());
    }

    /**
     * Filters sit on Modena defaults unless the cascade wins. We attach a scoped stylesheet to the filter card
     * (late for descendants) and set inline chrome on inputs so the field never paints stock white.
     */
    private void installAgencyDirectoryFilterStyling() {
        URL sheet = AgenciesSignedInController.class.getResource("/css/agency-directory-filters.css");
        if (agencyDirectoryFiltersRoot != null && sheet != null) {
            String ext = sheet.toExternalForm();
            if (!agencyDirectoryFiltersRoot.getStylesheets().contains(ext)) {
                agencyDirectoryFiltersRoot.getStylesheets().add(ext);
            }
        }
        if (!agencyFilterChromeListenersInstalled) {
            agencyFilterChromeListenersInstalled = true;
            if (searchField != null) {
                searchField.focusedProperty().addListener((o, a, b) -> applySearchFieldHardStyle());
            }
            if (countryFilter != null) {
                countryFilter.focusedProperty().addListener((o, a, b) -> applyCountryFilterHardStyle());
                countryFilter.showingProperty().addListener((o, a, b) -> applyCountryFilterHardStyle());
            }
            if (viewOnMapButton != null) {
                viewOnMapButton.hoverProperty().addListener((o, a, b) -> applyViewOnMapButtonHardStyle());
                viewOnMapButton.focusedProperty().addListener((o, a, b) -> applyViewOnMapButtonHardStyle());
            }
            if (myAgencyButton != null) {
                myAgencyButton.hoverProperty().addListener((o, a, b) -> applyMyAgencyFilterButtonHardStyle());
                myAgencyButton.focusedProperty().addListener((o, a, b) -> applyMyAgencyFilterButtonHardStyle());
                myAgencyButton.visibleProperty().addListener((o, a, vis) -> {
                    if (Boolean.TRUE.equals(vis)) {
                        Platform.runLater(this::applyMyAgencyFilterButtonHardStyle);
                    }
                });
            }
        }
        applySearchFieldHardStyle();
        applyCountryFilterHardStyle();
        applyViewOnMapButtonHardStyle();
        applyMyAgencyFilterButtonHardStyle();
        Platform.runLater(() -> {
            applySearchFieldHardStyle();
            applyCountryFilterHardStyle();
            applyViewOnMapButtonHardStyle();
            applyMyAgencyFilterButtonHardStyle();
        });
    }

    private static final String SEARCH_FIELD_STYLE_BASE =
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

    private void applySearchFieldHardStyle() {
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
        searchField.setStyle(SEARCH_FIELD_STYLE_BASE + focusFx + "-fx-border-color: " + border + ";");
    }

    private static final String COUNTRY_FILTER_STYLE_BASE =
            "-fx-background-insets: 0; -fx-border-insets: 0; "
                    + "-fx-min-height: 46; -fx-pref-height: 46; "
                    + "-fx-background-color: rgba(8,12,28,0.9); "
                    + "-fx-border-radius: 14; -fx-background-radius: 14; -fx-border-width: 1; "
                    + "-fx-font-size: 14px; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; "
                    + "-fx-accent: #6d28d9; -fx-mark-color: #e9d5ff; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 14, 0.2, 0, 3); ";

    private void applyCountryFilterHardStyle() {
        if (countryFilter == null) {
            return;
        }
        boolean on = countryFilter.isFocused() || countryFilter.isShowing();
        String border = on ? "rgba(196,181,253,0.92)" : "rgba(167,139,250,0.42)";
        countryFilter.setStyle(COUNTRY_FILTER_STYLE_BASE + "-fx-border-color: " + border + ";");
    }

    private void applyViewOnMapButtonHardStyle() {
        if (viewOnMapButton == null) {
            return;
        }
        boolean hover = viewOnMapButton.isHover();
        boolean focus = viewOnMapButton.isFocused();
        String grad = hover
                ? "linear-gradient(to right, #6d28d9, #7c3aed, #8b5cf6)"
                : "linear-gradient(to right, #5b21b6, #6d28d9, #7c3aed)";
        String border = focus ? "rgba(221,214,254,0.88)" : "rgba(196,181,253,0.58)";
        viewOnMapButton.setStyle(
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

    private void applyMyAgencyFilterButtonHardStyle() {
        if (myAgencyButton == null || !myAgencyButton.isVisible()) {
            return;
        }
        boolean hover = myAgencyButton.isHover();
        boolean focus = myAgencyButton.isFocused();
        String fill = hover ? "rgba(139,92,246,0.28)" : "rgba(255,255,255,0.1)";
        String border = focus ? "rgba(221,214,254,0.78)" : "rgba(196,181,253,0.5)";
        myAgencyButton.setStyle(
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

    private void applyAgencyCardCtaHardStyle(Button btn) {
        if (btn == null) {
            return;
        }
        boolean hover = btn.isHover();
        boolean focus = btn.isFocused();
        String grad = hover
                ? "linear-gradient(to right, #8b5cf6, #ec4899)"
                : "linear-gradient(to right, #7c3aed, #db2777)";
        String border = focus ? "rgba(221,214,254,0.72)" : "transparent";
        btn.setStyle(
                "-fx-background-color: " + grad + "; "
                        + "-fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-font-weight: 800; "
                        + "-fx-background-radius: 12; -fx-border-radius: 12; "
                        + "-fx-border-color: " + border + "; -fx-border-width: 1; "
                        + "-fx-padding: 9 14 9 14; -fx-min-height: 34; "
                        + "-fx-background-insets: 0; -fx-border-insets: 0; "
                        + "-fx-cursor: hand; "
                        + "-fx-effect: dropshadow(gaussian, rgba(124,58,237,0.45), 12, 0.26, 0, 2); "
                        + "-fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
    }

    private void loadAgencies() {
        allAgencies.clear();
        try {
            List<AgencyAccount> dbRows = agencyService.findAll();
            if (dbRows.isEmpty()) {
                allAgencies.addAll(buildMockAgencies());
            } else {
                allAgencies.addAll(dbRows);
            }
        } catch (SQLException e) {
            allAgencies.addAll(buildMockAgencies());
        }
        startCountryCatalogLoad();
    }

    private void startCountryCatalogLoad() {
        Thread t = new Thread(() -> {
            List<CountryCatalog.CountryRow> rows = CountryCatalog.fetchAllOrEmpty();
            if (rows.isEmpty()) {
                rows = new ArrayList<>(CountryCatalog.fallbackSample());
            }
            List<CountryCatalog.CountryRow> loaded = rows;
            Platform.runLater(() -> applyCountryCatalog(loaded));
        }, "agency-directory-countries");
        t.setDaemon(true);
        t.start();
    }

    private void applyCountryCatalog(List<CountryCatalog.CountryRow> rows) {
        countryRows.clear();
        countryRows.addAll(rows);
        String current = countryFilter.getValue();
        List<String> items = new ArrayList<>();
        items.add(LABEL_ALL_COUNTRIES);
        for (CountryCatalog.CountryRow r : countryRows) {
            items.add(r.choiceLabel());
        }
        countryFilter.getItems().setAll(items);
        countryFilter.setValue(items.contains(current) ? current : LABEL_ALL_COUNTRIES);
        applyFilters();
    }

    private String selectedCountryIso2() {
        String v = countryFilter.getValue();
        if (v == null || LABEL_ALL_COUNTRIES.equals(v)) {
            return null;
        }
        for (CountryCatalog.CountryRow r : countryRows) {
            if (r.choiceLabel().equals(v)) {
                return r.cca2();
            }
        }
        if (v.length() == 2 && v.chars().allMatch(Character::isLetter)) {
            return v.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private void applyFilters() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        Comparator<AgencyAccount> byName = Comparator.comparing(
                a -> safe(a.getAgencyName()), String.CASE_INSENSITIVE_ORDER);

        List<AgencyAccount> filtered = allAgencies.stream()
                .filter(a -> matchesQuery(a, query))
                .filter(this::matchesCountry)
                .sorted(byName)
                .collect(Collectors.toList());

        renderGrid(filtered);
        resultCountLabel.setText(formatAgencyResultCount(filtered.size()));
    }

    private static String formatAgencyResultCount(int n) {
        if (n == 0) {
            return "No agencies found";
        }
        if (n == 1) {
            return "1 agency found";
        }
        return n + " agencies found";
    }

    private boolean matchesQuery(AgencyAccount agency, String query) {
        if (query.isBlank()) {
            return true;
        }
        return safe(agency.getAgencyName()).toLowerCase(Locale.ROOT).contains(query)
                || safe(agency.getDescription()).toLowerCase(Locale.ROOT).contains(query)
                || safe(agency.getAddress()).toLowerCase(Locale.ROOT).contains(query)
                || safe(agency.getCountry()).toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesCountry(AgencyAccount agency) {
        String iso = selectedCountryIso2();
        if (iso == null) {
            return true;
        }
        return iso.equalsIgnoreCase(safe(agency.getCountry()));
    }

    private void renderGrid(List<AgencyAccount> agencies) {
        agenciesGrid.getChildren().clear();
        for (AgencyAccount agency : agencies) {
            agenciesGrid.getChildren().add(buildAgencyCard(agency));
        }
    }

    private VBox buildAgencyCard(AgencyAccount agency) {
        VBox card = new VBox();
        card.getStyleClass().addAll("user-profile-card", "agency-directory-card");
        card.setPrefWidth(AGENCY_CARD_WIDTH);
        card.setMinWidth(AGENCY_CARD_WIDTH);
        card.setMaxWidth(AGENCY_CARD_WIDTH);
        card.setMinHeight(AGENCY_CARD_FIXED_HEIGHT);
        card.setPrefHeight(AGENCY_CARD_FIXED_HEIGHT);
        card.setMaxHeight(AGENCY_CARD_FIXED_HEIGHT);
        card.setFillWidth(true);

        StackPane header = new StackPane();
        header.getStyleClass().add("agency-directory-card-header");
        header.setMinHeight(AGENCY_IMG_HEIGHT);
        header.setPrefHeight(AGENCY_IMG_HEIGHT);
        header.setMaxHeight(AGENCY_IMG_HEIGHT);

        StackPane hero = new StackPane();
        hero.getStyleClass().add("agency-directory-hero");
        hero.setMinHeight(AGENCY_IMG_HEIGHT);
        hero.setPrefHeight(AGENCY_IMG_HEIGHT);
        hero.setMaxHeight(AGENCY_IMG_HEIGHT);

        ImageView cover = new ImageView(resolveAgencyBannerImage(agency));
        cover.getStyleClass().addAll("agency-directory-hero-image", "agency-directory-banner-image");
        cover.setPreserveRatio(false);
        cover.setFitWidth(AGENCY_CARD_WIDTH);
        cover.setFitHeight(AGENCY_IMG_HEIGHT);
        cover.setSmooth(true);

        Region shade = new Region();
        shade.setMouseTransparent(true);
        shade.getStyleClass().add("agency-directory-hero-shade");

        hero.getChildren().addAll(cover, shade);
        header.getChildren().add(hero);

        Rectangle headerClip = new Rectangle();
        headerClip.setArcWidth(24);
        headerClip.setArcHeight(24);
        headerClip.setWidth(AGENCY_CARD_WIDTH);
        headerClip.setHeight(AGENCY_IMG_HEIGHT);
        header.setClip(headerClip);
        VBox.setVgrow(header, Priority.NEVER);

        Label title = new Label(safe(agency.getAgencyName(), "Agency"));
        title.getStyleClass().add("agency-directory-title");
        title.setWrapText(false);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMinHeight(Region.USE_PREF_SIZE);

        Label locationLabel = new Label(buildAgencyLocationLine(agency));
        locationLabel.getStyleClass().add("agency-directory-location-line");
        locationLabel.setWrapText(false);
        locationLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        locationLabel.setMinHeight(Region.USE_PREF_SIZE);

        HBox metaRow = new HBox(6);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.getStyleClass().add("agency-directory-meta-row");
        HBox.setHgrow(locationLabel, Priority.ALWAYS);
        metaRow.getChildren().add(locationLabel);

        VBox titleColumn = new VBox(6);
        titleColumn.setAlignment(Pos.CENTER_LEFT);
        titleColumn.getChildren().addAll(title, metaRow);

        Node glyph;
        if (agency.getVerified() != null) {
            glyph = Boolean.TRUE.equals(agency.getVerified())
                    ? AgencyVerificationGlyphs.verifiedMark(VERIFICATION_GLYPH_PX)
                    : AgencyVerificationGlyphs.pendingClockMark(VERIFICATION_GLYPH_PX);
            String tip = Boolean.TRUE.equals(agency.getVerified()) ? "Verified agency" : "Pending verification";
            Tooltip.install(glyph, new Tooltip(tip));
        } else {
            Region slot = new Region();
            slot.setMinSize(VERIFICATION_GLYPH_PX, VERIFICATION_GLYPH_PX);
            slot.setPrefSize(VERIFICATION_GLYPH_PX, VERIFICATION_GLYPH_PX);
            slot.setMaxSize(VERIFICATION_GLYPH_PX, VERIFICATION_GLYPH_PX);
            slot.setOpacity(0);
            glyph = slot;
        }
        metaRow.getChildren().add(glyph);

        HBox.setHgrow(titleColumn, Priority.ALWAYS);

        StackPane logoBadge = buildAgencyLogoBadge(agency);

        String descText = safe(agency.getDescription(), "No description yet.");
        Label desc = new Label(descText);
        desc.getStyleClass().add("agency-directory-desc");
        desc.setWrapText(true);
        desc.setAlignment(Pos.TOP_LEFT);
        desc.setMinHeight(AGENCY_CARD_DESC_FIXED_HEIGHT);
        desc.setPrefHeight(AGENCY_CARD_DESC_FIXED_HEIGHT);
        desc.setMaxHeight(AGENCY_CARD_DESC_FIXED_HEIGHT);

        applyAgencyCardTypography(title, desc, locationLabel);

        HBox identityRow = new HBox(14);
        identityRow.getStyleClass().addAll("agency-directory-identity-row", "agency-directory-title-row");
        identityRow.setAlignment(Pos.CENTER_LEFT);
        identityRow.setMaxWidth(Double.MAX_VALUE);
        identityRow.setMinHeight(AGENCY_CARD_IDENTITY_MIN_HEIGHT);

        VBox body = new VBox(8);
        body.getStyleClass().add("agency-directory-body");
        body.setPadding(new Insets(0, 16, 8, 16));
        body.setFillWidth(true);
        VBox.setVgrow(body, Priority.ALWAYS);
        body.setMaxHeight(Double.MAX_VALUE);
        identityRow.prefWidthProperty().bind(body.widthProperty());
        title.maxWidthProperty().bind(identityRow.widthProperty().subtract(DIRECTORY_LOGO_OUTER + 32));
        locationLabel.maxWidthProperty().bind(title.maxWidthProperty());
        desc.maxWidthProperty().bind(body.widthProperty());
        installDescriptionTwoLineClamp(desc, descText);

        identityRow.setPadding(new Insets(14, 0, 10, 0));
        identityRow.getChildren().addAll(logoBadge, titleColumn);
        body.getChildren().addAll(identityRow, desc);

        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("agency-directory-footer");
        footer.setPadding(new Insets(6, 16, 10, 16));
        Button details = new Button("View agency \u2192");
        details.getStyleClass().add("agency-directory-cta");
        details.hoverProperty().addListener((o, a, b) -> applyAgencyCardCtaHardStyle(details));
        details.focusedProperty().addListener((o, a, b) -> applyAgencyCardCtaHardStyle(details));
        applyAgencyCardCtaHardStyle(details);
        details.setOnAction(e -> onAgencyDetails(agency));
        footer.getChildren().add(details);

        card.getChildren().addAll(header, body, footer);
        return card;
    }

    /**
     * Country / location line only (verification is icon-only when present).
     */
    private String buildAgencyLocationLine(AgencyAccount agency) {
        String iso = CountryCatalog.resolveIso2(agency.getCountry(), agency.getAddress());
        String place = null;
        if (iso != null && !countryRows.isEmpty()) {
            for (CountryCatalog.CountryRow r : countryRows) {
                if (r.cca2().equalsIgnoreCase(iso)) {
                    place = r.name() + " · " + r.cca2();
                    break;
                }
            }
        }
        if (place == null) {
            String raw = safe(agency.getCountry());
            if (!raw.isEmpty()) {
                place = raw;
            } else if (iso != null) {
                place = iso;
            } else {
                place = "Location to be confirmed";
            }
        }
        return place;
    }

    /**
     * Scene stylesheets do not reliably set {@code -fx-font-*} on directory card {@link Label}s; programmatic
     * {@link Label#setFont} wins over Modena while CSS still supplies color, line spacing, and effects.
     */
    private static void applyAgencyCardTypography(Label title, Label desc, Label location) {
        title.setFont(Font.font("Segoe UI", FontWeight.BLACK, AGENCY_CARD_TITLE_FONT));
        desc.setFont(Font.font("Georgia", FontWeight.NORMAL, AGENCY_CARD_DESC_FONT));
        location.setFont(Font.font("Segoe UI", FontWeight.NORMAL, AGENCY_CARD_META_FONT));
    }

    private void installDescriptionTwoLineClamp(Label desc, String fullText) {
        Runnable refresh = () -> {
            double width = Math.max(desc.getWidth(), desc.getMaxWidth());
            if (width <= 1) {
                return;
            }
            String clipped = clampTextToLines(fullText, desc.getFont(), width, AGENCY_CARD_DESC_MAX_LINES);
            if (!clipped.equals(desc.getText())) {
                desc.setText(clipped);
            }
        };
        desc.widthProperty().addListener((o, a, b) -> refresh.run());
        desc.fontProperty().addListener((o, a, b) -> refresh.run());
        Platform.runLater(refresh);
    }

    private static String clampTextToLines(String text, Font font, double width, int maxLines) {
        String src = text == null ? "" : text.trim();
        if (src.isEmpty()) {
            return "";
        }
        if (fitsInLines(src, font, width, maxLines)) {
            return src;
        }
        final String ellipsis = "...";
        int low = 0;
        int high = src.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = src.substring(0, mid).trim() + ellipsis;
            if (fitsInLines(candidate, font, width, maxLines)) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        if (low <= 0) {
            return ellipsis;
        }
        return src.substring(0, low).trim() + ellipsis;
    }

    private static boolean fitsInLines(String text, Font font, double width, int maxLines) {
        Text probe = new Text(text);
        probe.setFont(font);
        probe.setWrappingWidth(width);
        Text lineProbe = new Text("Ag");
        lineProbe.setFont(font);
        double lineHeight = Math.max(1.0, lineProbe.getLayoutBounds().getHeight());
        double maxHeight = (lineHeight * maxLines) + 0.6;
        return probe.getLayoutBounds().getHeight() <= maxHeight;
    }

    /**
     * Wide banner on the card — {@link AgencyAccount#getCoverImageId()} / {@link AgencyAccountService#loadCoverImage(Long)}.
     */
    private Image resolveAgencyBannerImage(AgencyAccount agency) {
        if (agency.getId() != null) {
            try {
                Optional<ImageAsset> cover = agencyService.loadCoverImage(agency.getId());
                if (cover.isPresent() && cover.get().getData() != null && cover.get().getData().length > 0) {
                    return new Image(new ByteArrayInputStream(cover.get().getData()));
                }
            } catch (SQLException ignored) {
                // fall through to stock banner
            }
        }
        return loadBundledOrRemoteImage(FALLBACK_BANNER_RESOURCE, FALLBACK_BANNER_REMOTE);
    }

    private static Image loadBundledOrRemoteImage(String classpathResource, String remoteUrl) {
        URL u = AgenciesSignedInController.class.getResource(classpathResource);
        if (u != null) {
            return new Image(u.toExternalForm(), true);
        }
        return new Image(remoteUrl, true);
    }

    private Optional<Image> tryLoadAgencyProfileLogo(AgencyAccount agency) {
        if (agency.getId() == null) {
            return Optional.empty();
        }
        try {
            Optional<ImageAsset> asset = agencyService.loadAgencyProfileImage(agency.getId());
            if (asset.isPresent() && asset.get().getData() != null && asset.get().getData().length > 0) {
                return Optional.of(new Image(new ByteArrayInputStream(asset.get().getData())));
            }
        } catch (SQLException ignored) {
            // empty
        }
        return Optional.empty();
    }

    private StackPane buildAgencyLogoBadge(AgencyAccount agency) {
        StackPane ring = new StackPane();
        ring.getStyleClass().add("agency-directory-logo-badge");
        ring.setMinSize(DIRECTORY_LOGO_OUTER, DIRECTORY_LOGO_OUTER);
        ring.setPrefSize(DIRECTORY_LOGO_OUTER, DIRECTORY_LOGO_OUTER);
        ring.setMaxSize(DIRECTORY_LOGO_OUTER, DIRECTORY_LOGO_OUTER);
        double outerR = DIRECTORY_LOGO_OUTER / 2.0;
        ring.setClip(new Circle(outerR, outerR, outerR));

        Optional<Image> logo = tryLoadAgencyProfileLogo(agency);
        Image img = logo.orElseGet(() -> loadBundledOrRemoteImage(FALLBACK_LOGO_RESOURCE, FALLBACK_LOGO_REMOTE));
        ImageView iv = new ImageView(img);
        iv.setSmooth(true);
        iv.setCache(true);
        StackPane.setAlignment(iv, Pos.CENTER);
        ring.getChildren().add(iv);
        wireAgencyLogoCoverCrop(iv, img);
        return ring;
    }

    /**
     * Fills the circular badge like {@code object-fit: cover}: center-crop to a square in image space, then scale
     * to the outer diameter (avoids tiny letterboxed photos and busy default crops).
     */
    private void wireAgencyLogoCoverCrop(ImageView iv, Image img) {
        Runnable tryApply = () -> applyAgencyLogoSquareCover(iv, img);
        if (img.getException() != null) {
            return;
        }
        if (img.getWidth() > 0 && img.getHeight() > 0) {
            tryApply.run();
            return;
        }
        img.progressProperty().addListener((obs, o, n) -> {
            if (img.getException() != null) {
                return;
            }
            if (img.getWidth() > 0 && img.getHeight() > 0 || n.doubleValue() >= 1.0) {
                tryApply.run();
            }
        });
        img.errorProperty().addListener((obs, o, hadErr) -> {
            if (Boolean.TRUE.equals(hadErr)) {
                iv.setViewport(null);
            }
        });
        Platform.runLater(tryApply);
    }

    private static void applyAgencyLogoSquareCover(ImageView iv, Image img) {
        if (Boolean.TRUE.equals(iv.getProperties().get("agencyLogoCoverApplied"))) {
            return;
        }
        double iw = img.getWidth();
        double ih = img.getHeight();
        if (iw <= 0 || ih <= 0) {
            return;
        }
        double side = Math.min(iw, ih);
        double x = (iw - side) / 2.0;
        double y = (ih - side) / 2.0;
        iv.setViewport(new Rectangle2D(x, y, side, side));
        iv.setPreserveRatio(false);
        iv.setFitWidth(DIRECTORY_LOGO_OUTER);
        iv.setFitHeight(DIRECTORY_LOGO_OUTER);
        iv.getProperties().put("agencyLogoCoverApplied", Boolean.TRUE);
    }

    private List<AgencyAccount> buildMockAgencies() {
        List<AgencyAccount> mocks = new ArrayList<>();
        mocks.add(mockAgency("Blue Dune Travel", "Luxury desert and skyline itineraries.", "https://bluedune.example", "+971 50 112 3344", "Marina Walk", "AE", true));
        mocks.add(mockAgency("Lagoon Signature", "Island escapes and private-villa packages.", "https://lagoon.example", "+960 77 345 228", "Male Center", "MV", true));
        mocks.add(mockAgency("Aurora Routes", "Northern lights, fjords, and curated winter routes.", "https://aurora.example", "+47 91 222 999", "Bergen Harbor", "NO", false));
        mocks.add(mockAgency("Paris Lumiere Agency", "Art, gastronomy, and boutique city journeys.", "https://lumiere.example", "+33 6 11 55 22 90", "Rive Gauche", "FR", true));
        return mocks;
    }

    private AgencyAccount mockAgency(String name, String desc, String web, String phone, String address, String country, boolean verified) {
        AgencyAccount a = new AgencyAccount();
        a.setAgencyName(name);
        a.setDescription(desc);
        a.setWebsiteUrl(web);
        a.setPhone(phone);
        a.setAddress(address);
        a.setCountry(country);
        a.setVerified(verified);
        return a;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @FXML
    private void onViewOnMap() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("https://www.openstreetmap.org/"));
            }
        } catch (Exception ignored) {
            // No browser available; button remains a no-op.
        }
    }

    @FXML
    private void onMyAgency() {
        Optional<Integer> userIdOpt = NavigationManager.getInstance().sessionUser().map(u -> u.getId());
        if (userIdOpt.isEmpty()) {
            NavigationManager.getInstance().showLogin();
            return;
        }
        try {
            Optional<AgencyAccount> agency = agencyService.findByResponsableId(userIdOpt.get());
            if (agency.isPresent()) {
                NavigationManager.getInstance().showAgencyProfile(agency.get().getId());
                return;
            }
            NavigationManager.getInstance().showAgencyProposal();
        } catch (SQLException e) {
            NavigationManager.getInstance().showAgencyProposal();
        }
    }

    private void onAgencyDetails(AgencyAccount agency) {
        if (agency.getId() == null) {
            resultCountLabel.setText("Selected: " + safe(agency.getAgencyName(), "Agency"));
            return;
        }
        NavigationManager.getInstance().showAgencyProfile(agency.getId());
    }
}
