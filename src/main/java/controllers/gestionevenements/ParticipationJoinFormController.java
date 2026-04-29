package controllers.gestionevenements;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import models.gestionevenements.EventParticipation;
import models.gestionevenements.TravelEvent;
import models.gestionutilisateurs.User;
import services.gestionevenements.EventParticipationService;

import java.util.function.UnaryOperator;

public class ParticipationJoinFormController {

    @FXML
    private TextField fullNameField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextArea noteArea;
    @FXML
    private CheckBox rulesCheck;

    private Long eventId;
    private Integer userId;

    @FXML
    private void initialize() {
        fullNameField.setTextFormatter(limitLengthFormatter(120));
        phoneField.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            return next.matches("\\+?[0-9 ]{0,20}") ? change : null;
        }));
        noteArea.setTextFormatter(limitLengthFormatter(500));
    }

    public void prepare(TravelEvent event, User user) {
        this.eventId = event != null ? event.getId() : null;
        this.userId = user != null ? user.getId() : null;
        if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
            fullNameField.setText(user.getUsername());
        }
        if (user != null && user.getPhone() != null && !user.getPhone().isBlank()) {
            phoneField.setText(user.getPhone());
        }
    }

    public EventParticipation buildPayload() {
        String name = safe(fullNameField.getText());
        String phone = safe(phoneField.getText());
        String note = safe(noteArea.getText());

        if (!rulesCheck.isSelected()) {
            throw new IllegalArgumentException("Vous devez accepter les conditions de participation.");
        }

        EventParticipation payload = new EventParticipation();
        payload.setEventId(eventId);
        payload.setUserId(userId);
        payload.setStatus(EventParticipationService.STATUS_PENDING);
        payload.setRequesterName(name);
        payload.setContactPhone(phone.isBlank() ? null : phone);
        payload.setRequestNote(note);
        return payload;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private TextFormatter<String> limitLengthFormatter(int maxLength) {
        UnaryOperator<TextFormatter.Change> filter = change ->
                change.getControlNewText().length() <= maxLength ? change : null;
        return new TextFormatter<>(filter);
    }
}
