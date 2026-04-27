package controllers.gestionevenements;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import models.gestionevenements.TravelEvent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.UnaryOperator;

public class EventFormController {

    private static final int MIN_CAPACITY = 1;
    private static final int MAX_CAPACITY = 300;
    private static final int DEFAULT_CAPACITY = 50;

    @FXML
    private TextField titleField;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private TextField locationField;
    @FXML
    private DatePicker eventDatePicker;
    @FXML
    private Spinner<Integer> hourSpinner;
    @FXML
    private Spinner<Integer> minuteSpinner;
    @FXML
    private Slider capacitySlider;
    @FXML
    private Spinner<Integer> capacitySpinner;
    @FXML
    private Label capacityValueLabel;
    @FXML
    private TextField imagePathField;
    @FXML
    private ImageView imagePreview;

    @FXML
    private void initialize() {
        hourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 18));
        minuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0, 5));

        titleField.setTextFormatter(limitLengthFormatter(80));
        locationField.setTextFormatter(limitLengthFormatter(80));
        descriptionArea.setTextFormatter(limitLengthFormatter(500));
        imagePathField.setTextFormatter(limitLengthFormatter(255));

        SpinnerValueFactory.IntegerSpinnerValueFactory capacityFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_CAPACITY, MAX_CAPACITY, DEFAULT_CAPACITY);
        capacitySpinner.setValueFactory(capacityFactory);
        capacitySpinner.setEditable(true);

        capacitySlider.setMin(MIN_CAPACITY);
        capacitySlider.setMax(MAX_CAPACITY);
        capacitySlider.setValue(DEFAULT_CAPACITY);

        capacitySpinner.getEditor().setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            return next.matches("\\d{0,4}") ? change : null;
        }));

        capacitySpinner.getEditor().setOnAction(event -> commitCapacityEditorValue());
        capacitySpinner.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitCapacityEditorValue();
            }
        });
        capacitySpinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitCapacityEditorValue();
            }
        });

        capacitySpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            int clamped = clampCapacity(newValue);
            if (clamped != newValue) {
                capacitySpinner.getValueFactory().setValue(clamped);
                return;
            }
            if (Math.abs(capacitySlider.getValue() - clamped) > 0.5) {
                capacitySlider.setValue(clamped);
            }
        });

        capacitySlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            int rounded = clampCapacity(newValue.intValue());
            if (capacitySpinner.getValue() == null || !capacitySpinner.getValue().equals(rounded)) {
                capacitySpinner.getValueFactory().setValue(rounded);
            }
        });

        capacityValueLabel.textProperty().bind(Bindings.createStringBinding(
                () -> capacitySpinner.getValue() + " seats",
                capacitySpinner.valueProperty()));

        imagePathField.textProperty().addListener((obs, oldValue, newValue) -> imagePreview.setImage(resolveImage(newValue)));
        imagePreview.setImage(resolveImage(null));
        eventDatePicker.setValue(LocalDate.now().plusDays(2));
    }

    public void setEvent(TravelEvent source) {
        if (source == null) {
            resetForCreate();
            return;
        }

        titleField.setText(valueOrEmpty(source.getTitle()));
        descriptionArea.setText(valueOrEmpty(source.getDescription()));
        locationField.setText(valueOrEmpty(source.getLocation()));

        LocalDateTime when = source.getEventDate();
        if (when != null) {
            eventDatePicker.setValue(when.toLocalDate());
            hourSpinner.getValueFactory().setValue(when.getHour());
            minuteSpinner.getValueFactory().setValue(when.getMinute());
        }

        int capacity = source.getMaxParticipants() != null ? clampCapacity(source.getMaxParticipants()) : DEFAULT_CAPACITY;
        capacitySpinner.getValueFactory().setValue(capacity);
        capacitySlider.setValue(capacity);

        if (source.getImagePath() != null && !source.getImagePath().isBlank()) {
            imagePathField.setText(source.getImagePath().trim());
        }
    }

    public void resetForCreate() {
        titleField.clear();
        descriptionArea.clear();
        locationField.clear();
        eventDatePicker.setValue(LocalDate.now().plusDays(2));
        hourSpinner.getValueFactory().setValue(18);
        minuteSpinner.getValueFactory().setValue(0);
        capacitySpinner.getValueFactory().setValue(DEFAULT_CAPACITY);
        capacitySlider.setValue(DEFAULT_CAPACITY);
        imagePathField.clear();
        imagePreview.setImage(resolveImage(null));
    }

    public TravelEvent buildEvent(TravelEvent target) {
        commitCapacityEditorValue();

        TravelEvent event = target == null ? new TravelEvent() : target;

        String title = valueOrEmpty(titleField.getText()).trim();
        String location = valueOrEmpty(locationField.getText()).trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (title.length() < 3) {
            throw new IllegalArgumentException("Title must contain at least 3 characters.");
        }
        if (location.isBlank()) {
            throw new IllegalArgumentException("Location is required.");
        }
        if (location.length() < 2) {
            throw new IllegalArgumentException("Location must contain at least 2 characters.");
        }
        if (eventDatePicker.getValue() == null) {
            throw new IllegalArgumentException("Date is required.");
        }
        if (eventDatePicker.getValue().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Event date cannot be in the past.");
        }

        String description = valueOrEmpty(descriptionArea.getText()).trim();
        if (description.length() > 500) {
            throw new IllegalArgumentException("Description cannot exceed 500 characters.");
        }

        Integer hour = hourSpinner.getValue() == null ? 0 : hourSpinner.getValue();
        Integer minute = minuteSpinner.getValue() == null ? 0 : minuteSpinner.getValue();

        event.setTitle(title);
        event.setLocation(location);
        event.setDescription(description);
        event.setEventDate(LocalDateTime.of(eventDatePicker.getValue(), java.time.LocalTime.of(hour, minute)));
        int capacity = capacitySpinner.getValue() == null ? DEFAULT_CAPACITY : capacitySpinner.getValue();
        event.setMaxParticipants(clampCapacity(capacity));

        String imagePath = valueOrEmpty(imagePathField.getText()).trim();
        event.setImagePath(imagePath.isBlank() ? null : imagePath);
        return event;
    }

    @FXML
    private void onBrowseImage() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        File selected = chooser.showOpenDialog(titleField.getScene().getWindow());
        if (selected != null) {
            imagePathField.setText(selected.getAbsolutePath());
        }
    }

    private Image resolveImage(String imagePath) {
        Image fallback = loadFromClasspath("/images/welcome/featured-paris-eiffel.jpg");
        if (imagePath == null || imagePath.isBlank()) {
            return fallback;
        }

        String path = imagePath.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return new Image(path, 480, 270, true, true, true);
        }

        if (path.startsWith("/")) {
            Image classpathImage = loadFromClasspath(path);
            if (classpathImage != null) {
                return classpathImage;
            }
        }

        try {
            Path p = Path.of(path);
            if (Files.exists(p)) {
                return new Image(p.toUri().toString(), 480, 270, true, true, true);
            }
        } catch (Exception ignored) {
        }

        return fallback;
    }

    private Image loadFromClasspath(String path) {
        var url = EventFormController.class.getResource(path);
        if (url == null) {
            return null;
        }
        return new Image(url.toExternalForm(), 480, 270, true, true, true);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private int clampCapacity(int value) {
        return Math.min(MAX_CAPACITY, Math.max(MIN_CAPACITY, value));
    }

    private void commitCapacityEditorValue() {
        String text = valueOrEmpty(capacitySpinner.getEditor().getText()).trim();
        if (text.isBlank()) {
            capacitySpinner.getValueFactory().setValue(DEFAULT_CAPACITY);
            return;
        }
        try {
            int parsed = Integer.parseInt(text);
            capacitySpinner.getValueFactory().setValue(clampCapacity(parsed));
        } catch (NumberFormatException ex) {
            int current = capacitySpinner.getValue() == null ? DEFAULT_CAPACITY : capacitySpinner.getValue();
            capacitySpinner.getValueFactory().setValue(clampCapacity(current));
        }
    }

    private TextFormatter<String> limitLengthFormatter(int maxLength) {
        UnaryOperator<TextFormatter.Change> filter = change ->
                change.getControlNewText().length() <= maxLength ? change : null;
        return new TextFormatter<>(filter);
    }
}
