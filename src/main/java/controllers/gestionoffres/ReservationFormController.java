package controllers.gestionoffres;

import controllers.home.SignedInPageControllerBase;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import models.gestionoffres.Reservation;
import models.gestionoffres.TravelOffer;
import services.ServiceReservation;
import utils.SessionManager;
import utils.StarfieldHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReservationFormController extends SignedInPageControllerBase {
    private static final String DEFAULT_OFFER_IMAGE = "/images/default_offer.png";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML private Pane signedInCosmicStarfieldPane;
    @FXML private FlowPane layoutFlowPane;
    @FXML private ScrollPane scrollPane;
    @FXML private VBox offerSummaryCard;
    @FXML private VBox reservationFormCard;
    @FXML private Button submitButton;
    @FXML private ImageView offerImageView;
    @FXML private Label offerTitleLabel;
    @FXML private Label offerPriceLabel;
    @FXML private Label offerDatesLabel;
    @FXML private Label offerDescriptionLabel;
    @FXML private Label seatsBadgeLabel;
    @FXML private FlowPane countriesPillsPane;
    @FXML private TextField contactField;
    @FXML private Label editWarningBanner;
    @FXML private Label seatsValueLabel;
    @FXML private Label totalLabel;
    @FXML private Label statusLabel;

    private final SessionManager session = SessionManager.getInstance();
    private final ServiceReservation serviceReservation = new ServiceReservation();
    private TravelOffer offer;
    private Reservation existing;
    private int seats = 1;

    @FXML
    private void initialize() {
        initSignedInSidebar();
        StarfieldHelper.populate(signedInCosmicStarfieldPane);
        if (!"ROLE_USER".equalsIgnoreCase(session.getRole())) {
            setStatus("Only users can reserve offers.", true);
            return;
        }
        offer = session.getSelectedOffer();
        if (offer == null) {
            setStatus("No offer selected.", true);
            return;
        }
        bindOfferTop();
        loadExistingReservation();
        applyResponsiveLayout();
        refreshTotals();
    }

    @FXML
    private void onDecreaseSeats() {
        seats = Math.max(1, seats - 1);
        refreshTotals();
    }

    @FXML
    private void onIncreaseSeats() {
        int max = offer.getAvailableSeats() == null ? 1 : offer.getAvailableSeats();
        seats = Math.min(max, seats + 1);
        refreshTotals();
    }

    @FXML
    private void onSubmit() {
        try {
            if (contactField.getText() == null || contactField.getText().isBlank()) {
                throw new IllegalArgumentException("Contact info is required");
            }
            int max = offer.getAvailableSeats() == null ? 0 : offer.getAvailableSeats();
            if (seats < 1 || seats > max) {
                throw new IllegalArgumentException("Seats must be between 1 and " + max);
            }
            if (existing == null) {
                Reservation created = new Reservation();
                created.setOffer(offer);
                created.setUserId(session.getUserId());
                created.setContactInfo(contactField.getText().trim());
                created.setReservedSeats(seats);
                created.setReservationDate(LocalDateTime.now());
                created.setStatus(Reservation.STATUS_PENDING);
                created.setIsPaid(false);
                serviceReservation.add(created);
                setStatus("Reservation created.", false);
            } else {
                existing.setContactInfo(contactField.getText().trim());
                existing.setReservedSeats(seats);
                existing.setStatus(Reservation.STATUS_PENDING);
                serviceReservation.update(existing);
                setStatus("Your changes have been sent for agency approval", false);
            }
            goBack();
        } catch (Exception e) {
            setStatus(e.getMessage(), true);
        }
    }

    @FXML
    private void onBack() {
        goBack();
    }

    private void bindOfferTop() {
        offerTitleLabel.setText(safeText(offer.getTitle(), "Untitled offer"));
        offerPriceLabel.setText(String.format(Locale.US, "%.2f %s / seat", offer.getPrice() == null ? 0.0 : offer.getPrice(), safeText(offer.getCurrency(), "EUR")));
        offerDatesLabel.setText(formatDates(offer));
        offerDescriptionLabel.setText(safeText(offer.getDescription(), "No description provided for this offer."));
        seatsBadgeLabel.setText("Available: " + offer.getAvailableSeats());
        countriesPillsPane.getChildren().setAll(buildCountryPills(offer.getCountries()));
        offerImageView.setImage(resolveOfferImage(offer.getImage()));
    }

    private void loadExistingReservation() {
        try {
            existing = serviceReservation.getUserReservationForOffer(session.getUserId(), offer.getId());
            if (existing != null) {
                contactField.setText(existing.getContactInfo());
                seats = existing.getReservedSeats() == null ? 1 : existing.getReservedSeats();
                submitButton.setText("Update reservation");
                editWarningBanner.setVisible(true);
                editWarningBanner.setManaged(true);
            }
        } catch (SQLException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private void refreshTotals() {
        seatsValueLabel.setText(String.valueOf(seats));
        double total = (offer.getPrice() == null ? 0 : offer.getPrice()) * seats;
        totalLabel.setText(String.format(Locale.US, "%.2f %s", total, offer.getCurrency()));
    }

    private void goBack() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/gestionoffres/Offers.fxml"));
            Scene scene = contactField.getScene();
            scene.setRoot(root);
        } catch (IOException e) {
            setStatus("Navigation failed: " + e.getMessage(), true);
        }
    }

    private void setStatus(String message, boolean error) {
        if (message != null && message.toLowerCase(Locale.ROOT).contains("already have a reservation")) {
            message = "Vous avez déjà une réservation pour cette offre";
        }
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + (error ? "#FCA5A5" : "#A78BFA"));
    }

    private void applyResponsiveLayout() {
        if (layoutFlowPane == null) {
            return;
        }
        layoutFlowPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.widthProperty().addListener((o, oldW, newW) -> updateWrap(newW.doubleValue()));
                updateWrap(newScene.getWidth());
            }
        });
        var viewport = scrollPane != null ? scrollPane.lookup(".viewport") : null;
        if (viewport != null) {
            viewport.setStyle("-fx-background-color: transparent;");
        }
    }

    private void updateWrap(double width) {
        if (width <= 0 || layoutFlowPane == null) {
            return;
        }
        double contentWidth = Math.max(760, width - 370);
        layoutFlowPane.setPrefWrapLength(contentWidth);
        if (contentWidth < 980) {
            offerSummaryCard.setPrefWidth(contentWidth - 20);
            reservationFormCard.setPrefWidth(contentWidth - 20);
        } else {
            offerSummaryCard.setPrefWidth(420);
            reservationFormCard.setPrefWidth(620);
        }
    }

    private List<Region> buildCountryPills(String rawCountries) {
        List<Region> pills = new ArrayList<>();
        if (rawCountries == null || rawCountries.isBlank()) {
            return pills;
        }
        String normalized = rawCountries.replace("[", "").replace("]", "").replace("\"", "");
        for (String item : normalized.split(",")) {
            String value = item.trim();
            if (value.isBlank()) {
                continue;
            }
            Label pill = new Label(value);
            pill.setStyle("-fx-background-color: #7C3AEDAA; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 4 10;");
            pills.add(pill);
        }
        return pills;
    }

    private String formatDates(TravelOffer travelOffer) {
        if (travelOffer.getDepartureDate() == null || travelOffer.getReturnDate() == null) {
            return "Dates: N/A";
        }
        return "Dates: " + DATE_FMT.format(travelOffer.getDepartureDate()) + " -> " + DATE_FMT.format(travelOffer.getReturnDate());
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Image resolveOfferImage(String storedPath) {
        String path = (storedPath == null || storedPath.isBlank()) ? DEFAULT_OFFER_IMAGE : storedPath.trim();

        if (path.startsWith("http://") || path.startsWith("https://")) {
            return new Image(path, true);
        }

        var classpathUrl = getClass().getResource(path.startsWith("/") ? path : "/" + path);
        if (classpathUrl != null) {
            return new Image(classpathUrl.toExternalForm(), true);
        }

        // Support DB values that may point to file system paths.
        try {
            Path p = Path.of(path);
            if (!p.isAbsolute()) {
                p = Path.of("src/main/resources").resolve(path.replaceFirst("^/+", ""));
            }
            if (Files.exists(p)) {
                return new Image(p.toUri().toString(), true);
            }
        } catch (Exception ignored) {
        }

        var fallback = getClass().getResource(DEFAULT_OFFER_IMAGE);
        if (fallback != null) {
            return new Image(fallback.toExternalForm(), true);
        }
        return new Image("https://picsum.photos/640/360", true);
    }
}
