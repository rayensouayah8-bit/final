package controllers.gestionoffres;

import controllers.home.SignedInPageControllerBase;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import models.gestionoffres.Reservation;
import models.gestionoffres.TravelOffer;
import services.ServiceReservation;
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

public class AgencyReservationsController extends SignedInPageControllerBase {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    @FXML private Pane signedInCosmicStarfieldPane;
    @FXML private ComboBox<TravelOffer> offerFilterCombo;
    @FXML private ToggleButton allStatusBtn;
    @FXML private ToggleButton pendingStatusBtn;
    @FXML private ToggleButton confirmedStatusBtn;
    @FXML private ToggleButton cancelledStatusBtn;
    @FXML private ScrollPane requestsScrollPane;
    @FXML private VBox requestsContainer;
    @FXML private VBox emptyStateBox;
    @FXML private Label statusLabel;

    private final SessionManager session = SessionManager.getInstance();
    private final ServiceReservation serviceReservation = new ServiceReservation();
    private final ServiceTravelOffer serviceTravelOffer = new ServiceTravelOffer();
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
        loadFilterOffers();
        allStatusBtn.setSelected(true);
        loadReservations();
    }

    @FXML
    private void onBack() {
        navigate("/fxml/gestionoffres/Offers.fxml");
    }

    @FXML
    private void onFilterChanged() {
        renderRequests();
    }

    @FXML
    private void onStatusFilterChanged(javafx.event.ActionEvent event) {
        ToggleButton source = (ToggleButton) event.getSource();
        allStatusBtn.setSelected(source == allStatusBtn);
        pendingStatusBtn.setSelected(source == pendingStatusBtn);
        confirmedStatusBtn.setSelected(source == confirmedStatusBtn);
        cancelledStatusBtn.setSelected(source == cancelledStatusBtn);
        renderRequests();
    }

    private void loadFilterOffers() {
        try {
            List<TravelOffer> offers = serviceTravelOffer.getByAgency(session.getAgencyId());
            offerFilterCombo.setItems(FXCollections.observableArrayList(offers));
            offerFilterCombo.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(TravelOffer object) {
                    return object == null ? "All offers" : object.getTitle();
                }
                @Override
                public TravelOffer fromString(String string) { return null; }
            });
        } catch (SQLException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private void loadReservations() {
        try {
            allReservations = serviceReservation.getByAgency(session.getAgencyId());
            renderRequests();
        } catch (SQLException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private void renderRequests() {
        requestsContainer.getChildren().clear();
        List<Reservation> filtered = allReservations.stream()
                .filter(this::matchesOfferFilter)
                .filter(this::matchesStatusFilter)
                .toList();

        for (Reservation reservation : filtered) {
            requestsContainer.getChildren().add(buildCard(reservation));
        }
        boolean empty = filtered.isEmpty();
        requestsScrollPane.setVisible(!empty);
        requestsScrollPane.setManaged(!empty);
        emptyStateBox.setVisible(empty);
        emptyStateBox.setManaged(empty);
        setStatus(filtered.size() + " demandes au total", false);
    }

    private boolean matchesOfferFilter(Reservation reservation) {
        TravelOffer selected = offerFilterCombo.getValue();
        if (selected == null) {
            return true;
        }
        return reservation.getOffer() != null && selected.getId().equals(reservation.getOffer().getId());
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
        card.setStyle("-fx-background-color: rgba(34, 26, 58, 0.85); -fx-border-color: #2E2550 #2E2550 #2E2550 " + leftColor + "; -fx-border-width: 1 1 1 4; -fx-background-radius: 14; -fx-border-radius: 14;");

        Label title = new Label("🌐 " + (reservation.getOffer() != null ? reservation.getOffer().getTitle() : "Offer"));
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18; -fx-font-weight: 700;");
        Label date = new Label(reservation.getReservationDate() == null
                ? "Réservation: N/A"
                : "Réservation du " + DATE_FMT.format(reservation.getReservationDate()));
        date.setStyle("-fx-text-fill: #C4B5FD; -fx-font-size: 12;");
        VBox titleBox = new VBox(2, title, date);
        Label badge = new Label(badgeText);
        badge.setStyle("-fx-background-color: " + leftColor + "; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 4 10;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(10, titleBox, spacer, badge);

        Separator sep1 = new Separator();
        sep1.setStyle("-fx-opacity: 0.45;");

        HBox info = new HBox(12);
        info.getChildren().addAll(
                metric("👤", "Utilisateur", String.valueOf(reservation.getUserId()), false),
                metric("👥", "Places", String.valueOf(reservation.getReservedSeats()), false),
                metric("📞", "Contact", reservation.getContactInfo(), false),
                metric("💰", "Total", String.format(Locale.US, "%.2f", reservation.getTotalPrice()), true)
        );
        for (var n : info.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
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
            confirm.setOnAction(e -> updateReservation(reservation, true));
            Button cancel = outlinedCancelButton();
            cancel.setOnAction(e -> updateReservation(reservation, false));
            actions.getChildren().addAll(confirm, cancel);
        } else if ("confirmed".equals(status)) {
            Button cancel = outlinedCancelButton();
            cancel.setOnAction(e -> updateReservation(reservation, false));
            actions.getChildren().add(cancel);
        } else {
            Label none = new Label("Aucune action disponible");
            none.setStyle("-fx-text-fill: #A78BFA;");
            actions.getChildren().add(none);
        }

        card.getChildren().addAll(top, sep1, info, sep2, actions);
        return card;
    }

    private VBox metric(String icon, String title, String value, boolean highlight) {
        Label top = new Label(icon + " " + title);
        top.setStyle("-fx-text-fill: #A78BFA;");
        Label bottom = new Label(value == null || value.isBlank() ? "-" : value);
        bottom.setStyle(highlight
                ? "-fx-text-fill: #34D399; -fx-font-size: 14; -fx-font-weight: 700;"
                : "-fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: 600;");
        return new VBox(3, top, bottom);
    }

    private Button outlinedCancelButton() {
        Button b = new Button("✕ Annuler");
        b.setStyle("-fx-background-color: transparent; -fx-border-color: #7F1D1D; -fx-text-fill: #FCA5A5; -fx-border-radius: 10; -fx-background-radius: 10;");
        return b;
    }

    private void updateReservation(Reservation reservation, boolean confirm) {
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
            loadReservations();
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
}
