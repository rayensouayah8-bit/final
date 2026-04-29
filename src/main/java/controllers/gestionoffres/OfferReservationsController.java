package controllers.gestionoffres;

import controllers.home.SignedInPageControllerBase;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import models.gestionoffres.Reservation;
import models.gestionoffres.TravelOffer;
import services.ServiceReservation;
import utils.CustomConfirmDialog;
import utils.SessionManager;
import utils.StarfieldHelper;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OfferReservationsController extends SignedInPageControllerBase {
    private static final String DEFAULT_OFFER_IMAGE = "/images/default_offer.png";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    @FXML private Pane signedInCosmicStarfieldPane;
    @FXML private Label offerTitleLabel;
    @FXML private Label countLabel;
    @FXML private ToggleButton allStatusBtn;
    @FXML private ToggleButton pendingStatusBtn;
    @FXML private ToggleButton confirmedStatusBtn;
    @FXML private ToggleButton cancelledStatusBtn;
    @FXML private ImageView offerBannerImageView;
    @FXML private Label offerBannerTitleLabel;
    @FXML private FlowPane offerCountriesPane;
    @FXML private Label offerPriceDatesLabel;
    @FXML private Label offerSeatsBadgeLabel;
    @FXML private ScrollPane requestsScrollPane;
    @FXML private VBox requestsContainer;
    @FXML private VBox emptyStateBox;
    @FXML private Label statusLabel;

    private final SessionManager session = SessionManager.getInstance();
    private final ServiceReservation serviceReservation = new ServiceReservation();
    private TravelOffer offer;
    private List<Reservation> allReservations = new ArrayList<>();

    @FXML
    private void initialize() {
        initSignedInSidebar();
        StarfieldHelper.populate(signedInCosmicStarfieldPane);
        if (requestsScrollPane != null) {
            var viewport = requestsScrollPane.lookup(".viewport");
            if (viewport != null) {
                viewport.setStyle("-fx-background-color: transparent;");
            }
        }
        offer = session.getSelectedOffer();
        offerTitleLabel.setText(offer == null ? "Offer Reservations" : offer.getTitle());
        allStatusBtn.setSelected(true);
        fillOfferBanner();
        refresh();
    }

    @FXML
    private void onBack() {
        navigate("/fxml/gestionoffres/Offers.fxml");
    }

    @FXML
    private void onStatusFilterChanged(javafx.event.ActionEvent event) {
        ToggleButton source = (ToggleButton) event.getSource();
        allStatusBtn.setSelected(source == allStatusBtn);
        pendingStatusBtn.setSelected(source == pendingStatusBtn);
        confirmedStatusBtn.setSelected(source == confirmedStatusBtn);
        cancelledStatusBtn.setSelected(source == cancelledStatusBtn);
        renderCards();
    }

    private void refresh() {
        if (offer == null || offer.getId() == null) {
            setStatus("No offer selected.", true);
            return;
        }
        try {
            allReservations = serviceReservation.getByOffer(offer.getId());
            renderCards();
            setStatus("Loaded " + allReservations.size() + " requests.", false);
        } catch (SQLException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private void renderCards() {
        requestsContainer.getChildren().clear();
        List<Reservation> filtered = allReservations.stream().filter(this::matchesStatusFilter).toList();
        for (Reservation r : filtered) {
            requestsContainer.getChildren().add(buildCard(r));
        }
        countLabel.setText(filtered.size() + " demandes");
        boolean empty = filtered.isEmpty();
        requestsScrollPane.setVisible(!empty);
        requestsScrollPane.setManaged(!empty);
        emptyStateBox.setVisible(empty);
        emptyStateBox.setManaged(empty);
    }

    private boolean matchesStatusFilter(Reservation reservation) {
        String status = reservation.getStatus() == null ? "" : reservation.getStatus().toLowerCase(Locale.ROOT);
        if (allStatusBtn.isSelected()) return true;
        if (pendingStatusBtn.isSelected()) return "pending".equals(status);
        if (confirmedStatusBtn.isSelected()) return "confirmed".equals(status);
        if (cancelledStatusBtn.isSelected()) return "cancelled".equals(status);
        return true;
    }

    private VBox buildCard(Reservation reservation) {
        String status = reservation.getStatus() == null ? "pending" : reservation.getStatus().toLowerCase(Locale.ROOT);
        String leftColor = switch (status) {
            case "confirmed" -> "#059669";
            case "cancelled" -> "#DC2626";
            default -> "#D97706";
        };
        String badgeText = switch (status) {
            case "confirmed" -> "CONFIRMÉE";
            case "cancelled" -> "ANNULÉE";
            default -> "EN ATTENTE";
        };

        VBox card = new VBox(10);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: rgba(34, 26, 58, 0.85); -fx-border-color: #2E2550 #2E2550 #2E2550 " + leftColor + "; -fx-border-width: 1 1 1 4; -fx-background-radius: 12; -fx-border-radius: 12;");

        Label userTitle = new Label("👤 Utilisateur #" + reservation.getUserId());
        userTitle.setStyle("-fx-text-fill: white; -fx-font-size: 17; -fx-font-weight: 700;");
        Label date = new Label(reservation.getReservationDate() == null
                ? "Réservé le --/--/----"
                : "Réservé le " + DATE_FMT.format(reservation.getReservationDate()));
        date.setStyle("-fx-text-fill: #C4B5FD; -fx-font-size: 12;");
        VBox titleCol = new VBox(2, userTitle, date);
        Label badge = new Label(badgeText);
        badge.setStyle("-fx-background-color: " + leftColor + "; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 4 10;");
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox top = new HBox(10, titleCol, topSpacer, badge);

        Separator sep1 = new Separator();
        sep1.setStyle("-fx-opacity: 0.45;");

        HBox info = new HBox(12,
                metric("👥", "Places", String.valueOf(reservation.getReservedSeats()), false),
                metric("📞", "Contact", reservation.getContactInfo(), false),
                metric("🏷", "Prix unitaire", unitPrice(), false),
                metric("🧮", "Total", String.format(Locale.US, "%.2f", reservation.getTotalPrice()), true)
        );
        for (var node : info.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        Separator sep2 = new Separator();
        sep2.setStyle("-fx-opacity: 0.45;");

        HBox actions = new HBox(8);
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        actions.getChildren().add(actionSpacer);
        if ("pending".equals(status)) {
            Button confirm = new Button("✓ Confirmer");
            confirm.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-background-radius: 10;");
            confirm.setOnAction(e -> update(reservation, true));
            Button refuse = outlinedRedButton("✕ Refuser");
            refuse.setOnAction(e -> update(reservation, false));
            actions.getChildren().addAll(confirm, refuse);
        } else if ("confirmed".equals(status)) {
            Button cancel = outlinedRedButton("✕ Annuler");
            cancel.setOnAction(e -> update(reservation, false));
            actions.getChildren().add(cancel);
        } else {
            Label none = new Label("Aucune action");
            none.setStyle("-fx-text-fill: #A78BFA;");
            actions.getChildren().add(none);
        }

        card.getChildren().addAll(top, sep1, info, sep2, actions);
        return card;
    }

    private Button outlinedRedButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: transparent; -fx-border-color: #7F1D1D; -fx-text-fill: #FCA5A5; -fx-border-radius: 10; -fx-background-radius: 10;");
        return b;
    }

    private VBox metric(String icon, String title, String value, boolean green) {
        Label top = new Label(icon + " " + title);
        top.setStyle("-fx-text-fill: #A78BFA;");
        Label val = new Label(value == null || value.isBlank() ? "-" : value);
        val.setStyle(green
                ? "-fx-text-fill: #34D399; -fx-font-size: 14; -fx-font-weight: 700;"
                : "-fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: 600;");
        return new VBox(3, top, val);
    }

    private String unitPrice() {
        if (offer == null || offer.getPrice() == null) {
            return "-";
        }
        String currency = offer.getCurrency() == null ? "" : " " + offer.getCurrency();
        return String.format(Locale.US, "%.2f%s", offer.getPrice(), currency);
    }

    private void update(Reservation reservation, boolean confirm) {
        if (!confirm) {
            boolean agreed = CustomConfirmDialog.show(
                    requestsContainer.getScene().getWindow(),
                    "Êtes-vous sûr de vouloir annuler cette réservation ?",
                    "Confirmer"
            );
            if (!agreed) {
                return;
            }
        }
        try {
            if (confirm) {
                serviceReservation.confirm(reservation.getId());
            } else {
                serviceReservation.cancel(reservation.getId());
            }
            refresh();
        } catch (SQLException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private void navigate(String fxml) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Scene scene = requestsContainer.getScene();
            scene.setRoot(root);
        } catch (IOException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + (error ? "#FCA5A5" : "#A78BFA"));
    }

    private void fillOfferBanner() {
        if (offer == null) {
            return;
        }
        offerBannerTitleLabel.setText(offer.getTitle());
        offerCountriesPane.getChildren().setAll(buildCountryPills(offer.getCountries()));
        String dates = (offer.getDepartureDate() != null && offer.getReturnDate() != null)
                ? offer.getDepartureDate().toLocalDate() + " -> " + offer.getReturnDate().toLocalDate()
                : "N/A";
        offerPriceDatesLabel.setText(String.format(Locale.US, "%.2f %s  •  %s",
                offer.getPrice() == null ? 0 : offer.getPrice(),
                offer.getCurrency() == null ? "" : offer.getCurrency(),
                dates));
        offerSeatsBadgeLabel.setText("Seats: " + (offer.getAvailableSeats() == null ? 0 : offer.getAvailableSeats()));
        String imagePath = offer.getImage() == null || offer.getImage().isBlank() ? DEFAULT_OFFER_IMAGE : offer.getImage();
        var url = getClass().getResource(imagePath);
        if (url != null) {
            offerBannerImageView.setImage(new Image(url.toExternalForm(), true));
        } else {
            var fallback = getClass().getResource(DEFAULT_OFFER_IMAGE);
            if (fallback != null) {
                offerBannerImageView.setImage(new Image(fallback.toExternalForm(), true));
            }
        }
    }

    private List<Label> buildCountryPills(String raw) {
        List<Label> pills = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return pills;
        }
        String cleaned = raw.replace("[", "").replace("]", "").replace("\"", "");
        for (String part : cleaned.split(",")) {
            String c = part.trim();
            if (c.isBlank()) continue;
            Label pill = new Label(c);
            pill.setStyle("-fx-background-color: #7C3AED66; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 3 8;");
            pills.add(pill);
        }
        return pills;
    }
}
