package controllers.gestionoffres;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import models.gestionoffres.Reservation;
import services.ServiceReservation;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public class ReservationsController {
    @FXML
    private TableView<Reservation> reservationsTable;
    @FXML
    private TableColumn<Reservation, String> idColumn;
    @FXML
    private TableColumn<Reservation, String> offerTitleColumn;
    @FXML
    private TableColumn<Reservation, String> userIdColumn;
    @FXML
    private TableColumn<Reservation, String> seatsColumn;
    @FXML
    private TableColumn<Reservation, String> totalPriceColumn;
    @FXML
    private TableColumn<Reservation, String> statusColumn;
    @FXML
    private TableColumn<Reservation, String> paidColumn;
    @FXML
    private TableColumn<Reservation, Reservation> actionsColumn;
    @FXML
    private Label headerLabel;
    @FXML
    private Label statusLabel;

    private final ServiceReservation serviceReservation = new ServiceReservation();
    private Long offerFilterId;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getId())));
        offerTitleColumn.setCellValueFactory(v -> new SimpleStringProperty(
                v.getValue().getOffer() != null ? safe(v.getValue().getOffer().getTitle()) : "N/A"));
        userIdColumn.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getUserId())));
        seatsColumn.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getReservedSeats())));
        totalPriceColumn.setCellValueFactory(v -> new SimpleStringProperty(String.format(Locale.US, "%.2f", v.getValue().getTotalPrice())));
        statusColumn.setCellValueFactory(v -> new SimpleStringProperty(safe(v.getValue().getStatus())));
        paidColumn.setCellValueFactory(v -> new SimpleStringProperty(Boolean.TRUE.equals(v.getValue().getIsPaid()) ? "Yes" : "No"));
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Reservation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Button confirm = new Button("Confirm");
                confirm.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-background-radius: 8;");
                confirm.setOnAction(e -> doConfirm(item));
                Button cancel = new Button("Cancel");
                cancel.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-background-radius: 8;");
                cancel.setOnAction(e -> doCancel(item));
                Button delete = new Button("Delete");
                delete.setStyle("-fx-background-color: #DB2777; -fx-text-fill: white; -fx-background-radius: 8;");
                delete.setOnAction(e -> doDelete(item));
                HBox actions = new HBox(6, confirm, cancel, delete);
                setGraphic(actions);
            }
        });
        refresh();
    }

    public void setOfferFilterId(Long offerFilterId) {
        this.offerFilterId = offerFilterId;
        if (headerLabel != null && offerFilterId != null) {
            headerLabel.setText("Reservations for offer #" + offerFilterId);
        }
        refresh();
    }

    private void refresh() {
        try {
            List<Reservation> reservations = offerFilterId == null
                    ? serviceReservation.getAll()
                    : serviceReservation.getByOffer(offerFilterId);
            reservationsTable.setItems(FXCollections.observableArrayList(reservations));
            showStatus("Loaded " + reservations.size() + " reservations.", false);
        } catch (SQLException e) {
            showStatus("Load failed: " + e.getMessage(), true);
        }
    }

    private void doConfirm(Reservation reservation) {
        try {
            serviceReservation.confirm(reservation.getId());
            refresh();
        } catch (SQLException e) {
            showStatus("Confirm failed: " + e.getMessage(), true);
        }
    }

    private void doCancel(Reservation reservation) {
        try {
            serviceReservation.cancel(reservation.getId());
            refresh();
        } catch (SQLException e) {
            showStatus("Cancel failed: " + e.getMessage(), true);
        }
    }

    private void doDelete(Reservation reservation) {
        try {
            serviceReservation.delete(reservation);
            refresh();
        } catch (SQLException e) {
            showStatus("Delete failed: " + e.getMessage(), true);
        }
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + (error ? "#FCA5A5" : "#A78BFA") + ";");
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
