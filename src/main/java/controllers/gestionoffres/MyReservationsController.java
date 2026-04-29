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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import models.gestionoffres.Reservation;
import services.ServiceReservation;
import utils.CustomConfirmDialog;
import utils.SessionManager;
import utils.StarfieldHelper;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class MyReservationsController extends SignedInPageControllerBase {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    @FXML private Pane signedInCosmicStarfieldPane;
    @FXML private ScrollPane reservationsScrollPane;
    @FXML private VBox reservationsContainer;
    @FXML private VBox emptyStateBox;
    @FXML private Label statusLabel;

    private final ServiceReservation serviceReservation = new ServiceReservation();
    private final SessionManager session = SessionManager.getInstance();

    @FXML
    private void initialize() {
        initSignedInSidebar();
        StarfieldHelper.populate(signedInCosmicStarfieldPane);
        if (reservationsScrollPane != null) {
            var viewport = reservationsScrollPane.lookup(".viewport");
            if (viewport != null) {
                viewport.setStyle("-fx-background-color: transparent;");
            }
        }
        refresh();
    }

    @FXML
    private void onBack() {
        navigate("/fxml/gestionoffres/Offers.fxml");
    }

    private void refresh() {
        try {
            List<Reservation> reservations = serviceReservation.getByUser(session.getUserId());
            reservationsContainer.getChildren().clear();
            for (Reservation reservation : reservations) {
                reservationsContainer.getChildren().add(buildCard(reservation));
            }
            boolean empty = reservations.isEmpty();
            if (emptyStateBox != null) {
                emptyStateBox.setVisible(empty);
                emptyStateBox.setManaged(empty);
            }
            if (reservationsScrollPane != null) {
                reservationsScrollPane.setVisible(!empty);
                reservationsScrollPane.setManaged(!empty);
            }
            setStatus("Loaded " + reservations.size() + " reservations.", false);
        } catch (SQLException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private VBox buildCard(Reservation reservation) {
        String leftBorderColor = statusColor(reservation.getStatus());
        VBox card = new VBox(12);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: rgba(34, 26, 58, 0.85); -fx-border-color: #2E2550 #2E2550 #2E2550 " + leftBorderColor + "; -fx-border-width: 1 1 1 4; -fx-border-radius: 14; -fx-background-radius: 14;");

        Label title = new Label("🌐 " + (reservation.getOffer() != null ? reservation.getOffer().getTitle() : "Offer"));
        title.setStyle("-fx-text-fill: white; -fx-font-size: 19px; -fx-font-weight: 700;");
        String dateText = reservation.getReservationDate() == null ? "Réservation du --/--/---- à --:--"
                : "Réservation du " + DATE_FMT.format(reservation.getReservationDate());
        Label date = new Label(dateText);
        date.setStyle("-fx-text-fill: #C4B5FD; -fx-font-size: 12px;");
        VBox titleCol = new VBox(2, title, date);

        Label status = new Label(statusText(reservation.getStatus()));
        status.setStyle("-fx-background-color: " + statusColor(reservation.getStatus()) + "; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 4 10;");
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox top = new HBox(10, titleCol, topSpacer, status);

        Separator divider1 = new Separator();
        divider1.setStyle("-fx-opacity: 0.45;");

        HBox infoRow = new HBox(12);
        infoRow.getChildren().addAll(
                metric("👥", "Places réservées", String.valueOf(reservation.getReservedSeats())),
                metric("📞", "Contact", reservation.getContactInfo()),
                metric("🏷", "Prix unitaire", unitPrice(reservation)),
                metric("🧮", "Total", String.format(Locale.US, "%.2f", reservation.getTotalPrice()))
        );
        for (var node : infoRow.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        Separator divider2 = new Separator();
        divider2.setStyle("-fx-opacity: 0.45;");

        HBox actions = new HBox(8);
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        Button view = action("👁 Voir l'offre", "#7C3AED");
        view.setOnAction(e -> {
            session.setSelectedOffer(reservation.getOffer());
            navigate("/fxml/gestionoffres/Offers.fxml");
        });
        actions.getChildren().addAll(actionSpacer, view);

        boolean canMutate = Reservation.STATUS_PENDING.equalsIgnoreCase(reservation.getStatus())
                || Reservation.STATUS_CONFIRMED.equalsIgnoreCase(reservation.getStatus());
        if (canMutate) {
            Button edit = action("✏ Modifier", "#DB2777");
            edit.setOnAction(e -> {
                session.setSelectedOffer(reservation.getOffer());
                session.setSelectedReservation(reservation);
                navigate("/fxml/gestionoffres/ReservationForm.fxml");
            });
            actions.getChildren().add(edit);
        }
        if (Reservation.STATUS_PENDING.equalsIgnoreCase(reservation.getStatus())) {
            Button cancel = outlineAction("✕ Annuler");
            cancel.setOnAction(e -> {
                boolean confirmed = CustomConfirmDialog.show(
                        reservationsContainer.getScene().getWindow(),
                        "Êtes-vous sûr de vouloir annuler cette réservation ?",
                        "Confirmer"
                );
                if (!confirmed) {
                    return;
                }
                try {
                    serviceReservation.cancel(reservation.getId());
                    refresh();
                } catch (SQLException ex) {
                    setStatus(ex.getMessage(), true);
                }
            });
            Button delete = outlineAction("🗑 Supprimer");
            delete.setOnAction(e -> {
                boolean confirmed = CustomConfirmDialog.show(
                        reservationsContainer.getScene().getWindow(),
                        "Êtes-vous sûr de vouloir supprimer cette réservation ?",
                        "Supprimer"
                );
                if (!confirmed) {
                    return;
                }
                try {
                    serviceReservation.delete(reservation);
                    refresh();
                } catch (SQLException ex) {
                    setStatus(ex.getMessage(), true);
                }
            });
            actions.getChildren().addAll(cancel, delete);
        }

        card.getChildren().addAll(top, divider1, infoRow, divider2, actions);
        return card;
    }

    private VBox metric(String icon, String label, String value) {
        Label top = new Label(icon + " " + label);
        top.setStyle("-fx-text-fill: #A78BFA; -fx-font-size: 12;");
        Label val = new Label(value == null || value.isBlank() ? "-" : value);
        String valStyle = "🧮".equals(icon)
                ? "-fx-text-fill: #34D399; -fx-font-size: 14; -fx-font-weight: 700;"
                : "-fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: 600;";
        val.setStyle(valStyle);
        VBox box = new VBox(4, top, val);
        box.setFillWidth(true);
        return box;
    }

    private Button action(String text, String color) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color:" + color + "; -fx-text-fill: white; -fx-background-radius: 10;");
        return button;
    }

    private Button outlineAction(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: transparent; -fx-border-color: #7F1D1D; -fx-text-fill: #FCA5A5; -fx-border-radius: 10;");
        return button;
    }

    private String statusColor(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "confirmed" -> "#059669";
            case "cancelled" -> "#DC2626";
            default -> "#D97706";
        };
    }

    private String statusText(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "confirmed" -> "CONFIRMÉE";
            case "cancelled" -> "ANNULÉE";
            default -> "EN ATTENTE";
        };
    }

    private String unitPrice(Reservation reservation) {
        if (reservation.getOffer() == null || reservation.getOffer().getPrice() == null) {
            return "-";
        }
        String currency = reservation.getOffer().getCurrency() == null ? "" : " " + reservation.getOffer().getCurrency();
        return String.format(Locale.US, "%.2f%s", reservation.getOffer().getPrice(), currency);
    }

    private void navigate(String fxml) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Scene scene = reservationsContainer.getScene();
            scene.setRoot(root);
        } catch (IOException e) {
            setStatus("Navigation failed: " + e.getMessage(), true);
        }
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + (error ? "#FCA5A5" : "#A78BFA"));
    }
}
