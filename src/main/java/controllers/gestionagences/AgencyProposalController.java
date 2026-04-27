package controllers.gestionagences;

import enums.gestionagences.AgencyApplicationStatus;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import models.gestionagences.AgencyAccount;
import models.gestionagences.AgencyAdminApplication;
import services.geo.CountryCatalog;
import services.gestionagences.AgencyAccountService;
import services.gestionagences.AgencyAdminApplicationService;
import controllers.home.SignedInPageControllerBase;
import utils.NavigationManager;
import utils.StarfieldHelper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AgencyProposalController extends SignedInPageControllerBase {
    @FXML
    private VBox formContainer;
    @FXML
    private VBox statusContainer;
    @FXML
    private Label statusGlyphLabel;
    @FXML
    private StackPane statusGlyphRing;
    @FXML
    private Label statusTitleLabel;
    @FXML
    private Label statusMessageLabel;
    @FXML
    private VBox pendingDetailsCard;
    @FXML
    private HBox pendingFooterRow;
    @FXML
    private Button statusViewAgenciesButton;
    @FXML
    private Button statusActionButton;
    @FXML
    private TextField agencyNameField;
    @FXML
    private ComboBox<CountryCatalog.CountryRow> countryCombo;
    @FXML
    private StackPane countryFlagFrame;
    @FXML
    private ImageView countryFlagImageView;
    @FXML
    private TextArea messageToAdminField;
    @FXML
    private Label feedbackLabel;
    @FXML
    private Pane signedInCosmicStarfieldPane;
    @FXML
    private BorderPane proposalPageRoot;
    @FXML
    private ScrollPane proposalScrollPane;
    @FXML
    private HBox proposalContentGrid;
    @FXML
    private VBox proposalLeftPanel;
    @FXML
    private VBox proposalFormCard;
    @FXML
    private Button simulateApproveButton;

    private final AgencyAccountService agencyService = new AgencyAccountService();
    private final AgencyAdminApplicationService applicationService = new AgencyAdminApplicationService();

    @FXML
    private void initialize() {
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            nav.showLogin();
            return;
        }
        initSignedInSidebar();

        StarfieldHelper.populate(signedInCosmicStarfieldPane);

        clipFlagFrame();
        wireCountryCombo();
        loadCountriesAsync();
        refreshState();
        installLayoutDiagnostics();
    }

    private void installLayoutDiagnostics() {
        if (proposalPageRoot == null) {
            return;
        }
        proposalPageRoot.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            newScene.widthProperty().addListener((o, oldW, newW) -> logLayoutSnapshot("scene-resize"));
            newScene.heightProperty().addListener((o, oldH, newH) -> logLayoutSnapshot("scene-resize"));
            Window window = newScene.getWindow();
            if (window != null) {
                attachWindowDiagnostics(window);
            } else {
                newScene.windowProperty().addListener((wObs, oldWin, newWin) -> {
                    if (newWin != null) {
                        attachWindowDiagnostics(newWin);
                    }
                });
            }
            Platform.runLater(() -> {
                logLoadedStylesheets(newScene);
                logLayoutSnapshot("initial-layout");
            });
        });
    }

    private void attachWindowDiagnostics(Window window) {
        window.widthProperty().addListener((o, oldW, newW) -> logLayoutSnapshot("window-resize"));
        window.heightProperty().addListener((o, oldH, newH) -> logLayoutSnapshot("window-resize"));
        Platform.runLater(() -> logLayoutSnapshot("window-ready"));
    }

    private void logLoadedStylesheets(Scene scene) {
        System.out.println("[AgencyProposal][stylesheets] " + scene.getStylesheets());
    }

    private void logLayoutSnapshot(String reason) {
        Platform.runLater(() -> {
            Scene scene = proposalPageRoot == null ? null : proposalPageRoot.getScene();
            Window window = scene == null ? null : scene.getWindow();
            Bounds viewportBounds = null;
            if (proposalScrollPane != null) {
                Node viewport = proposalScrollPane.lookup(".viewport");
                if (viewport != null) {
                    viewportBounds = viewport.getLayoutBounds();
                }
            }
            String viewportText = viewportBounds == null
                    ? "n/a"
                    : String.format("%.1fx%.1f", viewportBounds.getWidth(), viewportBounds.getHeight());
            System.out.printf(
                    "[AgencyProposal][layout:%s] window=%.1fx%.1f scene=%.1fx%.1f root=%.1fx%.1f sidebar=%.1fx%.1f scroll=%.1fx%.1f viewport=%s left=%.1fx%.1f right=%.1fx%.1f%n",
                    reason,
                    value(window == null ? Double.NaN : window.getWidth()),
                    value(window == null ? Double.NaN : window.getHeight()),
                    value(scene == null ? Double.NaN : scene.getWidth()),
                    value(scene == null ? Double.NaN : scene.getHeight()),
                    value(proposalPageRoot == null ? Double.NaN : proposalPageRoot.getWidth()),
                    value(proposalPageRoot == null ? Double.NaN : proposalPageRoot.getHeight()),
                    value(signedInSidebarHost == null ? Double.NaN : signedInSidebarHost.getWidth()),
                    value(signedInSidebarHost == null ? Double.NaN : signedInSidebarHost.getHeight()),
                    value(proposalScrollPane == null ? Double.NaN : proposalScrollPane.getWidth()),
                    value(proposalScrollPane == null ? Double.NaN : proposalScrollPane.getHeight()),
                    viewportText,
                    value(proposalLeftPanel == null ? Double.NaN : proposalLeftPanel.getWidth()),
                    value(proposalLeftPanel == null ? Double.NaN : proposalLeftPanel.getHeight()),
                    value(proposalFormCard == null ? Double.NaN : proposalFormCard.getWidth()),
                    value(proposalFormCard == null ? Double.NaN : proposalFormCard.getHeight())
            );
        });
    }

    private static double value(double number) {
        return Double.isNaN(number) ? -1.0 : number;
    }

    private void clipFlagFrame() {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        clip.widthProperty().bind(countryFlagFrame.widthProperty());
        clip.heightProperty().bind(countryFlagFrame.heightProperty());
        countryFlagFrame.setClip(clip);
    }

    private void wireCountryCombo() {
        countryCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CountryCatalog.CountryRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setText(null);
                } else {
                    setText(row.name() + " (" + row.cca2() + ")");
                }
            }
        });
        countryCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(CountryCatalog.CountryRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setText(null);
                } else {
                    setText(row.name() + " (" + row.cca2() + ")");
                }
            }
        });
        countryCombo.valueProperty().addListener((obs, prev, cur) -> updateCountryFlagPreview(cur));
        updateCountryFlagPreview(countryCombo.getValue());
    }

    private void updateCountryFlagPreview(CountryCatalog.CountryRow row) {
        if (row == null || row.cca2() == null || row.cca2().length() != 2) {
            countryFlagImageView.setImage(null);
            return;
        }
        String url = row.flagImageUrl();
        if (url == null) {
            countryFlagImageView.setImage(null);
            return;
        }
        countryFlagImageView.setImage(new Image(url, 160, 107, true, true, true));
    }

    private void refreshState() {
        Optional<Integer> currentUserId = NavigationManager.getInstance().sessionUser().map(u -> u.getId());
        if (currentUserId.isEmpty()) {
            NavigationManager.getInstance().showLogin();
            return;
        }

        try {
            Optional<AgencyAccount> agency = agencyService.findByResponsableId(currentUserId.get());
            if (agency.isPresent()) {
                showAlreadyAgencyState(agency.get());
                return;
            }

            Optional<AgencyAdminApplication> latest = applicationService.findLatestByApplicant(currentUserId.get());
            if (latest.isEmpty()) {
                showFormState();
                return;
            }

            AgencyAdminApplication app = latest.get();
            if (app.getStatus() == AgencyApplicationStatus.APPROVED) {
                showApprovedState();
            } else if (app.getStatus() == AgencyApplicationStatus.PENDING) {
                showPendingState(app);
            } else {
                showRejectedState(app);
            }
        } catch (SQLException e) {
            showFormState();
            feedbackLabel.setText("Unable to load application status: " + e.getMessage());
        }
    }

    private void showFormState() {
        formContainer.setVisible(true);
        formContainer.setManaged(true);
        statusContainer.setVisible(false);
        statusContainer.setManaged(false);
        statusActionButton.setVisible(false);
        statusActionButton.setManaged(false);
        hidePendingDecor();
        hideStatusGlyph();
        if (simulateApproveButton != null) {
            simulateApproveButton.setDisable(false);
        }
    }

    private void hidePendingDecor() {
        pendingDetailsCard.setVisible(false);
        pendingDetailsCard.setManaged(false);
        pendingFooterRow.setVisible(false);
        pendingFooterRow.setManaged(false);
        statusViewAgenciesButton.setVisible(false);
        statusViewAgenciesButton.setManaged(false);
    }

    private void hideStatusGlyph() {
        statusGlyphRing.setVisible(false);
        statusGlyphRing.setManaged(false);
        statusGlyphLabel.setVisible(false);
        statusGlyphLabel.setManaged(false);
        statusGlyphLabel.setText("");
    }

    private void showPendingState(AgencyAdminApplication app) {
        formContainer.setVisible(false);
        formContainer.setManaged(false);
        statusContainer.setVisible(true);
        statusContainer.setManaged(true);
        statusGlyphLabel.setText("\u23F0");
        statusGlyphRing.setVisible(true);
        statusGlyphRing.setManaged(true);
        statusGlyphLabel.setVisible(true);
        statusGlyphLabel.setManaged(true);
        statusTitleLabel.setText("Application pending review");
        statusMessageLabel.setText("Your agency proposal \"" + safe(app.getAgencyNameRequested()) + "\" is under review. Please wait for admin approval.");
        pendingDetailsCard.setVisible(true);
        pendingDetailsCard.setManaged(true);
        pendingFooterRow.setVisible(true);
        pendingFooterRow.setManaged(true);
        statusViewAgenciesButton.setVisible(true);
        statusViewAgenciesButton.setManaged(true);
        statusActionButton.setVisible(false);
        statusActionButton.setManaged(false);
        if (simulateApproveButton != null) {
            simulateApproveButton.setDisable(false);
        }
    }

    private void showRejectedState(AgencyAdminApplication app) {
        formContainer.setVisible(false);
        formContainer.setManaged(false);
        statusContainer.setVisible(true);
        statusContainer.setManaged(true);
        hidePendingDecor();
        hideStatusGlyph();
        statusTitleLabel.setText("Application rejected");
        String note = app.getReviewNote() == null || app.getReviewNote().isBlank()
                ? "No admin note."
                : app.getReviewNote();
        statusMessageLabel.setText("Your previous application was rejected.\nAdmin note: " + note + "\nYou can submit a new proposal now.");
        statusActionButton.setVisible(true);
        statusActionButton.setManaged(true);
        statusActionButton.setText("Submit a new proposal");
        statusActionButton.setOnAction(e -> showFormState());
        if (simulateApproveButton != null) {
            simulateApproveButton.setDisable(true);
        }
    }

    private void showApprovedState() {
        statusContainer.setVisible(true);
        statusContainer.setManaged(true);
        formContainer.setVisible(false);
        formContainer.setManaged(false);
        hidePendingDecor();
        hideStatusGlyph();
        statusTitleLabel.setText("Application approved");
        statusMessageLabel.setText("Your application is approved. You can now access your agency page.");
        statusActionButton.setVisible(true);
        statusActionButton.setManaged(true);
        statusActionButton.setText("Open agency page");
        statusActionButton.setOnAction(e -> NavigationManager.getInstance().showMyAgency());
        if (simulateApproveButton != null) {
            simulateApproveButton.setDisable(true);
        }
    }

    private void showAlreadyAgencyState(AgencyAccount agency) {
        statusContainer.setVisible(true);
        statusContainer.setManaged(true);
        formContainer.setVisible(false);
        formContainer.setManaged(false);
        hidePendingDecor();
        hideStatusGlyph();
        statusTitleLabel.setText("Agency already exists");
        statusMessageLabel.setText("You already manage \"" + safe(agency.getAgencyName()) + "\". Opening agency page.");
        statusActionButton.setVisible(true);
        statusActionButton.setManaged(true);
        statusActionButton.setText("Open agency page");
        statusActionButton.setOnAction(e -> NavigationManager.getInstance().showMyAgency());
        if (simulateApproveButton != null) {
            simulateApproveButton.setDisable(true);
        }
    }

    @FXML
    private void onSubmitProposal() {
        feedbackLabel.setText("");
        Optional<Integer> currentUserId = NavigationManager.getInstance().sessionUser().map(u -> u.getId());
        if (currentUserId.isEmpty()) {
            NavigationManager.getInstance().showLogin();
            return;
        }

        String agencyName = agencyNameField.getText() == null ? "" : agencyNameField.getText().trim();
        String country = readSelectedCountryCode();
        String message = messageToAdminField.getText() == null ? "" : messageToAdminField.getText().trim();

        if (agencyName.isBlank()) {
            feedbackLabel.setText("Agency name is required.");
            return;
        }
        if (country == null || country.isBlank()) {
            feedbackLabel.setText("Country is required.");
            return;
        }

        AgencyAdminApplication app = new AgencyAdminApplication();
        app.setApplicantId(currentUserId.get());
        app.setAgencyNameRequested(agencyName);
        app.setCountry(country);
        app.setMessageToAdmin(message);
        try {
            applicationService.submit(app);
            feedbackLabel.setText("Application submitted successfully.");
            refreshState();
        } catch (SQLException | IllegalArgumentException e) {
            feedbackLabel.setText("Cannot submit application: " + e.getMessage());
        }
    }

    private void loadCountriesAsync() {
        Thread loader = new Thread(() -> {
            List<CountryCatalog.CountryRow> rows = CountryCatalog.fetchAllOrEmpty();
            if (rows.isEmpty()) {
                Platform.runLater(this::applyFallbackCountries);
            } else {
                Platform.runLater(() -> applyCountries(rows));
            }
        }, "proposal-countries");
        loader.setDaemon(true);
        loader.start();
    }

    private void applyCountries(List<CountryCatalog.CountryRow> rows) {
        countryCombo.setItems(FXCollections.observableArrayList(rows));
    }

    private void applyFallbackCountries() {
        countryCombo.setItems(FXCollections.observableArrayList(new ArrayList<>(CountryCatalog.fallbackSample())));
    }

    private String readSelectedCountryCode() {
        CountryCatalog.CountryRow v = countryCombo.getValue();
        return v == null ? null : v.cca2();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @FXML
    private void onSimulateApprove() {
        Optional<Integer> currentUserId = NavigationManager.getInstance().sessionUser().map(u -> u.getId());
        if (currentUserId.isEmpty()) {
            NavigationManager.getInstance().showLogin();
            return;
        }

        try {
            Optional<AgencyAdminApplication> latest = applicationService.findLatestByApplicant(currentUserId.get());
            if (latest.isEmpty() || latest.get().getStatus() != AgencyApplicationStatus.PENDING) {
                setApprovalFeedback("No pending application found to approve.");
                refreshState();
                return;
            }

            applicationService.approve(latest.get().getId(), currentUserId.get());
            setApprovalFeedback("Agency approved successfully (temporary action).");
            refreshState();
        } catch (SQLException | IllegalArgumentException e) {
            setApprovalFeedback("Unable to approve agency: " + e.getMessage());
        }
    }

    private void setApprovalFeedback(String message) {
        if (feedbackLabel != null && feedbackLabel.isVisible() && feedbackLabel.isManaged()) {
            feedbackLabel.setText(message);
        } else if (statusMessageLabel != null) {
            String base = statusMessageLabel.getText() == null ? "" : statusMessageLabel.getText().trim();
            statusMessageLabel.setText(base.isEmpty() ? message : base + "\n\n" + message);
        }
    }

    @FXML
    private void onBackToAgencies() {
        NavigationManager.getInstance().showSignedInAgencies();
    }
}
