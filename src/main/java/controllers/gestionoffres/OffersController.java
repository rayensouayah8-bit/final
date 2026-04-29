package controllers.gestionoffres;

import controllers.home.SignedInPageControllerBase;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import models.gestionoffres.TravelOffer;
import services.ServiceTravelOffer;
import utils.CustomConfirmDialog;
import utils.SessionManager;
import utils.StarfieldHelper;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OffersController extends SignedInPageControllerBase {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_OFFER_IMAGE = "/images/default_offer.png";

    @FXML
    private Pane signedInCosmicStarfieldPane;
    @FXML
    private Label pageSubtitleLabel;
    @FXML
    private TextField searchField;
    @FXML
    private Button addOfferButton;
    @FXML
    private Button secondaryActionButton;
    @FXML
    private FlowPane offersFlowPane;
    @FXML
    private ScrollPane offersScrollPane;
    @FXML
    private Label statusLabel;

    private final ServiceTravelOffer serviceTravelOffer = new ServiceTravelOffer();
    private final SessionManager session = SessionManager.getInstance();
    private List<TravelOffer> offers = new ArrayList<>();

    @FXML
    private void initialize() {
        initSignedInSidebar();
        if (signedInCosmicStarfieldPane != null) {
            StarfieldHelper.populate(signedInCosmicStarfieldPane);
        }
        configureTopActions();
        searchField.textProperty().addListener((obs, oldV, newV) -> renderCards());
        Platform.runLater(this::ensureTransparentViewport);
        loadOffers();
    }

    private void configureTopActions() {
        boolean agency = "ROLE_AGENCY".equalsIgnoreCase(session.getRole());
        addOfferButton.setText(agency ? "Add Offer" : "My Reservations");
        secondaryActionButton.setText(agency ? "All Reservation Requests" : "");
        secondaryActionButton.setVisible(agency);
        secondaryActionButton.setManaged(agency);
        pageSubtitleLabel.setText(agency
                ? "Manage your agency offers and reservation requests"
                : "Discover approved travel offers and reserve your seat");
    }

    @FXML
    private void onPrimaryAction() {
        if ("ROLE_AGENCY".equalsIgnoreCase(session.getRole())) {
            session.setSelectedOffer(null);
            goTo("/fxml/gestionoffres/OfferForm.fxml");
        } else {
            goTo("/fxml/gestionoffres/MyReservations.fxml");
        }
    }

    @FXML
    private void onSecondaryAction() {
        goTo("/fxml/gestionoffres/AgencyReservations.fxml");
    }

    private void loadOffers() {
        try {
            if ("ROLE_AGENCY".equalsIgnoreCase(session.getRole())) {
                offers = serviceTravelOffer.getByAgency(session.getAgencyId());
            } else {
                offers = serviceTravelOffer.getApproved();
            }
            renderCards();
            setStatus("Loaded " + offers.size() + " offers.", false);
        } catch (SQLException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private void renderCards() {
        offersFlowPane.getChildren().clear();
        String query = normalize(searchField.getText());
        for (TravelOffer offer : offers) {
            if (!matches(offer, query)) {
                continue;
            }
            offersFlowPane.getChildren().add(buildOfferCard(offer));
        }
    }

    private boolean matches(TravelOffer offer, String query) {
        if (query.isBlank()) {
            return true;
        }
        return normalize(offer.getTitle()).contains(query) || normalize(offer.getCountries()).contains(query);
    }

    private VBox buildOfferCard(TravelOffer offer) {
        VBox card = new VBox(10);
        card.setPrefWidth(330);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: rgba(34, 26, 58, 0.85); -fx-border-color: #2E2550; -fx-border-radius: 16; -fx-background-radius: 16;");

        ImageView cover = new ImageView(resolveImage(offer.getImage()));
        cover.setFitHeight(150);
        cover.setFitWidth(306);
        cover.setPreserveRatio(false);

        Label statusBadge = new Label(safe(offer.getApprovalStatus(), "pending").toUpperCase(Locale.ROOT));
        statusBadge.setStyle("-fx-background-color: " + statusColor(offer.getApprovalStatus()) + "; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 4 10;");
        HBox imageTop = new HBox(statusBadge);
        imageTop.setAlignment(Pos.TOP_RIGHT);
        VBox imageBox = new VBox(6, imageTop, cover);

        Label title = label(safe(offer.getTitle(), "Untitled"), "#FFFFFF", true, 17);
        FlowPane countries = buildCountryPills(offer.getCountries());
        Label price = label(String.format(Locale.US, "%.2f %s", offer.getPrice(), safe(offer.getCurrency(), "EUR")), "#FBCFE8", true, 14);
        Label seats = label("Seats: " + safe(offer.getAvailableSeats()), "#A78BFA", false, 12);
        Label dates = label("Dates: " + formatDate(offer), "#A78BFA", false, 12);

        HBox actions = new HBox(8);
        if ("ROLE_AGENCY".equalsIgnoreCase(session.getRole())) {
            Button edit = actionBtn("Edit", "#7C3AED");
            edit.setOnAction(e -> {
                session.setSelectedOffer(offer);
                goTo("/fxml/gestionoffres/OfferForm.fxml");
            });
            Button delete = actionBtn("Delete", "#BE185D");
            delete.setOnAction(e -> deleteOffer(offer));
            Button reservations = actionBtn("View Reservations", "#4C1D95");
            reservations.setOnAction(e -> {
                session.setSelectedOffer(offer);
                goTo("/fxml/gestionoffres/OfferReservations.fxml");
            });
            actions.getChildren().addAll(edit, delete, reservations);
        } else {
            Button reserve = actionBtn("Reserve", "#7C3AED");
            reserve.setOnAction(e -> {
                session.setSelectedOffer(offer);
                goTo("/fxml/gestionoffres/ReservationForm.fxml");
            });
            actions.getChildren().add(reserve);
        }

        card.getChildren().addAll(imageBox, title, countries, price, seats, dates, actions);
        return card;
    }

    private void deleteOffer(TravelOffer offer) {
        boolean confirm = CustomConfirmDialog.show(
                searchField.getScene().getWindow(),
                "Êtes-vous sûr de vouloir supprimer cette offre ?",
                "Supprimer"
        );
        if (!confirm) {
            return;
        }
        try {
            serviceTravelOffer.delete(offer);
            loadOffers();
            setStatus("Offer deleted.", false);
        } catch (SQLException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private FlowPane buildCountryPills(String countriesValue) {
        FlowPane pills = new FlowPane(6, 6);
        for (String country : parseCountries(countriesValue)) {
            Label pill = new Label(country);
            pill.setStyle("-fx-background-color: #7C3AED66; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 3 9;");
            pills.getChildren().add(pill);
        }
        return pills;
    }

    private List<String> parseCountries(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String cleaned = raw.replace("[", "").replace("]", "").replace("\"", "");
        for (String part : cleaned.split(",")) {
            String c = part.trim();
            if (!c.isBlank()) {
                out.add(c);
            }
        }
        return out;
    }

    private String statusColor(String status) {
        String s = normalize(status);
        if ("approved".equals(s)) {
            return "#059669";
        }
        if ("rejected".equals(s) || "cancelled".equals(s)) {
            return "#DC2626";
        }
        return "#EAB308";
    }

    private String formatDate(TravelOffer offer) {
        if (offer.getDepartureDate() == null || offer.getReturnDate() == null) {
            return "N/A";
        }
        return DATE_FORMAT.format(offer.getDepartureDate()) + " -> " + DATE_FORMAT.format(offer.getReturnDate());
    }

    private Image resolveImage(String path) {
        String source = (path == null || path.isBlank()) ? DEFAULT_OFFER_IMAGE : path;
        var url = getClass().getResource(source);
        if (url != null) {
            return new Image(url.toExternalForm(), 640, 360, false, true, true);
        }
        var fallback = getClass().getResource(DEFAULT_OFFER_IMAGE);
        if (fallback != null) {
            return new Image(fallback.toExternalForm(), 640, 360, false, true, true);
        }
        return new Image("https://picsum.photos/640/360", true);
    }

    private void goTo(String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Scene scene = searchField.getScene();
            scene.setRoot(root);
        } catch (IOException e) {
            setStatus("Navigation failed: " + e.getMessage(), true);
        }
    }

    private Label label(String text, String color, boolean bold, int size) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-size: " + size + "px; -fx-font-weight: " + (bold ? "700" : "400") + ";");
        return label;
    }

    private Button actionBtn(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-background-radius: 10;");
        return btn;
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + (error ? "#FCA5A5" : "#A78BFA") + ";");
    }

    private void ensureTransparentViewport() {
        if (offersScrollPane == null) {
            return;
        }
        var viewport = offersScrollPane.lookup(".viewport");
        if (viewport != null) {
            viewport.setStyle("-fx-background-color: transparent;");
        }
        if (offersScrollPane.getContent() != null) {
            offersScrollPane.getContent().setStyle("-fx-background-color: transparent;");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safe(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }
}
