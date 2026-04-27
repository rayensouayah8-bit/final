package controllers.gestionevenements;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.fxml.FXML;
import javafx.util.Duration;
import models.gestionevenements.EventSponsorship;
import models.gestionevenements.TravelEvent;
import models.gestionutilisateurs.User;
import services.gestionevenements.EventParticipationService;
import services.gestionevenements.EventSponsorshipService;
import services.gestionevenements.TravelEventService;
import controllers.home.SignedInPageControllerBase;
import utils.NavigationManager;
import utils.StarfieldHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EventsSignedInController extends SignedInPageControllerBase {

    private enum ActiveEmbeddedView {
        LIST,
        EVENT_FORM,
        SPONSOR_FORM
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final double EVENT_CARD_WIDTH = 290;
    private static final double EVENT_CARD_HORIZONTAL_PADDING = 32;

    @FXML
    private Label userGreetingLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private Button createEventButton;
    @FXML
    private Label eventsStatusLabel;
    @FXML
    private TextField eventSearchField;
    @FXML
    private TilePane eventsTile;
    @FXML
    private VBox eventsListView;
    @FXML
    private VBox eventsFormView;
    @FXML
    private StackPane embeddedFormHost;
    @FXML
    private Label formTitleLabel;
    @FXML
    private Label formSubtitleLabel;
    @FXML
    private Label formStatusLabel;
    @FXML
    private Button formSubmitButton;
    @FXML
    private Pane signedInCosmicStarfieldPane;

    private final TravelEventService eventService = new TravelEventService();
    private final EventParticipationService participationService = new EventParticipationService();
    private final EventSponsorshipService sponsorshipService = new EventSponsorshipService();
    private List<TravelEvent> events = new ArrayList<>();
    private EventFormController activeEventFormController;
    private SponsorshipFormController activeSponsorshipFormController;
    private TravelEvent activeEventTarget;
    private TravelEvent activeSponsorEvent;
    private ActiveEmbeddedView activeView = ActiveEmbeddedView.LIST;

    @FXML
    private void initialize() {
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            nav.showLogin();
            return;
        }
        initSignedInSidebar();
        StarfieldHelper.populate(signedInCosmicStarfieldPane);
        if (!hasEventsUi()) {
            return;
        }
        var currentUser = nav.sessionUser().orElse(null);
        if (currentUser != null) {
            String displayName = currentUser.getUsername() != null && !currentUser.getUsername().isBlank()
                    ? currentUser.getUsername()
                    : currentUser.getEmail();
            userGreetingLabel.setText("Welcome, " + displayName);
            roleLabel.setText(nav.canAccessAgencyAdminFeatures() ? "Agency admin" : "User");
        }

        reloadEvents();
        switchToListView(false);
    }

    private boolean hasEventsUi() {
        return userGreetingLabel != null
                && roleLabel != null
                && createEventButton != null
                && eventsStatusLabel != null
                && eventSearchField != null
                && eventsTile != null
                && eventsListView != null
                && eventsFormView != null
                && embeddedFormHost != null
                && formTitleLabel != null
                && formSubtitleLabel != null
                && formStatusLabel != null
                && formSubmitButton != null;
    }

    private void reloadEvents() {
        try {
            events = eventService.findAll();
            boolean inserted = ensureDemoEvents(events);
            if (inserted) {
                events = eventService.findAll();
            }
            events.sort(Comparator.comparing(TravelEvent::getEventDate).reversed());
            renderEvents(events);
        } catch (SQLException e) {
            showError("Chargement des evenements", e.getMessage());
        }
    }

    private boolean ensureDemoEvents(List<TravelEvent> existingEvents) {
        Integer userId = NavigationManager.getInstance().sessionUser().map(u -> u.getId()).orElse(null);
        if (userId == null) {
            return false;
        }

        Set<String> existingTitles = new HashSet<>();
        for (TravelEvent e : existingEvents) {
            if (e.getTitle() != null && !e.getTitle().isBlank()) {
                existingTitles.add(e.getTitle().trim().toLowerCase(Locale.ROOT));
            }
        }

        List<TravelEvent> demoEvents = List.of(
                buildDemoEvent(
                        "Paris Night Discovery",
                        "Evening city walk with rooftop dinner and local guide.",
                        "Paris",
                        LocalDateTime.now().plusDays(3).withHour(19).withMinute(30),
                        180,
                        "/images/welcome/featured-paris-eiffel.jpg",
                        userId
                ),
                buildDemoEvent(
                        "Lagoon Chill Weekend",
                        "Relaxing beach program with snorkeling and sunset cruise.",
                        "Maldives",
                        LocalDateTime.now().plusDays(7).withHour(10).withMinute(0),
                        240,
                        "/images/welcome/featured-maldives-beach.jpg",
                        userId
                ),
                buildDemoEvent(
                        "Aerial Escape Experience",
                        "Premium island hopping itinerary and photography stops.",
                        "Blue Lagoon",
                        LocalDateTime.now().plusDays(12).withHour(9).withMinute(45),
                        120,
                        "/images/welcome/hero-aerial-lagoon.jpg",
                        userId
                )
        );

        boolean insertedAny = false;
        for (TravelEvent event : demoEvents) {
            String normalizedTitle = event.getTitle().trim().toLowerCase(Locale.ROOT);
            if (existingTitles.contains(normalizedTitle)) {
                continue;
            }
            try {
                eventService.insert(event);
                insertedAny = true;
                existingTitles.add(normalizedTitle);
            } catch (SQLException ignored) {
                // Skip failing demo row, continue with remaining items.
            }
        }
        return insertedAny;
    }

    private TravelEvent buildDemoEvent(
            String title,
            String description,
            String location,
            LocalDateTime when,
            int maxParticipants,
            String imagePath,
            int createdByUserId
    ) {
        TravelEvent event = new TravelEvent();
        event.setTitle(title);
        event.setDescription(description);
        event.setLocation(location);
        event.setEventDate(when);
        event.setMaxParticipants(maxParticipants);
        event.setImagePath(imagePath);
        event.setCreatedByUserId(createdByUserId);
        return event;
    }

    private void renderEvents(List<TravelEvent> source) {
        eventsTile.getChildren().clear();
        for (TravelEvent event : source) {
            eventsTile.getChildren().add(buildEventCard(event));
        }
    }

    private VBox buildEventCard(TravelEvent event) {
        VBox card = new VBox(10);
        card.getStyleClass().add("event-card");
        card.setPrefWidth(EVENT_CARD_WIDTH);
        card.setMinWidth(EVENT_CARD_WIDTH);
        card.setMaxWidth(EVENT_CARD_WIDTH);

        StackPane imageShell = new StackPane();
        imageShell.getStyleClass().add("event-card-image-shell");
        ImageView image = new ImageView(resolveImage(event.getImagePath()));
        image.fitWidthProperty().bind(card.widthProperty().subtract(EVENT_CARD_HORIZONTAL_PADDING));
        image.setFitHeight(160);
        image.setPreserveRatio(false);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(card.widthProperty().subtract(EVENT_CARD_HORIZONTAL_PADDING));
        clip.setHeight(160);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        image.setClip(clip);
        imageShell.getChildren().add(image);

        Label title = new Label(safe(event.getTitle(), "Event"));
        title.getStyleClass().add("event-card-title");
        title.setWrapText(true);

        Label date = new Label("Date: " + formatDate(event.getEventDate()));
        date.getStyleClass().add("event-card-meta");

        Label location = new Label("Location: " + safe(event.getLocation(), "N/A"));
        location.getStyleClass().add("event-card-meta");

        Button sponsorBtn = new Button("Sponsor");
        sponsorBtn.getStyleClass().add("event-action-sponsor-inline");
        sponsorBtn.setOnAction(e -> onSponsor(event));

        int participants = 0;
        try {
            participants = participationService.countParticipants(event.getId());
        } catch (SQLException ignored) {
        }
        int capacity = Math.max(1, event.getMaxParticipants() != null ? event.getMaxParticipants() : 300);
        IntegerProperty participantsProperty = new SimpleIntegerProperty(participants);

        Label participantsLabel = new Label();
        participantsLabel.textProperty().bind(Bindings.createStringBinding(
            () -> participantsProperty.get() + "/" + capacity + " participants",
            participantsProperty));
        participantsLabel.getStyleClass().add("event-card-meta");

        HBox participationRow = new HBox(8);
        participationRow.getStyleClass().add("event-card-participation-row");
        participationRow.setAlignment(Pos.CENTER_LEFT);
        Region participationSpacer = new Region();
        HBox.setHgrow(participationSpacer, Priority.ALWAYS);
        participationRow.getChildren().addAll(participantsLabel, participationSpacer, sponsorBtn);

        ProgressBar participantsProgress = new ProgressBar();
        participantsProgress.getStyleClass().add("event-card-progress");
        participantsProgress.setMaxWidth(Double.MAX_VALUE);
        participantsProgress.progressProperty().bind(Bindings.createDoubleBinding(
            () -> Math.min(1.0, (double) participantsProperty.get() / capacity),
            participantsProperty));

        Label progressText = new Label();
        progressText.getStyleClass().add("event-card-progress-text");
        progressText.textProperty().bind(Bindings.createStringBinding(
            () -> (int) Math.round(Math.min(1.0, (double) participantsProperty.get() / capacity) * 100) + "%",
            participantsProperty));

        StackPane progressWrap = new StackPane(participantsProgress, progressText);
        progressWrap.getStyleClass().add("event-card-progress-wrap");

        Label desc = new Label(safe(event.getDescription(), "No description"));
        desc.getStyleClass().add("event-card-description");
        desc.setWrapText(true);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);
        actions.getStyleClass().add("event-card-actions");

        Button participateBtn = new Button("Participer");
        participateBtn.getStyleClass().add("event-action-primary");
        updateParticipationButtonState(participateBtn, event.getId());
        participateBtn.setOnAction(e -> onParticipate(event, participateBtn, participantsProperty, capacity));

        Button editBtn = new Button("Modifier");
        editBtn.getStyleClass().add("event-action-secondary");
        editBtn.setOnAction(e -> onEditEvent(event));

        Button deleteBtn = new Button("Supprimer");
        deleteBtn.getStyleClass().add("event-action-danger");
        deleteBtn.setOnAction(e -> onDeleteEvent(event));

        Integer currentUserId = NavigationManager.getInstance().sessionUser().map(u -> u.getId()).orElse(null);
        boolean isOwner = currentUserId != null && currentUserId.equals(event.getCreatedByUserId());

        actions.getChildren().add(participateBtn);
        if (isOwner) {
            actions.getChildren().add(editBtn);
            actions.getChildren().add(deleteBtn);
        }

        card.getChildren().addAll(imageShell, title, date, location, participationRow, progressWrap, desc, actions);
        VBox.setMargin(actions, new Insets(6, 0, 0, 0));
        return card;
    }

    private void onParticipate(TravelEvent event, Button participateBtn, IntegerProperty participantsProperty, int capacity) {
        Integer userId = NavigationManager.getInstance().sessionUser().map(u -> u.getId()).orElse(null);
        if (userId == null) {
            NavigationManager.getInstance().showLogin();
            return;
        }
        try {
            boolean isParticipating = participationService.isParticipating(event.getId(), userId);
            if (isParticipating) {
                participationService.cancelParticipation(event.getId(), userId);
            } else {
                int currentCount = participationService.countParticipants(event.getId());
                if (currentCount >= capacity) {
                    showError("Event full", "This event has reached its maximum capacity.");
                    return;
                }
                participationService.participate(event.getId(), userId);
            }
            updateParticipationButtonState(participateBtn, event.getId());
            int participants = participationService.countParticipants(event.getId());
            participantsProperty.set(participants);
        } catch (SQLException ex) {
            showError("Participation", ex.getMessage());
        }
    }

    private void updateParticipationButtonState(Button button, Long eventId) {
        Integer userId = NavigationManager.getInstance().sessionUser().map(u -> u.getId()).orElse(null);
        if (userId == null) {
            button.setText("Participer");
            return;
        }
        try {
            boolean isParticipating = participationService.isParticipating(eventId, userId);
            button.setText(isParticipating ? "Cancel" : "Join");
        } catch (SQLException ignored) {
            button.setText("Participer");
        }
    }

    @FXML
    private void onSearchEvents() {
        String query = eventSearchField.getText() == null ? "" : eventSearchField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            renderEvents(events);
            return;
        }
        List<TravelEvent> filtered = events.stream().filter(e ->
                safe(e.getTitle(), "").toLowerCase(Locale.ROOT).contains(query)
                        || safe(e.getLocation(), "").toLowerCase(Locale.ROOT).contains(query)
                        || safe(e.getDescription(), "").toLowerCase(Locale.ROOT).contains(query)
        ).toList();
        renderEvents(filtered);
    }

    @FXML
    private void onCreateEvent() {
        showEventFormEmbedded(null);
    }

    private void onEditEvent(TravelEvent event) {
        showEventFormEmbedded(event);
    }

    private void showEventFormEmbedded(TravelEvent source) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/user/components/event_form.fxml"));
            Node formContent = loader.load();
            activeEventFormController = loader.getController();
            activeSponsorshipFormController = null;
            activeEventTarget = source;
            activeSponsorEvent = null;

            activeEventFormController.setEvent(source);
            embeddedFormHost.getChildren().setAll(formContent);
            StackPane.setAlignment(formContent, Pos.TOP_CENTER);

            formTitleLabel.setText(source == null ? "Create Event" : "Edit Event");
            formSubtitleLabel.setText(source == null
                    ? "Add a new event without leaving this page"
                    : "Update event details in place");
            formSubmitButton.setText(source == null ? "Create" : "Save changes");

            clearStatus(formStatusLabel);
            switchToFormView(ActiveEmbeddedView.EVENT_FORM);
        } catch (IOException e) {
            showError("Form", "Unable to load event form: " + e.getMessage());
        }
    }

    private void onDeleteEvent(TravelEvent event) {
        try {
            eventService.delete(event.getId());
            reloadEvents();
            showStatus(eventsStatusLabel, "Event deleted.", false);
        } catch (SQLException e) {
            showError("Suppression", e.getMessage());
        }
    }

    private void onSponsor(TravelEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/user/components/sponsorship_form.fxml"));
            Node formContent = loader.load();
            activeSponsorshipFormController = loader.getController();
            activeEventFormController = null;
            activeSponsorEvent = event;
            activeEventTarget = null;

            User sessionUser = NavigationManager.getInstance().sessionUser().orElse(null);
            activeSponsorshipFormController.prepare(event, sessionUser);
            embeddedFormHost.getChildren().setAll(formContent);
            StackPane.setAlignment(formContent, Pos.TOP_CENTER);

            formTitleLabel.setText("Sponsor Event");
            formSubtitleLabel.setText(safe(event.getTitle(), "Event") + " - submit your sponsorship request");
            formSubmitButton.setText("Submit sponsorship");

            clearStatus(formStatusLabel);
            switchToFormView(ActiveEmbeddedView.SPONSOR_FORM);
        } catch (IOException e) {
            showError("Sponsor", "Unable to load sponsorship form: " + e.getMessage());
        }
    }

    @FXML
    private void onInlineSubmit() {
        if (activeView == ActiveEmbeddedView.EVENT_FORM && activeEventFormController != null) {
            try {
                TravelEvent event = activeEventFormController.buildEvent(activeEventTarget);
                if (activeEventTarget == null) {
                    Integer userId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
                    if (userId == null) {
                        throw new IllegalArgumentException("Invalid session.");
                    }
                    event.setCreatedByUserId(userId);
                    eventService.insert(event);
                    reloadEvents();
                    switchToListView(true);
                    showStatus(eventsStatusLabel, "Event created successfully.", false);
                } else {
                    eventService.update(event);
                    reloadEvents();
                    switchToListView(true);
                    showStatus(eventsStatusLabel, "Event updated successfully.", false);
                }
            } catch (IllegalArgumentException ex) {
                showStatus(formStatusLabel, ex.getMessage(), true);
            } catch (Exception ex) {
                showStatus(formStatusLabel, ex.getMessage(), true);
            }
            return;
        }

        if (activeView == ActiveEmbeddedView.SPONSOR_FORM && activeSponsorshipFormController != null) {
            try {
                EventSponsorship payload = activeSponsorshipFormController.buildPayload();
                payload.setEvenementId(activeSponsorEvent != null ? activeSponsorEvent.getId() : null);
                payload.setUserId(NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null));
                sponsorshipService.insert(payload);

                switchToListView(true);
                showStatus(eventsStatusLabel, "Thank you. Your sponsorship request is pending approval.", false);
            } catch (IllegalArgumentException ex) {
                showStatus(formStatusLabel, ex.getMessage(), true);
            } catch (SQLException ex) {
                showStatus(formStatusLabel, ex.getMessage(), true);
            }
        }
    }

    @FXML
    private void onInlineCancel() {
        switchToListView(true);
    }

    private void switchToFormView(ActiveEmbeddedView view) {
        activeView = view;
        clearStatus(eventsStatusLabel);
        createEventButton.setDisable(true);
        createEventButton.setVisible(false);
        createEventButton.setManaged(false);

        eventsListView.setVisible(false);
        eventsListView.setManaged(false);
        eventsFormView.setVisible(true);
        eventsFormView.setManaged(true);
        animateEntry(eventsFormView);
    }

    private void switchToListView(boolean animate) {
        activeView = ActiveEmbeddedView.LIST;
        activeEventFormController = null;
        activeSponsorshipFormController = null;
        activeEventTarget = null;
        activeSponsorEvent = null;

        createEventButton.setDisable(false);
        createEventButton.setVisible(true);
        createEventButton.setManaged(true);

        clearStatus(formStatusLabel);
        embeddedFormHost.getChildren().clear();

        eventsFormView.setVisible(false);
        eventsFormView.setManaged(false);
        eventsListView.setVisible(true);
        eventsListView.setManaged(true);

        if (animate) {
            animateEntry(eventsListView);
        }
    }

    private void animateEntry(Node node) {
        node.setOpacity(0);
        node.setTranslateY(10);

        FadeTransition fade = new FadeTransition(Duration.millis(180), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(180), node);
        slide.setFromY(10);
        slide.setToY(0);

        new ParallelTransition(fade, slide).play();
    }

    private void clearStatus(Label statusLabel) {
        statusLabel.setText("");
        statusLabel.getStyleClass().removeAll("status-error", "status-success");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    private void showStatus(Label statusLabel, String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-error", "status-success");
        statusLabel.getStyleClass().add(error ? "status-error" : "status-success");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private Image resolveImage(String imagePath) {
        Image fallback = loadFromClasspath("/images/welcome/featured-paris-eiffel.jpg");
        if (imagePath == null || imagePath.isBlank()) {
            return fallback;
        }

        String p = imagePath.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) {
            return new Image(p, 900, 540, false, true, true);
        }

        if (p.startsWith("/")) {
            Image classpath = loadFromClasspath(p);
            if (classpath != null) {
                return classpath;
            }
        }

        try {
            Path path = Path.of(p);
            if (Files.exists(path)) {
                return new Image(path.toUri().toString(), 900, 540, false, true, true);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private Image loadFromClasspath(String path) {
        var url = EventsSignedInController.class.getResource(path);
        if (url == null) {
            return null;
        }
        return new Image(url.toExternalForm(), 900, 540, false, true, true);
    }

    private String formatDate(LocalDateTime date) {
        if (date == null) {
            return "TBD";
        }
        return DATE_FMT.format(date);
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void showError(String title, String message) {
        Label target = activeView == ActiveEmbeddedView.LIST ? eventsStatusLabel : formStatusLabel;
        showStatus(target, title + ": " + message, true);
    }

}
