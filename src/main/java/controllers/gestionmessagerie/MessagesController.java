package controllers.gestionmessagerie;

import controllers.home.SignedInPageControllerBase;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import models.gestionmessagerie.ChatMessage;
import models.gestionmessagerie.Conversation;
import models.gestionmessagerie.ConversationFollowUp;
import models.gestionmessagerie.MessagingNotification;
import models.gestionutilisateurs.User;
import services.gestionmessagerie.ConversationService;
import services.speech.AudioRecorderService;
import utils.NavigationManager;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MessagesController extends SignedInPageControllerBase {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd MMM", java.util.Locale.FRANCE);

    @FXML
    private VBox conversationsList;
    @FXML
    private VBox messagesList;
    @FXML
    private TextArea messageInputArea;
    @FXML
    private TextField messageSearchField;
    @FXML
    private Label pageStatusLabel;
    @FXML
    private Label activeConversationTitleLabel;
    @FXML
    private ScrollPane messagesScrollPane;
    @FXML
    private VBox followUpsList;
    @FXML
    private TextField followUpTitleField;
    @FXML
    private TextArea followUpDescriptionArea;
    @FXML
    private Label notificationsStatusLabel;
    @FXML
    private VBox notificationsList;
    @FXML
    private Button followUpsSectionButton;
    @FXML
    private Button notificationsSectionButton;
    @FXML
    private VBox followUpsSectionBox;
    @FXML
    private VBox notificationsSectionBox;

    private final ConversationService conversationService = new ConversationService();
    private final AudioRecorderService audioRecorderService = new AudioRecorderService();
    private final List<Conversation> conversations = new ArrayList<>();
    private Integer currentUserId;
    private Conversation activeConversation;

    @FXML
    private void initialize() {
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            nav.showLogin();
            return;
        }
        User sessionUser = nav.sessionUser().orElse(null);
        if (sessionUser == null || sessionUser.getId() == null) {
            nav.showLogin();
            return;
        }
        currentUserId = sessionUser.getId();
        initSignedInSidebar();
        reloadConversations(nav.consumePendingConversationId());
        reloadNotifications();
        showFollowUpsSection();
    }

    @FXML
    private void onSendMessage() {
        if (activeConversation == null || activeConversation.getId() == null) {
            showPageStatus("Selectionnez une conversation.", true);
            return;
        }
        try {
            conversationService.sendMessage(activeConversation.getId(), currentUserId, messageInputArea.getText());
            messageInputArea.clear();
            refreshMessages();
            reloadConversations(activeConversation.getId());
            showPageStatus("Message envoye.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus(ex.getMessage(), true);
        }
    }

    private void reloadConversations(Long preferredConversationId) {
        try {
            conversations.clear();
            conversations.addAll(conversationService.findForUser(currentUserId));
            renderConversations();

            if (conversations.isEmpty()) {
                activeConversation = null;
                activeConversationTitleLabel.setText("No conversation selected");
                messagesList.getChildren().setAll(emptyLabel("Aucune conversation pour le moment."));
                followUpsList.getChildren().setAll(emptyLabel("Aucun follow-up."));
                return;
            }

            Conversation toOpen = conversations.get(0);
            if (preferredConversationId != null) {
                for (Conversation c : conversations) {
                    if (preferredConversationId.equals(c.getId())) {
                        toOpen = c;
                        break;
                    }
                }
            }
            selectConversation(toOpen);
        } catch (SQLException ex) {
            showPageStatus("Chargement impossible: " + ex.getMessage(), true);
        }
    }

    private void renderConversations() {
        conversationsList.getChildren().clear();
        if (conversations.isEmpty()) {
            conversationsList.getChildren().add(emptyLabel("Aucune conversation."));
            return;
        }
        for (Conversation c : conversations) {
            Button row = new Button();
            row.getStyleClass().add("messages-conversation-item");
            row.setStyle("-fx-background-color: rgba(34,26,58,0.85); -fx-border-color: #2E2550; -fx-border-width: 1; "
                    + "-fx-background-radius: 12; -fx-border-radius: 12; -fx-padding: 12 14 12 14;");
            row.setMaxWidth(Double.MAX_VALUE);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setWrapText(true);
            row.setGraphic(buildConversationCardGraphic(c));
            row.setOnAction(e -> selectConversation(c));
            if (activeConversation != null && activeConversation.getId() != null && activeConversation.getId().equals(c.getId())) {
                row.getStyleClass().add("messages-conversation-item-active");
            }
            conversationsList.getChildren().add(row);
        }
    }

    private void selectConversation(Conversation c) {
        activeConversation = c;
        activeConversationTitleLabel.setText(buildConversationHeader(c));
        try {
            conversationService.markConversationNotificationsRead(c.getId(), currentUserId);
            refreshMessages();
            renderFollowUps(conversationService.findFollowUpsForConversation(c.getId(), currentUserId));
            reloadNotifications();
            renderConversations();
            showPageStatus(null, false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus(ex.getMessage(), true);
        }
    }

    @FXML
    private void onCreateFollowUp() {
        if (activeConversation == null || activeConversation.getId() == null) {
            showPageStatus("Selectionnez une conversation.", true);
            return;
        }
        try {
            conversationService.createFollowUp(
                    activeConversation.getId(),
                    currentUserId,
                    followUpTitleField.getText(),
                    followUpDescriptionArea.getText());
            followUpTitleField.clear();
            followUpDescriptionArea.clear();
            renderFollowUps(conversationService.findFollowUpsForConversation(activeConversation.getId(), currentUserId));
            reloadNotifications();
            showPageStatus("Follow-up cree.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus(ex.getMessage(), true);
        }
    }

    private void renderFollowUps(List<ConversationFollowUp> followUps) {
        followUpsList.getChildren().clear();
        if (followUps.isEmpty()) {
            followUpsList.getChildren().add(emptyLabel("Aucun follow-up."));
            return;
        }
        for (ConversationFollowUp f : followUps) {
            String assignee = safe(f.getAssignedToDisplayName(), "Unknown");
            String header = safe(f.getTitle(), "Task") + " - " + safe(f.getStatus(), "OPEN");
            String details = "Assigned to: " + assignee;
            Label info = new Label(header + "\n" + details);
            info.setWrapText(true);
            info.getStyleClass().add("messages-followup-title");

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("messages-followup-card");
            row.getChildren().add(info);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().add(spacer);

            Button editBtn = new Button("Edit");
            editBtn.getStyleClass().add("event-action-secondary");
            editBtn.setOnAction(e -> onEditFollowUp(f));
            Button deleteBtn = new Button("Delete");
            deleteBtn.getStyleClass().add("event-action-danger");
            deleteBtn.setOnAction(e -> onDeleteFollowUp(f));
            row.getChildren().add(editBtn);
            if (!ConversationService.FOLLOW_UP_STATUS_DONE.equalsIgnoreCase(f.getStatus())) {
                Button doneBtn = new Button("Done");
                doneBtn.getStyleClass().add("event-action-secondary");
                doneBtn.setOnAction(e -> onMarkFollowUpDone(f));
                row.getChildren().add(doneBtn);
            }
            row.getChildren().add(deleteBtn);
            followUpsList.getChildren().add(row);
        }
    }

    private void onMarkFollowUpDone(ConversationFollowUp followUp) {
        try {
            conversationService.markFollowUpDone(followUp.getId(), currentUserId);
            if (activeConversation != null && activeConversation.getId() != null) {
                renderFollowUps(conversationService.findFollowUpsForConversation(activeConversation.getId(), currentUserId));
            }
            reloadNotifications();
            showPageStatus("Follow-up complete.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus(ex.getMessage(), true);
        }
    }

    private void onEditFollowUp(ConversationFollowUp followUp) {
        try {
            TextInputDialog titleDialog = new TextInputDialog(safe(followUp.getTitle(), ""));
            titleDialog.setTitle("Edit follow-up");
            titleDialog.setHeaderText("Update follow-up title");
            titleDialog.setContentText("Title:");
            var titleResult = titleDialog.showAndWait();
            if (titleResult.isEmpty()) {
                return;
            }
            TextInputDialog descDialog = new TextInputDialog(safe(followUp.getDescription(), ""));
            descDialog.setTitle("Edit follow-up");
            descDialog.setHeaderText("Update follow-up details");
            descDialog.setContentText("Details:");
            var descResult = descDialog.showAndWait();
            if (descResult.isEmpty()) {
                return;
            }
            conversationService.updateFollowUp(followUp.getId(), currentUserId, titleResult.get(), descResult.get());
            if (activeConversation != null && activeConversation.getId() != null) {
                renderFollowUps(conversationService.findFollowUpsForConversation(activeConversation.getId(), currentUserId));
            }
            showPageStatus("Follow-up updated.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus(ex.getMessage(), true);
        }
    }

    private void onDeleteFollowUp(ConversationFollowUp followUp) {
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete follow-up");
        confirm.setHeaderText("Delete this follow-up?");
        confirm.setContentText(safe(followUp.getTitle(), "Follow-up"));
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            conversationService.deleteFollowUp(followUp.getId(), currentUserId);
            if (activeConversation != null && activeConversation.getId() != null) {
                renderFollowUps(conversationService.findFollowUpsForConversation(activeConversation.getId(), currentUserId));
            }
            showPageStatus("Follow-up deleted.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus(ex.getMessage(), true);
        }
    }

    private void reloadNotifications() {
        if (notificationsStatusLabel == null || notificationsList == null) {
            return;
        }
        try {
            int unread = conversationService.countUnreadNotificationsForUser(currentUserId);
            if (!notificationsStatusLabel.getStyleClass().contains("messages-notifications-badge")) {
                notificationsStatusLabel.getStyleClass().add("messages-notifications-badge");
            }
            notificationsStatusLabel.setText("Unread notifications: " + unread);
            notificationsStatusLabel.setVisible(true);
            notificationsStatusLabel.setManaged(true);
            notificationsList.getChildren().clear();
            List<MessagingNotification> latest = conversationService.findRecentNotificationsForUser(currentUserId, 5);
            if (latest.isEmpty()) {
                notificationsList.getChildren().add(emptyLabel("Aucune notification."));
                return;
            }
            for (MessagingNotification n : latest) {
                String actor = safe(n.getActorDisplayName(), "System");
                String line = actor + " - " + safe(n.getTitle(), "Notification");
                if (n.getBody() != null && !n.getBody().isBlank()) {
                    line += "\n" + n.getBody();
                }
                Label l = new Label(line);
                l.setWrapText(true);
                l.getStyleClass().add("messages-notification-item");
                notificationsList.getChildren().add(l);
            }
        } catch (SQLException ex) {
            notificationsStatusLabel.setText("Notifications unavailable: " + ex.getMessage());
            notificationsStatusLabel.setVisible(true);
            notificationsStatusLabel.setManaged(true);
        }
    }

    @FXML
    private void onShowFollowUpsSection() {
        showFollowUpsSection();
    }

    @FXML
    private void onShowNotificationsSection() {
        showNotificationsSection();
    }

    @FXML
    private void onSearchMessages() {
        if (activeConversation == null || activeConversation.getId() == null) {
            showPageStatus("Selectionnez une conversation.", true);
            return;
        }
        try {
            refreshMessages();
            showPageStatus("Recherche appliquee.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus(ex.getMessage(), true);
        }
    }

    @FXML
    private void onClearSearchMessages() {
        if (messageSearchField != null) {
            messageSearchField.clear();
        }
        if (activeConversation == null || activeConversation.getId() == null) {
            return;
        }
        try {
            refreshMessages();
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus(ex.getMessage(), true);
        }
    }

    @FXML
    private void onRecordVoiceMessage() {
        if (activeConversation == null || activeConversation.getId() == null) {
            showPageStatus("Selectionnez une conversation.", true);
            return;
        }
        try {
            AudioRecorderService.RecordingResult result = audioRecorderService.recordToTempWav(Duration.ofSeconds(8));
            File wavFile = result.wavFile();
            conversationService.sendAudioMessage(activeConversation.getId(), currentUserId, wavFile.getAbsolutePath());
            refreshMessages();
            reloadConversations(activeConversation.getId());
            showPageStatus("Message vocal envoye.", false);
        } catch (IOException | SQLException | IllegalArgumentException ex) {
            showPageStatus("Echec vocal: " + ex.getMessage(), true);
        }
    }

    @FXML
    private void onSendImageMessage() {
        if (activeConversation == null || activeConversation.getId() == null) {
            showPageStatus("Selectionnez une conversation.", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp"));
        File selected = chooser.showOpenDialog(messageInputArea == null ? null : messageInputArea.getScene().getWindow());
        if (selected == null) {
            return;
        }
        try {
            conversationService.sendImageMessage(activeConversation.getId(), currentUserId, selected.getAbsolutePath());
            refreshMessages();
            reloadConversations(activeConversation.getId());
            showPageStatus("Image envoyee.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus("Echec envoi image: " + ex.getMessage(), true);
        }
    }

    @FXML
    private void onSendFileMessage() {
        if (activeConversation == null || activeConversation.getId() == null) {
            showPageStatus("Selectionnez une conversation.", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir un fichier");
        File selected = chooser.showOpenDialog(messageInputArea == null ? null : messageInputArea.getScene().getWindow());
        if (selected == null) {
            return;
        }
        try {
            conversationService.sendFileMessage(activeConversation.getId(), currentUserId, selected.getAbsolutePath());
            refreshMessages();
            reloadConversations(activeConversation.getId());
            showPageStatus("Fichier envoye.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus("Echec envoi fichier: " + ex.getMessage(), true);
        }
    }

    private void showFollowUpsSection() {
        if (followUpsSectionBox != null) {
            followUpsSectionBox.setManaged(true);
            followUpsSectionBox.setVisible(true);
        }
        if (notificationsSectionBox != null) {
            notificationsSectionBox.setManaged(false);
            notificationsSectionBox.setVisible(false);
        }
        if (followUpsSectionButton != null) {
            followUpsSectionButton.getStyleClass().remove("messages-entity-btn-active");
            followUpsSectionButton.getStyleClass().add("messages-entity-btn-active");
        }
        if (notificationsSectionButton != null) {
            notificationsSectionButton.getStyleClass().remove("messages-entity-btn-active");
        }
    }

    private void showNotificationsSection() {
        if (followUpsSectionBox != null) {
            followUpsSectionBox.setManaged(false);
            followUpsSectionBox.setVisible(false);
        }
        if (notificationsSectionBox != null) {
            notificationsSectionBox.setManaged(true);
            notificationsSectionBox.setVisible(true);
        }
        if (notificationsSectionButton != null) {
            notificationsSectionButton.getStyleClass().remove("messages-entity-btn-active");
            notificationsSectionButton.getStyleClass().add("messages-entity-btn-active");
        }
        if (followUpsSectionButton != null) {
            followUpsSectionButton.getStyleClass().remove("messages-entity-btn-active");
        }
    }

    private void renderMessages(List<ChatMessage> messages) {
        messagesList.getChildren().clear();
        if (messages.isEmpty()) {
            messagesList.getChildren().add(buildChatEmptyState("💬", "Sélectionnez une conversation"));
            return;
        }

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            LocalDate currentDay = m.getCreatedAt() == null ? null : m.getCreatedAt().toLocalDate();
            LocalDate previousDay = null;
            if (i > 0 && messages.get(i - 1).getCreatedAt() != null) {
                previousDay = messages.get(i - 1).getCreatedAt().toLocalDate();
            }
            if (currentDay != null && (previousDay == null || !previousDay.equals(currentDay))) {
                Label dayChip = new Label(DAY_FMT.format(currentDay));
                dayChip.getStyleClass().add("messages-day-separator");
                HBox dayRow = new HBox(dayChip);
                dayRow.setAlignment(Pos.CENTER);
                dayRow.setPadding(new Insets(4, 0, 6, 0));
                messagesList.getChildren().add(dayRow);
            }

            boolean mine = currentUserId != null && currentUserId.equals(m.getSenderUserId());
            Label text = new Label(m.getContent());
            text.setWrapText(true);
            text.getStyleClass().add(mine ? "messages-bubble-mine" : "messages-bubble-other");
            if (messagesScrollPane != null) {
                text.maxWidthProperty().bind(messagesScrollPane.widthProperty().multiply(0.65));
            } else {
                text.setMaxWidth(460);
            }

            String who = mine ? "Moi" : safe(m.getSenderDisplayName(), "Participant");
            String when = m.getCreatedAt() == null ? "" : DATE_FMT.format(m.getCreatedAt());
            Label meta = new Label(who + (when.isBlank() ? "" : " - " + when));
            meta.getStyleClass().add("messages-meta");

            VBox box = new VBox(4, text, meta);
            String type = safe(m.getMessageType(), "TEXT");
            if ("AUDIO".equalsIgnoreCase(type) && m.getFileUrl() != null && !m.getFileUrl().isBlank()) {
                Button playBtn = new Button("Play voice");
                playBtn.getStyleClass().add("event-action-secondary");
                playBtn.setOnAction(e -> onOpenMediaFile(m, "Fichier vocal introuvable."));
                box.getChildren().add(playBtn);
            } else if (("IMAGE".equalsIgnoreCase(type) || "FILE".equalsIgnoreCase(type)) && m.getFileUrl() != null && !m.getFileUrl().isBlank()) {
                Button openBtn = new Button("Open file");
                openBtn.getStyleClass().add("event-action-secondary");
                openBtn.setOnAction(e -> onOpenMediaFile(m, "Fichier introuvable."));
                box.getChildren().add(openBtn);
            }
            box.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            if (mine) {
                HBox actions = new HBox(6);
                actions.setAlignment(Pos.CENTER_RIGHT);
                Button editBtn = new Button("Edit");
                editBtn.getStyleClass().add("event-action-secondary");
                editBtn.setOnAction(e -> onEditMessage(m));
                Button deleteBtn = new Button("Delete");
                deleteBtn.getStyleClass().add("event-action-danger");
                deleteBtn.setOnAction(e -> onDeleteMessage(m));
                actions.getChildren().addAll(editBtn, deleteBtn);
                box.getChildren().add(actions);
            }
            HBox row = new HBox(box);
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            row.setPadding(new Insets(0, 0, 4, 0));
            messagesList.getChildren().add(row);
        }

        if (messagesScrollPane != null) {
            messagesScrollPane.layout();
            messagesScrollPane.setVvalue(1.0);
        }
    }

    private void onEditMessage(ChatMessage message) {
        TextInputDialog dialog = new TextInputDialog(safe(message.getContent(), ""));
        dialog.setTitle("Edit message");
        dialog.setHeaderText("Update your message");
        dialog.setContentText("Message:");
        var result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        try {
            conversationService.updateMessage(message.getId(), currentUserId, result.get());
            if (activeConversation != null && activeConversation.getId() != null) {
                refreshMessages();
            }
            showPageStatus("Message updated.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus(ex.getMessage(), true);
        }
    }

    private void onDeleteMessage(ChatMessage message) {
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete message");
        confirm.setHeaderText("Delete this message?");
        confirm.setContentText(safe(message.getContent(), ""));
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            conversationService.deleteMessage(message.getId(), currentUserId);
            if (activeConversation != null && activeConversation.getId() != null) {
                refreshMessages();
                reloadConversations(activeConversation.getId());
            }
            showPageStatus("Message deleted.", false);
        } catch (SQLException | IllegalArgumentException ex) {
            showPageStatus(ex.getMessage(), true);
        }
    }

    private void onOpenMediaFile(ChatMessage message, String missingMessage) {
        try {
            if (message.getFileUrl() == null || message.getFileUrl().isBlank()) {
                showPageStatus(missingMessage, true);
                return;
            }
            File target = new File(message.getFileUrl());
            if (!target.exists()) {
                showPageStatus("Le fichier n'existe plus.", true);
                return;
            }
            if (!Desktop.isDesktopSupported()) {
                showPageStatus("Ouverture de fichier non supportee sur cette machine.", true);
                return;
            }
            Desktop.getDesktop().open(target);
        } catch (IOException ex) {
            showPageStatus("Impossible d'ouvrir le fichier: " + ex.getMessage(), true);
        }
    }

    private void refreshMessages() throws SQLException {
        if (activeConversation == null || activeConversation.getId() == null) {
            renderMessages(List.of());
            return;
        }
        String query = messageSearchField == null ? null : messageSearchField.getText();
        List<ChatMessage> messages = (query == null || query.isBlank())
                ? conversationService.findMessages(activeConversation.getId(), currentUserId)
                : conversationService.searchMessages(activeConversation.getId(), currentUserId, query);
        renderMessages(messages);
    }

    private Label emptyLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("messages-empty-label");
        l.setWrapText(true);
        return l;
    }

    private VBox buildChatEmptyState(String icon, String text) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("messages-empty-icon");
        Label textLabel = new Label(text);
        textLabel.getStyleClass().add("messages-empty-title");
        VBox box = new VBox(10, iconLabel, textLabel);
        box.getStyleClass().add("messages-empty-state");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private String buildConversationHeader(Conversation c) {
        String event = safe(c.getEventTitle(), "Event");
        String agency = safe(c.getAgencyName(), "Agency");
        return event + " - " + agency;
    }

    private String buildConversationLabel(Conversation c) {
        String header = buildConversationHeader(c);
        String footer = c.getLastMessageAt() == null ? "No message yet" : "Last message: " + DATE_FMT.format(c.getLastMessageAt());
        return header + "\n" + footer;
    }

    private HBox buildConversationCardGraphic(Conversation c) {
        String title = buildConversationHeader(c);
        String subtitle = c.getLastMessageAt() == null ? "No message yet" : "Last message: " + DATE_FMT.format(c.getLastMessageAt());
        String timestamp = c.getLastMessageAt() == null ? "" : DATE_FMT.format(c.getLastMessageAt());

        Label initials = new Label(initialsFromConversation(c));
        initials.getStyleClass().add("messages-avatar-initials");
        StackPane avatar = new StackPane(initials);
        avatar.getStyleClass().add("messages-avatar");
        avatar.setMinSize(40, 40);
        avatar.setPrefSize(40, 40);
        avatar.setMaxSize(40, 40);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("messages-conversation-title");
        Label timestampLabel = new Label(timestamp);
        timestampLabel.getStyleClass().add("messages-conversation-time");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("messages-conversation-subtitle");
        if ("No message yet".equals(subtitle)) {
            subtitleLabel.getStyleClass().add("messages-conversation-subtitle-empty");
        }
        subtitleLabel.setWrapText(false);
        subtitleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        subtitleLabel.setMaxWidth(160);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox topRow = new HBox(6, titleLabel, topSpacer, timestampLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox textBox = new VBox(2, topRow, subtitleLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox row = new HBox(10, avatar, textBox);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String initialsFromConversation(Conversation c) {
        String src = safe(c.getAgencyName(), "AG");
        String cleaned = src.trim();
        if (cleaned.isBlank()) {
            return "AG";
        }
        String[] parts = cleaned.split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return cleaned.substring(0, Math.min(2, cleaned.length())).toUpperCase();
    }

    private void showPageStatus(String message, boolean error) {
        if (pageStatusLabel == null) {
            return;
        }
        if (message == null || message.isBlank()) {
            pageStatusLabel.setText("");
            pageStatusLabel.setVisible(false);
            pageStatusLabel.setManaged(false);
            pageStatusLabel.getStyleClass().removeAll("status-error", "status-success");
            return;
        }
        pageStatusLabel.setText(message);
        pageStatusLabel.setVisible(true);
        pageStatusLabel.setManaged(true);
        pageStatusLabel.getStyleClass().removeAll("status-error", "status-success");
        pageStatusLabel.getStyleClass().add(error ? "status-error" : "status-success");
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
