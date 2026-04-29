package controllers.gestionevenements;

import controllers.home.SignedInPageControllerBase;
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
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.fxml.FXML;
import javafx.util.Duration;
import models.gestionevenements.EventParticipation;
import models.gestionevenements.EventSponsorship;
import models.gestionevenements.TravelEvent;
import models.gestionutilisateurs.User;
import services.gestionevenements.EventParticipationService;
import services.gestionevenements.EventSponsorshipService;
import services.gestionevenements.EventLikeService;
import services.gestionevenements.TravelEventService;
import services.gestionmessagerie.ConversationService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EventsSignedInController extends SignedInPageControllerBase {

    private enum ActiveEmbeddedView {
        LIST,
        EVENT_FORM,
        SPONSOR_FORM,
        PARTICIPATION_FORM
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
    private Button allEventsButton;
    @FXML
    private Button favoritesButton;
    @FXML
    private Button mesEventsButton;
    @FXML
    private Button myRequestsButton;
    @FXML
    private Button mySponsorsButton;
    @FXML
    private Label eventsStatusLabel;
    @FXML
    private TextField eventSearchField;
    @FXML
    private TilePane eventsTile;
    @FXML
    private VBox eventsListView;
    @FXML
    private VBox ownerRequestsPanel;
    @FXML
    private VBox ownerRequestsList;
    @FXML
    private Label ownerRequestsStatusLabel;
    @FXML
    private Label ownerRequestsTitleLabel;
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
    private final EventLikeService eventLikeService = new EventLikeService();
    private final ConversationService conversationService = new ConversationService();
    private List<TravelEvent> events = new ArrayList<>();
    private EventFormController activeEventFormController;
    private SponsorshipFormController activeSponsorshipFormController;
    private ParticipationJoinFormController activeParticipationFormController;
    private TravelEvent activeEventTarget;
    private TravelEvent activeSponsorEvent;
    private EventSponsorship activeSponsorTarget;
    private TravelEvent activeParticipationEvent;
    private ActiveEmbeddedView activeView = ActiveEmbeddedView.LIST;
    private boolean mesEventsMode;
    private boolean favoritesMode;
    private boolean requestStatusMode;
    private boolean sponsorsMode;
    private final Map<Long, String> sponsorshipStatusByEventId = new HashMap<>();

    @FXML
    private void initialize() {
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            nav.showLogin();
            return;
        }
        initSignedInSidebar();
        if (signedInCosmicStarfieldPane != null) {
            StarfieldHelper.populate(signedInCosmicStarfieldPane);
        }

        var currentUser = nav.sessionUser().orElse(null);
        if (currentUser != null) {
            String displayName = currentUser.getUsername() != null && !currentUser.getUsername().isBlank()
                    ? currentUser.getUsername()
                    : currentUser.getEmail();
            userGreetingLabel.setText("Welcome, " + displayName);
            roleLabel.setText(nav.canAccessAgencyAdminFeatures() ? "Agency admin" : "User");
        }

        reloadEvents(false, false, false, false);
        switchToListView(false);
    }

    private void reloadEvents(boolean onlyMine, boolean onlyFavorites, boolean onlyMyRequests, boolean onlyMySponsors) {
        try {
            Integer sessionUserId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
            sponsorshipStatusByEventId.clear();
            if (onlyMine && sessionUserId != null) {
                events = eventService.findByCreator(sessionUserId);
            } else if ((onlyMyRequests || onlyMySponsors) && sessionUserId != null) {
                events = eventService.findAll();
            } else {
                events = eventService.findApproved();
            }
            if (!onlyFavorites && !onlyMyRequests && !onlyMySponsors) {
                boolean inserted = ensureDemoEvents(events);
                if (inserted) {
                    if (onlyMine && sessionUserId != null) {
                        events = eventService.findByCreator(sessionUserId);
                    } else {
                        events = eventService.findApproved();
                    }
                }
            }
            if (onlyFavorites) {
                if (sessionUserId == null) {
                    events = List.of();
                } else {
                    Set<Long> likedIds = eventLikeService.findLikedEventIdsByUser(sessionUserId);
                    events = new ArrayList<>(events.stream()
                            .filter(e -> e.getId() != null && likedIds.contains(e.getId()))
                            .toList());
                }
            }
            if (onlyMyRequests) {
                if (sessionUserId == null) {
                    events = List.of();
                } else {
                    Set<Long> requestedIds = new HashSet<>();
                    for (EventParticipation p : participationService.findByUser(sessionUserId)) {
                        if (p.getEventId() != null) {
                            requestedIds.add(p.getEventId());
                        }
                    }
                    events = new ArrayList<>(events.stream()
                            .filter(e -> e.getId() != null && requestedIds.contains(e.getId()))
                            .toList());
                }
            }
            if (onlyMySponsors) {
                if (sessionUserId == null) {
                    events = List.of();
                } else {
                    Set<Long> sponsoredEventIds = new HashSet<>();
                    for (EventSponsorship s : sponsorshipService.findByUser(sessionUserId)) {
                        if (s.getEvenementId() != null) {
                            sponsoredEventIds.add(s.getEvenementId());
                            sponsorshipStatusByEventId.putIfAbsent(s.getEvenementId(), formatSponsorStatus(s.getStatut()));
                        }
                    }
                    events = new ArrayList<>(events.stream()
                            .filter(e -> e.getId() != null && sponsoredEventIds.contains(e.getId()))
                            .toList());
                }
            }
            events.sort(Comparator.comparing(TravelEvent::getEventDate).reversed());
            renderEvents(events);
        } catch (SQLException e) {
            showError("Chargement des evenements", e.getMessage());
        }
    }

    private void reloadCurrentEvents() {
        reloadEvents(mesEventsMode, favoritesMode, requestStatusMode, sponsorsMode);
        if (mesEventsMode) {
            refreshOwnerRequestsPanel();
        }
        if (sponsorsMode) {
            refreshMySponsorsPanel();
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
        event.setStatus(TravelEventService.STATUS_APPROVED);
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

        Label statusBadge = new Label("Status: " + safe(event.getStatus(), TravelEventService.STATUS_PENDING));
        statusBadge.getStyleClass().add("event-card-meta");

        Button sponsorBtn = new Button("Sponsor");
        sponsorBtn.getStyleClass().add("event-action-sponsor-inline");
        sponsorBtn.setOnAction(e -> onSponsor(event));
        Label mySponsorStatusLabel = new Label();
        mySponsorStatusLabel.getStyleClass().add("event-card-meta");
        mySponsorStatusLabel.setVisible(false);
        mySponsorStatusLabel.setManaged(false);


        int approvedSponsors = 0;
        int likes = 0;
        try {
            approvedSponsors = sponsorshipService.countApprovedByEvent(event.getId());
        } catch (SQLException ignored) {
        }
        try {
            likes = eventLikeService.countByEvent(event.getId());
        } catch (SQLException ignored) {
        }
        Label sponsorsLabel = new Label("Sponsors: " + approvedSponsors);
        sponsorsLabel.getStyleClass().add("event-card-meta");
        IntegerProperty likesProperty = new SimpleIntegerProperty(likes);
        Label likesLabel = new Label();
        likesLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Likes: " + likesProperty.get(),
                likesProperty));
        likesLabel.getStyleClass().add("event-card-meta");

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

        FlowPane actions = new FlowPane();
        actions.setHgap(8);
        actions.setVgap(8);
        actions.setPrefWrapLength(EVENT_CARD_WIDTH - EVENT_CARD_HORIZONTAL_PADDING - 8);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("event-card-actions");

        Button participateBtn = new Button("Participer");
        participateBtn.getStyleClass().add("event-action-join");
        updateParticipationButtonState(participateBtn, event.getId());
        participateBtn.setOnAction(e -> onParticipate(event, participateBtn, participantsProperty, capacity));

        Button detailsBtn = new Button("Details");
        detailsBtn.getStyleClass().add("event-action-secondary");
        detailsBtn.setOnAction(e -> onDetails(event));

        Button contactAgencyBtn = new Button("Contact Agency");
        contactAgencyBtn.getStyleClass().add("event-action-secondary");
        contactAgencyBtn.setOnAction(e -> onContactAgency(event));

        Button likeBtn = new Button();
        likeBtn.getStyleClass().add("event-action-like");
        updateLikeButtonState(likeBtn, event.getId());
        likeBtn.setOnAction(e -> onLikeEvent(event, likeBtn, likesProperty));

        Button editBtn = new Button("Modifier");
        editBtn.getStyleClass().add("event-action-secondary");
        editBtn.setOnAction(e -> onEditEvent(event));

        Button deleteBtn = new Button("Supprimer");
        deleteBtn.getStyleClass().add("event-action-danger");
        deleteBtn.setOnAction(e -> onDeleteEvent(event));

        Integer currentUserId = NavigationManager.getInstance().sessionUser().map(u -> u.getId()).orElse(null);
        boolean isOwner = currentUserId != null && currentUserId.equals(event.getCreatedByUserId());
        if (currentUserId != null) {
            try {
                var existingSponsorship = sponsorshipService.findLatestByEventAndUser(event.getId(), currentUserId);
                if (existingSponsorship.isPresent()) {
                    sponsorBtn.setText("Sponsored");
                    sponsorBtn.setDisable(true);
                    String statusText = formatSponsorStatus(existingSponsorship.get().getStatut());
                    mySponsorStatusLabel.setText("My sponsorship: " + statusText);
                    mySponsorStatusLabel.setVisible(true);
                    mySponsorStatusLabel.setManaged(true);
                }
            } catch (SQLException ignored) {
            }
        }
        if (!mySponsorStatusLabel.isVisible() && sponsorsMode && event.getId() != null) {
            String modeStatus = sponsorshipStatusByEventId.get(event.getId());
            if (modeStatus != null && !modeStatus.isBlank()) {
                mySponsorStatusLabel.setText("My sponsorship: " + modeStatus);
                mySponsorStatusLabel.setVisible(true);
                mySponsorStatusLabel.setManaged(true);
            }
        }

        actions.getChildren().add(participateBtn);
        actions.getChildren().add(likeBtn);
        actions.getChildren().add(detailsBtn);
        if (!isOwner) {
            actions.getChildren().add(contactAgencyBtn);
        }
        if (mesEventsMode && isOwner) {
            actions.getChildren().add(editBtn);
            actions.getChildren().add(deleteBtn);
        }

        card.getChildren().addAll(imageShell, title, date, location, statusBadge, sponsorsLabel, likesLabel, mySponsorStatusLabel, participationRow, progressWrap, desc, actions);
        VBox.setMargin(actions, new Insets(6, 0, 0, 0));
        return card;
    }

    private void onLikeEvent(TravelEvent event, Button likeBtn, IntegerProperty likesProperty) {
        Integer userId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
        if (userId == null) {
            NavigationManager.getInstance().showLogin();
            return;
        }
        try {
            boolean nowLiked = eventLikeService.toggleLike(event.getId(), userId);
            int likes = eventLikeService.countByEvent(event.getId());
            likesProperty.set(likes);
            updateLikeButtonState(likeBtn, event.getId());
            if (favoritesMode && !nowLiked) {
                reloadCurrentEvents();
            }
            showStatus(eventsStatusLabel, nowLiked ? "You liked this event." : "Like removed.", false);
        } catch (SQLException ex) {
            showError("Like", ex.getMessage());
        }
    }

    private void updateLikeButtonState(Button button, Long eventId) {
        Integer userId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
        if (userId == null) {
            button.setText("Like");
            return;
        }
        try {
            boolean hasLiked = eventLikeService.hasLiked(eventId, userId);
            button.setText(hasLiked ? "Liked" : "Like");
        } catch (SQLException ignored) {
            button.setText("Like");
        }
    }

    private void onParticipate(TravelEvent event, Button participateBtn, IntegerProperty participantsProperty, int capacity) {
        Integer userId = NavigationManager.getInstance().sessionUser().map(u -> u.getId()).orElse(null);
        if (userId == null) {
            NavigationManager.getInstance().showLogin();
            return;
        }
        try {
            var currentRequest = participationService.findByEventAndUser(event.getId(), userId);
            if (currentRequest.isPresent()) {
                String status = currentRequest.get().getStatus() == null ? "" : currentRequest.get().getStatus();
                if (EventParticipationService.STATUS_APPROVED.equalsIgnoreCase(status)) {
                    showStatus(eventsStatusLabel, "You are already approved for this event.", false);
                    return;
                }
                if (EventParticipationService.STATUS_PENDING.equalsIgnoreCase(status)) {
                    showStatus(eventsStatusLabel, "Your participation request is pending owner approval.", false);
                    return;
                }
            }

            int currentCount = participationService.countParticipants(event.getId());
            if (currentCount >= capacity) {
                showError("Event full", "This event has reached its maximum capacity.");
                return;
            }

            showParticipationFormEmbedded(event);
        } catch (SQLException ex) {
            showError("Participation", ex.getMessage());
        }
    }

    private void showParticipationFormEmbedded(TravelEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/user/components/participation_form.fxml"));
            Node formContent = loader.load();
            activeParticipationFormController = loader.getController();
            activeSponsorshipFormController = null;
            activeEventFormController = null;
            activeParticipationEvent = event;
            activeSponsorEvent = null;
            activeEventTarget = null;

            User sessionUser = NavigationManager.getInstance().sessionUser().orElse(null);
            activeParticipationFormController.prepare(event, sessionUser);
            embeddedFormHost.getChildren().setAll(formContent);
            StackPane.setAlignment(formContent, Pos.TOP_CENTER);

            formTitleLabel.setText("Join Event");
            formSubtitleLabel.setText(safe(event.getTitle(), "Event") + " - submit your participation request");
            formSubmitButton.setText("Submit join request");

            clearStatus(formStatusLabel);
            switchToFormView(ActiveEmbeddedView.PARTICIPATION_FORM);
        } catch (IOException e) {
            showError("Participation", "Unable to load join form: " + e.getMessage());
        }
    }

    private void updateParticipationButtonState(Button button, Long eventId) {
        button.getStyleClass().removeAll("event-action-join", "event-action-secondary", "event-action-danger");
        Integer userId = NavigationManager.getInstance().sessionUser().map(u -> u.getId()).orElse(null);
        if (userId == null) {
            button.setText("Join");
            button.getStyleClass().add("event-action-join");
            return;
        }
        try {
            var request = participationService.findByEventAndUser(eventId, userId);
            if (request.isEmpty()) {
                button.setText("Join");
                button.getStyleClass().add("event-action-join");
                return;
            }
            String status = request.get().getStatus() == null ? "" : request.get().getStatus();
            if (EventParticipationService.STATUS_APPROVED.equalsIgnoreCase(status)) {
                button.setText("Accepted");
                button.getStyleClass().add("event-action-join");
                return;
            }
            if (EventParticipationService.STATUS_REJECTED.equalsIgnoreCase(status)) {
                button.setText("Refused");
                button.getStyleClass().add("event-action-danger");
                return;
            }
            button.setText("Pending");
            button.getStyleClass().add("event-action-secondary");
        } catch (SQLException ignored) {
            button.setText("Join");
            button.getStyleClass().add("event-action-join");
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
        Integer currentUserId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
        if (currentUserId == null || event.getCreatedByUserId() == null || !event.getCreatedByUserId().equals(currentUserId)) {
            showError("Access", "Only the event owner can edit this event.");
            return;
        }
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
        Integer currentUserId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
        if (currentUserId == null || event.getCreatedByUserId() == null || !event.getCreatedByUserId().equals(currentUserId)) {
            showError("Access", "Only the event owner can delete this event.");
            return;
        }
        try {
            eventService.deleteByOwner(event.getId(), currentUserId);
            reloadCurrentEvents();
            showStatus(eventsStatusLabel, "Event deleted.", false);
        } catch (SQLException | IllegalArgumentException e) {
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
            activeSponsorTarget = null;
            activeEventTarget = null;

            User sessionUser = NavigationManager.getInstance().sessionUser().orElse(null);
            activeSponsorshipFormController.prepare(event, sessionUser);
            Integer userId = sessionUser != null ? sessionUser.getId() : null;
            if (userId != null) {
                var existing = sponsorshipService.findLatestByEventAndUser(event.getId(), userId);
                if (existing.isPresent()) {
                    activeSponsorTarget = existing.get();
                    activeSponsorshipFormController.setSponsorship(existing.get());
                }
            }
            embeddedFormHost.getChildren().setAll(formContent);
            StackPane.setAlignment(formContent, Pos.TOP_CENTER);

            formTitleLabel.setText("Sponsor Event");
            formSubtitleLabel.setText(safe(event.getTitle(), "Event") + " - submit your sponsorship request");
            formSubmitButton.setText(activeSponsorTarget == null ? "Submit sponsorship" : "Update sponsorship");

            clearStatus(formStatusLabel);
            switchToFormView(ActiveEmbeddedView.SPONSOR_FORM);
        } catch (IOException e) {
            showError("Sponsor", "Unable to load sponsorship form: " + e.getMessage());
        } catch (SQLException e) {
            showError("Sponsor", e.getMessage());
        }
    }

    private void onDeleteSponsor(TravelEvent event) {
        Integer userId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
        if (userId == null) {
            NavigationManager.getInstance().showLogin();
            return;
        }
        try {
            sponsorshipService.deleteByEventAndUser(event.getId(), userId);
            reloadCurrentEvents();
            showStatus(eventsStatusLabel, "Sponsorship request deleted.", false);
        } catch (SQLException | IllegalArgumentException e) {
            showError("Sponsor", e.getMessage());
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
                    event.setStatus(TravelEventService.STATUS_PENDING);
                    eventService.insert(event);
                    reloadCurrentEvents();
                    switchToListView(true);
                    showStatus(eventsStatusLabel, "Event created and sent for admin approval.", false);
                } else {
                    Integer currentUserId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
                    if (currentUserId == null) {
                        throw new IllegalArgumentException("Invalid session.");
                    }
                    eventService.updateByOwner(event, currentUserId);
                    reloadCurrentEvents();
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
                if (payload.getId() == null) {
                    sponsorshipService.insert(payload);
                } else {
                    payload.setStatut(EventSponsorshipService.STATUS_PENDING);
                    sponsorshipService.update(payload);
                }

                switchToListView(true);
                reloadCurrentEvents();
                showStatus(eventsStatusLabel, "Thank you. Your sponsorship request is pending approval.", false);
            } catch (IllegalArgumentException ex) {
                showStatus(formStatusLabel, ex.getMessage(), true);
            } catch (SQLException ex) {
                showStatus(formStatusLabel, ex.getMessage(), true);
            }
            return;
        }

        if (activeView == ActiveEmbeddedView.PARTICIPATION_FORM && activeParticipationFormController != null) {
            try {
                EventParticipation payload = activeParticipationFormController.buildPayload();
                payload.setEventId(activeParticipationEvent != null ? activeParticipationEvent.getId() : null);
                payload.setUserId(NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null));
                participationService.participate(payload);

                switchToListView(true);
                reloadCurrentEvents();
                showStatus(eventsStatusLabel, "Participation request submitted.", false);
            } catch (IllegalArgumentException ex) {
                showStatus(formStatusLabel, ex.getMessage(), true);
            } catch (SQLException ex) {
                showStatus(formStatusLabel, ex.getMessage(), true);
            }
        }
    }

    @FXML
    private void onAllEvents() {
        mesEventsMode = false;
        favoritesMode = false;
        requestStatusMode = false;
        sponsorsMode = false;
        setOwnerRequestsPanelVisible(false);
        reloadCurrentEvents();
        showStatus(eventsStatusLabel, "Showing all available events.", false);
    }

    @FXML
    private void onFavorites() {
        mesEventsMode = false;
        requestStatusMode = false;
        favoritesMode = true;
        sponsorsMode = false;
        setOwnerRequestsPanelVisible(false);
        reloadCurrentEvents();
        showStatus(eventsStatusLabel, "Showing events you liked.", false);
    }

    @FXML
    private void onMyRequests() {
        mesEventsMode = false;
        favoritesMode = false;
        requestStatusMode = true;
        sponsorsMode = false;
        setOwnerRequestsPanelVisible(false);
        reloadCurrentEvents();
        showStatus(eventsStatusLabel, "Showing your join request statuses.", false);
    }

    @FXML
    private void onMySponsors() {
        mesEventsMode = false;
        favoritesMode = false;
        requestStatusMode = false;
        sponsorsMode = true;
        setOwnerRequestsPanelVisible(true);
        if (ownerRequestsTitleLabel != null) {
            ownerRequestsTitleLabel.setText("My Sponsorships");
        }
        reloadCurrentEvents();
        refreshMySponsorsPanel();
        showStatus(eventsStatusLabel, "Showing your sponsorship requests.", false);
    }

    @FXML
    private void onMesEvents() {
        mesEventsMode = true;
        favoritesMode = false;
        requestStatusMode = false;
        sponsorsMode = false;
        setOwnerRequestsPanelVisible(true);
        if (ownerRequestsTitleLabel != null) {
            ownerRequestsTitleLabel.setText("Pending Requests");
        }
        reloadCurrentEvents();
        refreshOwnerRequestsPanel();
        showStatus(eventsStatusLabel, "Showing your events. You can modify/delete and review join requests.", false);
    }

    private void refreshMySponsorsPanel() {
        if (ownerRequestsList == null) {
            return;
        }
        ownerRequestsList.getChildren().clear();
        Integer userId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
        if (userId == null) {
            return;
        }
        try {
            List<EventSponsorship> mySponsorships = sponsorshipService.findByUser(userId);
            if (mySponsorships.isEmpty()) {
                setOwnerRequestsStatus("No sponsorship requests yet.", false);
                return;
            }

            Map<Long, TravelEvent> eventById = new HashMap<>();
            for (TravelEvent event : events) {
                if (event.getId() != null) {
                    eventById.put(event.getId(), event);
                }
            }

            int count = 0;
            Set<Long> seenEventIds = new HashSet<>();
            for (EventSponsorship sponsorship : mySponsorships) {
                Long eventId = sponsorship.getEvenementId();
                if (eventId == null || seenEventIds.contains(eventId)) {
                    continue;
                }
                TravelEvent targetEvent = eventById.get(eventId);
                if (targetEvent == null) {
                    targetEvent = eventService.get(eventId).orElse(null);
                }
                if (targetEvent == null) {
                    continue;
                }
                ownerRequestsList.getChildren().add(buildMySponsorRow(targetEvent, sponsorship));
                seenEventIds.add(eventId);
                count++;
            }

            if (count == 0) {
                setOwnerRequestsStatus("No sponsorship requests yet.", false);
            } else {
                setOwnerRequestsStatus("My sponsorship requests: " + count, false);
            }
        } catch (SQLException ex) {
            setOwnerRequestsStatus("Unable to load sponsorships: " + ex.getMessage(), true);
        }
    }

    private HBox buildMySponsorRow(TravelEvent event, EventSponsorship sponsorship) {
        Label info = new Label(safe(event.getTitle(), "Event") + " • " + formatSponsorStatus(sponsorship.getStatut()));
        info.getStyleClass().add("event-card-meta");
        info.setMaxWidth(165);

        Button edit = new Button("Edit");
        edit.getStyleClass().add("event-action-secondary");
        edit.getStyleClass().add("event-side-action-btn");
        edit.setOnAction(e -> onSponsor(event));

        Button delete = new Button("Cancel");
        delete.getStyleClass().add("event-action-danger");
        delete.getStyleClass().add("event-side-action-btn");
        delete.setOnAction(e -> onDeleteSponsor(event));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, info, spacer, edit, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("event-card-actions");
        return row;
    }

    private void refreshOwnerRequestsPanel() {
        if (ownerRequestsList == null) {
            return;
        }
        ownerRequestsList.getChildren().clear();
        Integer ownerId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
        if (ownerId == null) {
            return;
        }

        int count = 0;
        for (TravelEvent event : eventServiceSafeMine(ownerId)) {
            try {
                for (EventParticipation participation : participationService.findPendingByEventOwner(event.getId(), ownerId)) {
                    ownerRequestsList.getChildren().add(buildParticipationRequestRow(event, participation, ownerId));
                    count++;
                }
                for (EventSponsorship sponsorship : sponsorshipService.findPendingByEventOwner(event.getId(), ownerId)) {
                    ownerRequestsList.getChildren().add(buildSponsorshipRequestRow(event, sponsorship, ownerId));
                    count++;
                }
            } catch (SQLException ex) {
                setOwnerRequestsStatus("Unable to load requests: " + ex.getMessage(), true);
                return;
            }
        }

        if (count == 0) {
            setOwnerRequestsStatus("No pending requests.", false);
        } else {
            setOwnerRequestsStatus("Pending requests: " + count, false);
        }
    }

    private HBox buildParticipationRequestRow(TravelEvent event, EventParticipation participation, Integer ownerId) {
        Label info = new Label("Join • " + safe(event.getTitle(), "Event") + " • User #" + participation.getUserId());
        info.getStyleClass().add("event-card-meta");
        info.setMaxWidth(165);

        Button approve = new Button("Approve");
        approve.getStyleClass().add("event-action-primary");
        approve.getStyleClass().add("event-side-action-btn");
        approve.setOnAction(e -> {
            try {
                participationService.approveParticipation(participation.getId(), ownerId);
                reloadCurrentEvents();
                refreshOwnerRequestsPanel();
            } catch (SQLException ex) {
                showError("Requests", ex.getMessage());
            }
        });

        Button reject = new Button("Refuse");
        reject.getStyleClass().add("event-action-danger");
        reject.getStyleClass().add("event-side-action-btn");
        reject.setOnAction(e -> {
            try {
                participationService.rejectParticipation(participation.getId(), ownerId);
                reloadCurrentEvents();
                refreshOwnerRequestsPanel();
            } catch (SQLException ex) {
                showError("Requests", ex.getMessage());
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, info, spacer, approve, reject);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("event-card-actions");
        return row;
    }

    private HBox buildSponsorshipRequestRow(TravelEvent event, EventSponsorship sponsorship, Integer ownerId) {
        Label info = new Label("Sponsor • " + safe(event.getTitle(), "Event") + " • " + safe(sponsorship.getNom(), "Unknown"));
        info.getStyleClass().add("event-card-meta");
        info.setMaxWidth(165);

        Button approve = new Button("Approve");
        approve.getStyleClass().add("event-action-primary");
        approve.getStyleClass().add("event-side-action-btn");
        approve.setOnAction(e -> {
            try {
                sponsorshipService.approveSponsorship(sponsorship.getId(), ownerId);
                reloadCurrentEvents();
                refreshOwnerRequestsPanel();
            } catch (SQLException ex) {
                showError("Requests", ex.getMessage());
            }
        });

        Button reject = new Button("Refuse");
        reject.getStyleClass().add("event-action-danger");
        reject.getStyleClass().add("event-side-action-btn");
        reject.setOnAction(e -> {
            try {
                sponsorshipService.rejectSponsorship(sponsorship.getId(), ownerId);
                reloadCurrentEvents();
                refreshOwnerRequestsPanel();
            } catch (SQLException ex) {
                showError("Requests", ex.getMessage());
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, info, spacer, approve, reject);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("event-card-actions");
        return row;
    }

    private void setOwnerRequestsPanelVisible(boolean visible) {
        if (ownerRequestsPanel == null) {
            return;
        }
        ownerRequestsPanel.setVisible(visible);
        ownerRequestsPanel.setManaged(visible);
    }

    private void setOwnerRequestsStatus(String message, boolean error) {
        if (ownerRequestsStatusLabel == null) {
            return;
        }
        if (message == null || message.isBlank()) {
            ownerRequestsStatusLabel.setText("");
            ownerRequestsStatusLabel.setVisible(false);
            ownerRequestsStatusLabel.setManaged(false);
            ownerRequestsStatusLabel.getStyleClass().removeAll("status-error", "status-success");
            return;
        }
        ownerRequestsStatusLabel.setText(message);
        ownerRequestsStatusLabel.setVisible(true);
        ownerRequestsStatusLabel.setManaged(true);
        ownerRequestsStatusLabel.getStyleClass().removeAll("status-error", "status-success");
        ownerRequestsStatusLabel.getStyleClass().add(error ? "status-error" : "status-success");
    }

    private List<TravelEvent> eventServiceSafeMine(Integer ownerId) {
        try {
            return eventService.findByCreator(ownerId);
        } catch (SQLException e) {
            showError("My events", e.getMessage());
            return List.of();
        }
    }

    private void onDetails(TravelEvent event) {
        int likes = 0;
        int comments = 0;
        try {
            likes = eventLikeService.countByEvent(event.getId());
            comments = countByEvent("event_comment", event.getId());
        } catch (SQLException ignored) {
        }
        Alert details = new Alert(Alert.AlertType.INFORMATION);
        details.setTitle("Event Details");
        details.setHeaderText(safe(event.getTitle(), "Event"));
        details.setContentText("Likes: " + likes + "\nComments: " + comments + "\nLocation: " + safe(event.getLocation(), "N/A") + "\nDate: " + formatDate(event.getEventDate()));
        details.showAndWait();
    }

    private void onContactAgency(TravelEvent event) {
        Integer travelerUserId = NavigationManager.getInstance().sessionUser().map(User::getId).orElse(null);
        if (travelerUserId == null) {
            NavigationManager.getInstance().showLogin();
            return;
        }
        try {
            Long conversationId = conversationService.ensureConversationForEvent(travelerUserId, event);
            NavigationManager.getInstance().setPendingConversationId(conversationId);
            NavigationManager.getInstance().showSignedInMessages();
        } catch (SQLException | IllegalArgumentException ex) {
            showError("Messages", ex.getMessage());
        }
    }

    private int countByEvent(String table, Long eventId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE event_id = ?";
        try (var c = utils.DbConnexion.getInstance().getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
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
        activeParticipationFormController = null;
        activeEventTarget = null;
        activeSponsorEvent = null;
        activeSponsorTarget = null;
        activeParticipationEvent = null;

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

    private String formatSponsorStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "PENDING";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case EventSponsorshipService.STATUS_APPROVED -> "APPROVED";
            case EventSponsorshipService.STATUS_REJECTED -> "REJECTED";
            default -> "PENDING";
        };
    }

    private void showError(String title, String message) {
        Label target = activeView == ActiveEmbeddedView.LIST ? eventsStatusLabel : formStatusLabel;
        showStatus(target, title + ": " + message, true);
    }
}
