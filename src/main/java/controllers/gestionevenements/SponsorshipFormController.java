package controllers.gestionevenements;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import models.gestionevenements.EventSponsorship;
import models.gestionevenements.TravelEvent;
import models.gestionutilisateurs.User;
import services.gestionevenements.EventSponsorshipService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class SponsorshipFormController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ]{6,20}$");

    @FXML
    private TextField fullNameField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField amountField;
    @FXML
    private TextArea messageArea;

    private Long eventId;
    private Integer userId;
    private EventSponsorship editingTarget;

    @FXML
    private void initialize() {
        fullNameField.setTextFormatter(limitLengthFormatter(80));
        emailField.setTextFormatter(limitLengthFormatter(120));
        phoneField.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            return next.matches("\\+?[0-9 ]{0,20}") ? change : null;
        }));
        amountField.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            return next.matches("\\d{0,7}([.,]\\d{0,3})?") ? change : null;
        }));
        messageArea.setTextFormatter(limitLengthFormatter(500));
    }

    public void prepare(TravelEvent event, User user) {
        if (event != null) {
            this.eventId = event.getId();
        }
        this.userId = user != null ? user.getId() : null;

        if (user != null) {
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                fullNameField.setText(user.getUsername());
            }
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                emailField.setText(user.getEmail());
            }
            if (user.getPhone() != null && !user.getPhone().isBlank()) {
                phoneField.setText(user.getPhone());
            }
        }
        amountField.setText("100.000");
    }

    public EventSponsorship buildPayload() {
        String fullName = safe(fullNameField.getText());
        String email = safe(emailField.getText());
        String phone = safe(phoneField.getText());
        String amountRaw = safe(amountField.getText());

        if (fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (fullName.length() < 3) {
            throw new IllegalArgumentException("Full name must contain at least 3 characters.");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Email format is invalid.");
        }

        if (!phone.isBlank() && !PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("Phone format is invalid.");
        }

        if (amountRaw.isBlank()) {
            throw new IllegalArgumentException("Contribution amount is required.");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Contribution amount must be a valid number.");
        }
        if (amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Contribution amount must be at least 1.");
        }

        String message = safe(messageArea.getText());
        if (message.length() > 500) {
            throw new IllegalArgumentException("Message cannot exceed 500 characters.");
        }

        EventSponsorship payload = new EventSponsorship();
        if (editingTarget != null) {
            payload.setId(editingTarget.getId());
        }
        payload.setNom(fullName);
        payload.setEmail(email);
        payload.setTelephone(phone.isBlank() ? null : phone);
        payload.setMontantContribution(amount);
        payload.setMessage(message);
        payload.setStatut(EventSponsorshipService.STATUS_PENDING);
        payload.setIsPaid(false);
        payload.setSponsoredAt(LocalDateTime.now());
        payload.setEvenementId(eventId);
        payload.setUserId(userId);
        return payload;
    }

    public void setSponsorship(EventSponsorship source) {
        this.editingTarget = source;
        if (source == null) {
            return;
        }
        fullNameField.setText(safe(source.getNom()));
        emailField.setText(safe(source.getEmail()));
        phoneField.setText(safe(source.getTelephone()));
        amountField.setText(source.getMontantContribution() == null ? "" : source.getMontantContribution().toPlainString());
        messageArea.setText(safe(source.getMessage()));
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
