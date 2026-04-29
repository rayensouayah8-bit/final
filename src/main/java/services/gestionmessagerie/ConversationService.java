package services.gestionmessagerie;

import models.gestionevenements.TravelEvent;
import models.gestionmessagerie.ChatMessage;
import models.gestionmessagerie.Conversation;
import models.gestionmessagerie.ConversationFollowUp;
import models.gestionmessagerie.MessagingNotification;
import services.TwilioNotificationService;
import utils.DbConnexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConversationService {
    public static final String FOLLOW_UP_STATUS_OPEN = "OPEN";
    public static final String FOLLOW_UP_STATUS_DONE = "DONE";
    private static final int MAX_CONSECUTIVE_MESSAGES = 5;
    private static final Set<String> BAD_WORDS = Set.of(
            "merde", "putain", "con", "idiot", "stupide",
            "fuck", "shit", "damn", "stupid", "dumb", "bitch",
            "caca", "nul"
    );
    private final TwilioNotificationService twilioNotificationService = new TwilioNotificationService();

    public Long ensureConversationForEvent(Integer travelerUserId, TravelEvent event) throws SQLException {
        if (travelerUserId == null) {
            throw new IllegalArgumentException("Utilisateur non authentifie.");
        }
        if (event == null || event.getId() == null) {
            throw new IllegalArgumentException("Evenement invalide.");
        }
        Integer agencyResponsableUserId = event.getCreatedByUserId();
        if (agencyResponsableUserId == null) {
            throw new IllegalArgumentException("Responsable agence introuvable pour cet evenement.");
        }
        if (travelerUserId.equals(agencyResponsableUserId)) {
            throw new IllegalArgumentException("Vous etes le proprietaire de cet evenement.");
        }

        Connection c = DbConnexion.getInstance().getConnection();
        Long existingId = findConversationId(c, event.getId(), travelerUserId, agencyResponsableUserId);
        if (existingId != null) {
            return existingId;
        }

        String insert = """
                INSERT INTO conversation (
                    event_id, traveler_user_id, agency_responsable_user_id, created_at, updated_at, last_message_at
                ) VALUES (?, ?, ?, NOW(), NOW(), NULL)
                """;
        try (PreparedStatement ps = c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, event.getId());
            ps.setInt(2, travelerUserId);
            ps.setInt(3, agencyResponsableUserId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Creation de conversation echouee.");
    }

    public Long ensureDirectConversationWithAgency(Integer travelerUserId, Integer agencyResponsableUserId) throws SQLException {
        if (travelerUserId == null) {
            throw new IllegalArgumentException("Utilisateur non authentifie.");
        }
        if (agencyResponsableUserId == null) {
            throw new IllegalArgumentException("Responsable agence introuvable.");
        }
        if (travelerUserId.equals(agencyResponsableUserId)) {
            throw new IllegalArgumentException("Vous etes le responsable de cette agence.");
        }

        Connection c = DbConnexion.getInstance().getConnection();
        Long existingId = findDirectConversationId(c, travelerUserId, agencyResponsableUserId);
        if (existingId != null) {
            return existingId;
        }

        String insert = """
                INSERT INTO conversation (
                    event_id, traveler_user_id, agency_responsable_user_id, created_at, updated_at, last_message_at
                ) VALUES (NULL, ?, ?, NOW(), NOW(), NULL)
                """;
        try (PreparedStatement ps = c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, travelerUserId);
            ps.setInt(2, agencyResponsableUserId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Creation de conversation directe echouee.");
    }

    public List<Conversation> findForUser(Integer userId) throws SQLException {
        if (userId == null) {
            return List.of();
        }
        String sql = """
                SELECT c.id, c.event_id, c.traveler_user_id, c.agency_responsable_user_id,
                       c.created_at, c.updated_at, c.last_message_at,
                       te.title AS event_title,
                       COALESCE(aa.agency_name, CONCAT('Agency #', c.agency_responsable_user_id)) AS agency_name,
                       COALESCE(ut.username, CONCAT('Traveler #', c.traveler_user_id)) AS traveler_name
                FROM conversation c
                LEFT JOIN travel_event te ON te.id = c.event_id
                LEFT JOIN agency_account aa ON aa.responsable_id = c.agency_responsable_user_id
                LEFT JOIN `user` ut ON ut.id = c.traveler_user_id
                WHERE c.traveler_user_id = ? OR c.agency_responsable_user_id = ?
                ORDER BY COALESCE(c.last_message_at, c.updated_at, c.created_at) DESC, c.id DESC
                """;
        List<Conversation> out = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapConversation(rs));
                }
            }
        }
        return out;
    }

    public List<ChatMessage> findMessages(Long conversationId, Integer requesterUserId) throws SQLException {
        ensureParticipant(conversationId, requesterUserId);
        String sql = """
                SELECT m.id, m.conversation_id, m.sender_user_id, m.content,
                       COALESCE(m.message_type, 'TEXT') AS message_type, m.file_url, m.created_at,
                       COALESCE(u.username, CONCAT('User #', m.sender_user_id)) AS sender_display_name
                FROM chat_message m
                LEFT JOIN `user` u ON u.id = m.sender_user_id
                WHERE m.conversation_id = ?
                ORDER BY m.created_at ASC, m.id ASC
                """;
        List<ChatMessage> out = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapMessage(rs));
                }
            }
        }
        return out;
    }

    public List<ChatMessage> searchMessages(Long conversationId, Integer requesterUserId, String query) throws SQLException {
        ensureParticipant(conversationId, requesterUserId);
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) {
            return findMessages(conversationId, requesterUserId);
        }
        String sql = """
                SELECT m.id, m.conversation_id, m.sender_user_id, m.content,
                       COALESCE(m.message_type, 'TEXT') AS message_type, m.file_url, m.created_at,
                       COALESCE(u.username, CONCAT('User #', m.sender_user_id)) AS sender_display_name
                FROM chat_message m
                LEFT JOIN `user` u ON u.id = m.sender_user_id
                WHERE m.conversation_id = ?
                  AND (
                      LOWER(m.content) LIKE CONCAT('%', LOWER(?), '%')
                      OR (m.message_type IN ('AUDIO', 'IMAGE', 'FILE') AND LOWER(COALESCE(m.file_url, '')) LIKE CONCAT('%', LOWER(?), '%'))
                  )
                ORDER BY m.created_at ASC, m.id ASC
                """;
        List<ChatMessage> out = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ps.setString(2, normalized);
            ps.setString(3, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapMessage(rs));
                }
            }
        }
        return out;
    }

    public void sendMessage(Long conversationId, Integer senderUserId, String content) throws SQLException {
        if (senderUserId == null) {
            throw new IllegalArgumentException("Utilisateur non authentifie.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message vide.");
        }
        String trimmed = content.trim();
        if (trimmed.length() > 1500) {
            throw new IllegalArgumentException("Le message ne doit pas depasser 1500 caracteres.");
        }
        ModerationResult moderation = moderateBadWords(trimmed);
        ensureParticipant(conversationId, senderUserId);
        assertConsecutiveSpamRule(conversationId, senderUserId);

        Connection c = DbConnexion.getInstance().getConnection();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement insert = c.prepareStatement("""
                    INSERT INTO chat_message (conversation_id, sender_user_id, content, message_type, file_url, created_at)
                    VALUES (?, ?, ?, 'TEXT', NULL, NOW())
                    """)) {
                insert.setLong(1, conversationId);
                insert.setInt(2, senderUserId);
                insert.setString(3, moderation.sanitizedContent());
                insert.executeUpdate();
            }
            try (PreparedStatement update = c.prepareStatement("""
                    UPDATE conversation
                    SET updated_at = NOW(), last_message_at = NOW()
                    WHERE id = ?
                    """)) {
                update.setLong(1, conversationId);
                update.executeUpdate();
            }
            Integer recipientId = findCounterpartUserId(c, conversationId, senderUserId);
            if (recipientId != null) {
                createNotification(c, recipientId, senderUserId, "MESSAGE_NEW",
                        "New message received", moderation.sanitizedContent(), conversationId, null);
                maybeCreateAutomaticFollowUp(c, conversationId, senderUserId, recipientId, moderation.sanitizedContent());
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    public void sendAudioMessage(Long conversationId, Integer senderUserId, String fileUrl) throws SQLException {
        sendMediaMessage(conversationId, senderUserId, fileUrl, "AUDIO", "Voice message", "New voice message received");
    }

    public void sendImageMessage(Long conversationId, Integer senderUserId, String fileUrl) throws SQLException {
        String fileName = extractFileName(fileUrl);
        String content = fileName == null ? "Image" : ("Image: " + fileName);
        sendMediaMessage(conversationId, senderUserId, fileUrl, "IMAGE", content, "New image received");
    }

    public void sendFileMessage(Long conversationId, Integer senderUserId, String fileUrl) throws SQLException {
        String fileName = extractFileName(fileUrl);
        String content = fileName == null ? "File" : ("File: " + fileName);
        sendMediaMessage(conversationId, senderUserId, fileUrl, "FILE", content, "New file received");
    }

    private void sendMediaMessage(Long conversationId,
                                  Integer senderUserId,
                                  String fileUrl,
                                  String messageType,
                                  String content,
                                  String notificationTitle) throws SQLException {
        if (senderUserId == null) {
            throw new IllegalArgumentException("Utilisateur non authentifie.");
        }
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Fichier invalide.");
        }
        if (messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException("Type de message invalide.");
        }
        String normalizedUrl = fileUrl.trim();
        ensureParticipant(conversationId, senderUserId);
        assertConsecutiveSpamRule(conversationId, senderUserId);

        Connection c = DbConnexion.getInstance().getConnection();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement insert = c.prepareStatement("""
                    INSERT INTO chat_message (conversation_id, sender_user_id, content, message_type, file_url, created_at)
                    VALUES (?, ?, ?, ?, ?, NOW())
                    """)) {
                insert.setLong(1, conversationId);
                insert.setInt(2, senderUserId);
                insert.setString(3, content == null || content.isBlank() ? "Media" : content);
                insert.setString(4, messageType);
                insert.setString(5, normalizedUrl);
                insert.executeUpdate();
            }
            try (PreparedStatement update = c.prepareStatement("""
                    UPDATE conversation
                    SET updated_at = NOW(), last_message_at = NOW()
                    WHERE id = ?
                    """)) {
                update.setLong(1, conversationId);
                update.executeUpdate();
            }
            Integer recipientId = findCounterpartUserId(c, conversationId, senderUserId);
            if (recipientId != null) {
                createNotification(c, recipientId, senderUserId, "MESSAGE_NEW",
                        notificationTitle == null ? "New media received" : notificationTitle,
                        content == null ? "Media" : content, conversationId, null);
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    private static String extractFileName(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        String normalized = fileUrl.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        String fileName = idx >= 0 ? normalized.substring(idx + 1) : normalized;
        return fileName.isBlank() ? null : fileName;
    }

    public void updateMessage(Long messageId, Integer actorUserId, String content) throws SQLException {
        if (messageId == null || actorUserId == null) {
            throw new IllegalArgumentException("Message invalide.");
        }
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Message vide.");
        }
        if (trimmed.length() > 1500) {
            throw new IllegalArgumentException("Le message ne doit pas depasser 1500 caracteres.");
        }
        ModerationResult moderation = moderateBadWords(trimmed);

        Connection c = DbConnexion.getInstance().getConnection();
        try {
            c.setAutoCommit(false);
            Long conversationId = null;
            Integer sender = null;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT conversation_id, sender_user_id
                    FROM chat_message
                    WHERE id = ?
                    LIMIT 1
                    """)) {
                ps.setLong(1, messageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        conversationId = rs.getLong("conversation_id");
                        sender = rs.getInt("sender_user_id");
                    }
                }
            }
            if (conversationId == null) {
                throw new IllegalArgumentException("Message introuvable.");
            }
            ensureParticipant(conversationId, actorUserId);
            if (!actorUserId.equals(sender)) {
                throw new IllegalArgumentException("Seul l'auteur peut modifier ce message.");
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE chat_message
                    SET content = ?
                    WHERE id = ?
                    """)) {
                ps.setString(1, moderation.sanitizedContent());
                ps.setLong(2, messageId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE conversation
                    SET updated_at = NOW(), last_message_at = NOW()
                    WHERE id = ?
                    """)) {
                ps.setLong(1, conversationId);
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    public void deleteMessage(Long messageId, Integer actorUserId) throws SQLException {
        if (messageId == null || actorUserId == null) {
            throw new IllegalArgumentException("Message invalide.");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try {
            c.setAutoCommit(false);
            Long conversationId = null;
            Integer sender = null;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT conversation_id, sender_user_id
                    FROM chat_message
                    WHERE id = ?
                    LIMIT 1
                    """)) {
                ps.setLong(1, messageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        conversationId = rs.getLong("conversation_id");
                        sender = rs.getInt("sender_user_id");
                    }
                }
            }
            if (conversationId == null) {
                throw new IllegalArgumentException("Message introuvable.");
            }
            ensureParticipant(conversationId, actorUserId);
            if (!actorUserId.equals(sender)) {
                throw new IllegalArgumentException("Seul l'auteur peut supprimer ce message.");
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM chat_message WHERE id = ?")) {
                ps.setLong(1, messageId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE conversation
                    SET updated_at = NOW(), last_message_at = (
                        SELECT MAX(created_at) FROM chat_message WHERE conversation_id = ?
                    )
                    WHERE id = ?
                    """)) {
                ps.setLong(1, conversationId);
                ps.setLong(2, conversationId);
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    public List<ConversationFollowUp> findFollowUpsForConversation(Long conversationId, Integer requesterUserId) throws SQLException {
        ensureParticipant(conversationId, requesterUserId);
        String sql = """
                SELECT f.id, f.conversation_id, f.created_by_user_id, f.assigned_to_user_id,
                       f.title, f.description, f.status, f.due_at, f.done_at, f.created_at, f.updated_at,
                       COALESCE(u.username, CONCAT('User #', f.assigned_to_user_id)) AS assigned_to_display_name
                FROM conversation_follow_up f
                LEFT JOIN `user` u ON u.id = f.assigned_to_user_id
                WHERE f.conversation_id = ?
                ORDER BY f.created_at DESC, f.id DESC
                """;
        List<ConversationFollowUp> out = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapFollowUp(rs));
                }
            }
        }
        return out;
    }

    public void createFollowUp(Long conversationId, Integer creatorUserId, String title, String description) throws SQLException {
        if (creatorUserId == null) {
            throw new IllegalArgumentException("Utilisateur non authentifie.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Le titre du follow-up est obligatoire.");
        }
        String normalizedTitle = title.trim();
        if (normalizedTitle.length() > 180) {
            throw new IllegalArgumentException("Le titre ne doit pas depasser 180 caracteres.");
        }
        String normalizedDescription = description == null ? null : description.trim();
        ensureParticipant(conversationId, creatorUserId);

        Connection c = DbConnexion.getInstance().getConnection();
        try {
            c.setAutoCommit(false);
            Integer assignedToUserId = findCounterpartUserId(c, conversationId, creatorUserId);
            if (assignedToUserId == null) {
                throw new IllegalArgumentException("Participant cible introuvable.");
            }
            Long followUpId;
            try (PreparedStatement insert = c.prepareStatement("""
                    INSERT INTO conversation_follow_up (
                        conversation_id, created_by_user_id, assigned_to_user_id, title, description, status, due_at, done_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NOW(), NOW())
                    """, Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, conversationId);
                insert.setInt(2, creatorUserId);
                insert.setInt(3, assignedToUserId);
                insert.setString(4, normalizedTitle);
                if (normalizedDescription == null || normalizedDescription.isBlank()) {
                    insert.setNull(5, java.sql.Types.VARCHAR);
                } else {
                    insert.setString(5, normalizedDescription);
                }
                insert.setString(6, FOLLOW_UP_STATUS_OPEN);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Creation follow-up echouee.");
                    }
                    followUpId = keys.getLong(1);
                }
            }
            createNotification(c, assignedToUserId, creatorUserId, "FOLLOW_UP_CREATED",
                    "New follow-up task", normalizedTitle, conversationId, followUpId);
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    public void markFollowUpDone(Long followUpId, Integer actorUserId) throws SQLException {
        if (followUpId == null || actorUserId == null) {
            throw new IllegalArgumentException("Follow-up invalide.");
        }
        String recipientPhone = null;
        String followUpTitleForSms = null;
        Connection c = DbConnexion.getInstance().getConnection();
        try {
            c.setAutoCommit(false);
            Long conversationId;
            Integer createdBy;
            Integer assignedTo;
            String title;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT conversation_id, created_by_user_id, assigned_to_user_id, title
                    FROM conversation_follow_up
                    WHERE id = ?
                    """)) {
                ps.setLong(1, followUpId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Follow-up introuvable.");
                    }
                    conversationId = rs.getLong("conversation_id");
                    createdBy = rs.getInt("created_by_user_id");
                    assignedTo = rs.getInt("assigned_to_user_id");
                    title = rs.getString("title");
                }
            }
            ensureParticipant(conversationId, actorUserId);
            if (!actorUserId.equals(createdBy) && !actorUserId.equals(assignedTo)) {
                throw new IllegalArgumentException("Action non autorisee.");
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE conversation_follow_up
                    SET status = ?, done_at = NOW(), updated_at = NOW()
                    WHERE id = ?
                    """)) {
                ps.setString(1, FOLLOW_UP_STATUS_DONE);
                ps.setLong(2, followUpId);
                ps.executeUpdate();
            }
            Integer recipient = actorUserId.equals(createdBy) ? assignedTo : createdBy;
            if (recipient != null) {
                createNotification(c, recipient, actorUserId, "FOLLOW_UP_DONE",
                        "Follow-up completed", title, conversationId, followUpId);
                recipientPhone = findUserPhone(c, recipient);
                followUpTitleForSms = title;
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }

        // External network call intentionally happens after commit.
        if (recipientPhone != null && !recipientPhone.isBlank()) {
            twilioNotificationService.sendFollowUpDoneSms(recipientPhone, followUpTitleForSms);
        }
    }

    public void updateFollowUp(Long followUpId, Integer actorUserId, String title, String description) throws SQLException {
        if (followUpId == null || actorUserId == null) {
            throw new IllegalArgumentException("Follow-up invalide.");
        }
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("Le titre du follow-up est obligatoire.");
        }
        if (normalizedTitle.length() > 180) {
            throw new IllegalArgumentException("Le titre ne doit pas depasser 180 caracteres.");
        }
        String normalizedDescription = description == null ? null : description.trim();

        Connection c = DbConnexion.getInstance().getConnection();
        try {
            c.setAutoCommit(false);
            Long conversationId;
            Integer createdBy;
            Integer assignedTo;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT conversation_id, created_by_user_id, assigned_to_user_id
                    FROM conversation_follow_up
                    WHERE id = ?
                    LIMIT 1
                    """)) {
                ps.setLong(1, followUpId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Follow-up introuvable.");
                    }
                    conversationId = rs.getLong("conversation_id");
                    createdBy = rs.getInt("created_by_user_id");
                    assignedTo = rs.getInt("assigned_to_user_id");
                }
            }
            ensureParticipant(conversationId, actorUserId);
            if (!actorUserId.equals(createdBy) && !actorUserId.equals(assignedTo)) {
                throw new IllegalArgumentException("Action non autorisee.");
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE conversation_follow_up
                    SET title = ?, description = ?, updated_at = NOW()
                    WHERE id = ?
                    """)) {
                ps.setString(1, normalizedTitle);
                if (normalizedDescription == null || normalizedDescription.isBlank()) {
                    ps.setNull(2, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(2, normalizedDescription);
                }
                ps.setLong(3, followUpId);
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    public void deleteFollowUp(Long followUpId, Integer actorUserId) throws SQLException {
        if (followUpId == null || actorUserId == null) {
            throw new IllegalArgumentException("Follow-up invalide.");
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try {
            c.setAutoCommit(false);
            Long conversationId;
            Integer createdBy;
            Integer assignedTo;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT conversation_id, created_by_user_id, assigned_to_user_id
                    FROM conversation_follow_up
                    WHERE id = ?
                    LIMIT 1
                    """)) {
                ps.setLong(1, followUpId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Follow-up introuvable.");
                    }
                    conversationId = rs.getLong("conversation_id");
                    createdBy = rs.getInt("created_by_user_id");
                    assignedTo = rs.getInt("assigned_to_user_id");
                }
            }
            ensureParticipant(conversationId, actorUserId);
            if (!actorUserId.equals(createdBy) && !actorUserId.equals(assignedTo)) {
                throw new IllegalArgumentException("Action non autorisee.");
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM conversation_follow_up WHERE id = ?")) {
                ps.setLong(1, followUpId);
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(true);
        }
    }

    public int countUnreadNotificationsForUser(Integer userId) throws SQLException {
        if (userId == null) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM messaging_notification WHERE recipient_user_id = ? AND is_read = 0";
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public void markConversationNotificationsRead(Long conversationId, Integer userId) throws SQLException {
        if (conversationId == null || userId == null) {
            return;
        }
        String sql = """
                UPDATE messaging_notification
                SET is_read = 1, read_at = NOW()
                WHERE recipient_user_id = ? AND conversation_id = ? AND is_read = 0
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setLong(2, conversationId);
            ps.executeUpdate();
        }
    }

    public List<MessagingNotification> findRecentNotificationsForUser(Integer userId, int limit) throws SQLException {
        if (userId == null) {
            return List.of();
        }
        String sql = """
                SELECT n.id, n.recipient_user_id, n.actor_user_id, n.type, n.title, n.body,
                       n.conversation_id, n.follow_up_id, n.is_read, n.created_at, n.read_at,
                       COALESCE(u.username, CONCAT('User #', n.actor_user_id)) AS actor_display_name
                FROM messaging_notification n
                LEFT JOIN `user` u ON u.id = n.actor_user_id
                WHERE n.recipient_user_id = ?
                ORDER BY n.created_at DESC, n.id DESC
                LIMIT ?
                """;
        List<MessagingNotification> out = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapNotification(rs));
                }
            }
        }
        return out;
    }

    public int adminCountConversations() throws SQLException {
        return countScalar("SELECT COUNT(*) FROM conversation");
    }

    public int adminCountMessages() throws SQLException {
        return countScalar("SELECT COUNT(*) FROM chat_message");
    }

    public int adminCountOpenFollowUps() throws SQLException {
        String sql = "SELECT COUNT(*) FROM conversation_follow_up WHERE UPPER(status) = 'OPEN'";
        return countScalar(sql);
    }

    public List<ConversationFollowUp> adminRecentFollowUps(int limit) throws SQLException {
        String sql = """
                SELECT f.id, f.conversation_id, f.created_by_user_id, f.assigned_to_user_id,
                       f.title, f.description, f.status, f.due_at, f.done_at, f.created_at, f.updated_at,
                       COALESCE(u.username, CONCAT('User #', f.assigned_to_user_id)) AS assigned_to_display_name
                FROM conversation_follow_up f
                LEFT JOIN `user` u ON u.id = f.assigned_to_user_id
                ORDER BY f.created_at DESC, f.id DESC
                LIMIT ?
                """;
        List<ConversationFollowUp> out = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapFollowUp(rs));
                }
            }
        }
        return out;
    }

    public List<MessagingNotification> adminRecentNotifications(int limit) throws SQLException {
        String sql = """
                SELECT n.id, n.recipient_user_id, n.actor_user_id, n.type, n.title, n.body,
                       n.conversation_id, n.follow_up_id, n.is_read, n.created_at, n.read_at,
                       COALESCE(u.username, CONCAT('User #', n.actor_user_id)) AS actor_display_name
                FROM messaging_notification n
                LEFT JOIN `user` u ON u.id = n.actor_user_id
                ORDER BY n.created_at DESC, n.id DESC
                LIMIT ?
                """;
        List<MessagingNotification> out = new ArrayList<>();
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapNotification(rs));
                }
            }
        }
        return out;
    }

    private void ensureParticipant(Long conversationId, Integer userId) throws SQLException {
        if (conversationId == null || userId == null) {
            throw new IllegalArgumentException("Conversation invalide.");
        }
        String sql = """
                SELECT 1
                FROM conversation
                WHERE id = ?
                  AND (traveler_user_id = ? OR agency_responsable_user_id = ?)
                LIMIT 1
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Acces refuse a cette conversation.");
                }
            }
        }
    }

    private Long findConversationId(Connection c, Long eventId, Integer travelerId, Integer agencyUserId) throws SQLException {
        String sql = """
                SELECT id
                FROM conversation
                WHERE event_id = ?
                  AND traveler_user_id = ?
                  AND agency_responsable_user_id = ?
                LIMIT 1
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setInt(2, travelerId);
            ps.setInt(3, agencyUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        return null;
    }

    private Long findDirectConversationId(Connection c, Integer travelerId, Integer agencyUserId) throws SQLException {
        String sql = """
                SELECT id
                FROM conversation
                WHERE event_id IS NULL
                  AND traveler_user_id = ?
                  AND agency_responsable_user_id = ?
                LIMIT 1
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, travelerId);
            ps.setInt(2, agencyUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        return null;
    }

    private Integer findCounterpartUserId(Connection c, Long conversationId, Integer currentUserId) throws SQLException {
        String sql = """
                SELECT traveler_user_id, agency_responsable_user_id
                FROM conversation
                WHERE id = ?
                LIMIT 1
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Integer traveler = rs.getInt("traveler_user_id");
                    if (rs.wasNull()) {
                        traveler = null;
                    }
                    Integer agency = rs.getInt("agency_responsable_user_id");
                    if (rs.wasNull()) {
                        agency = null;
                    }
                    if (currentUserId != null && currentUserId.equals(traveler)) {
                        return agency;
                    }
                    if (currentUserId != null && currentUserId.equals(agency)) {
                        return traveler;
                    }
                }
            }
        }
        return null;
    }

    private String findUserPhone(Connection c, Integer userId) throws SQLException {
        if (userId == null) {
            return null;
        }
        String sql = """
                SELECT phone
                FROM `user`
                WHERE id = ?
                LIMIT 1
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String phone = rs.getString("phone");
                    return (phone == null || phone.isBlank()) ? null : phone.trim();
                }
            }
        }
        return null;
    }

    private void createNotification(Connection c,
                                    Integer recipientUserId,
                                    Integer actorUserId,
                                    String type,
                                    String title,
                                    String body,
                                    Long conversationId,
                                    Long followUpId) throws SQLException {
        String sql = """
                INSERT INTO messaging_notification (
                    recipient_user_id, actor_user_id, type, title, body,
                    conversation_id, follow_up_id, is_read, created_at, read_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, NOW(), NULL)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, recipientUserId);
            if (actorUserId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, actorUserId);
            }
            ps.setString(3, type);
            ps.setString(4, title == null ? "Notification" : title);
            if (body == null || body.isBlank()) {
                ps.setNull(5, java.sql.Types.VARCHAR);
            } else {
                ps.setString(5, body);
            }
            if (conversationId == null) {
                ps.setNull(6, java.sql.Types.BIGINT);
            } else {
                ps.setLong(6, conversationId);
            }
            if (followUpId == null) {
                ps.setNull(7, java.sql.Types.BIGINT);
            } else {
                ps.setLong(7, followUpId);
            }
            ps.executeUpdate();
        }
    }

    private void maybeCreateAutomaticFollowUp(Connection c,
                                              Long conversationId,
                                              Integer creatorUserId,
                                              Integer assignedToUserId,
                                              String messageContent) throws SQLException {
        if (conversationId == null || creatorUserId == null || assignedToUserId == null) {
            return;
        }
        if (messageContent == null || messageContent.isBlank()) {
            return;
        }
        String autoTitle = extractAutomaticFollowUpTitle(messageContent);
        if (autoTitle == null) {
            return;
        }
        if (hasSimilarOpenFollowUp(c, conversationId, autoTitle)) {
            return;
        }

        Long followUpId;
        try (PreparedStatement insert = c.prepareStatement("""
                INSERT INTO conversation_follow_up (
                    conversation_id, created_by_user_id, assigned_to_user_id, title, description, status, due_at, done_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NOW(), NOW())
                """, Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, conversationId);
            insert.setInt(2, creatorUserId);
            insert.setInt(3, assignedToUserId);
            insert.setString(4, autoTitle);
            insert.setString(5, "Auto-generated from message: " + messageContent);
            insert.setString(6, FOLLOW_UP_STATUS_OPEN);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    return;
                }
                followUpId = keys.getLong(1);
            }
        }

        createNotification(c, assignedToUserId, creatorUserId, "FOLLOW_UP_AUTO_CREATED",
                "Automatic follow-up created", autoTitle, conversationId, followUpId);
    }

    private boolean hasSimilarOpenFollowUp(Connection c, Long conversationId, String autoTitle) throws SQLException {
        String sql = """
                SELECT 1
                FROM conversation_follow_up
                WHERE conversation_id = ?
                  AND UPPER(status) = 'OPEN'
                  AND LOWER(title) = LOWER(?)
                LIMIT 1
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ps.setString(2, autoTitle);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String extractAutomaticFollowUpTitle(String messageContent) {
        String normalized = messageContent.toLowerCase(Locale.ROOT).trim();
        if (normalized.length() < 3) {
            return null;
        }

        if (containsAny(normalized, "devis", "prix", "tarif", "budget", "quote", "cost")) {
            return "Prepare and send pricing details";
        }
        if (containsAny(normalized, "programme", "itineraire", "itinerary", "plan", "schedule")) {
            return "Share travel program details";
        }
        if (containsAny(normalized, "document", "visa", "passport", "piece", "passeport")) {
            return "Request and verify required documents";
        }
        if (containsAny(normalized, "date", "disponible", "availability", "reservation", "book")) {
            return "Confirm dates and availability";
        }
        if (containsAny(normalized, "payer", "payment", "paiement", "acompte", "invoice", "facture")) {
            return "Follow up on payment step";
        }
        if (containsAny(normalized, "hotel", "transfert", "transfer", "transport", "vol", "flight")) {
            return "Clarify logistics and transport details";
        }
        if (containsAny(normalized, "merci", "thanks", "ok", "d'accord", "oui", "bonjour", "salut", "hello", "hi")) {
            return null;
        }

        if (normalized.contains("?") || containsAny(normalized, "peux", "pouvez", "please", "can you", "est-ce")) {
            return "Respond to customer request";
        }
        return "Follow up on latest customer message";
    }

    private static boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> detectBadWords(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] tokens = text.split("[^\\p{L}\\p{Nd}']+");
        LinkedHashSet<String> detected = new LinkedHashSet<>();
        for (String token : tokens) {
            String normalized = normalizeForModeration(token);
            if (!normalized.isBlank() && BAD_WORDS.contains(normalized)) {
                detected.add(normalized);
            }
        }
        return new ArrayList<>(detected);
    }

    private static ModerationResult moderateBadWords(String text) {
        String input = text == null ? "" : text;
        String[] tokens = input.split("(?<=\\b)|(?=\\b)");
        StringBuilder out = new StringBuilder(input.length());
        LinkedHashSet<String> detected = new LinkedHashSet<>();
        for (String token : tokens) {
            String normalized = normalizeForModeration(token);
            if (!normalized.isBlank() && BAD_WORDS.contains(normalized)) {
                detected.add(normalized);
                out.append("*".repeat(token.length()));
            } else {
                out.append(token);
            }
        }
        return new ModerationResult(out.toString(), new ArrayList<>(detected));
    }

    private record ModerationResult(String sanitizedContent, List<String> detectedBadWords) {
    }

    private static String normalizeForModeration(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        String nfd = Normalizer.normalize(lowered, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}+", "").trim();
    }

    private void assertConsecutiveSpamRule(Long conversationId, Integer senderUserId) throws SQLException {
        if (conversationId == null || senderUserId == null) {
            return;
        }
        String sql = """
                SELECT sender_user_id
                FROM chat_message
                WHERE conversation_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;
        Connection c = DbConnexion.getInstance().getConnection();
        int sameSenderCount = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ps.setInt(2, MAX_CONSECUTIVE_MESSAGES);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int sender = rs.getInt("sender_user_id");
                    if (sender == senderUserId) {
                        sameSenderCount++;
                    } else {
                        break;
                    }
                }
            }
        }
        if (sameSenderCount >= MAX_CONSECUTIVE_MESSAGES) {
            throw new IllegalArgumentException("Anti-spam: maximum " + MAX_CONSECUTIVE_MESSAGES
                    + " messages consecutifs autorises. Attendez une reponse de l'autre utilisateur.");
        }
    }

    private Conversation mapConversation(ResultSet rs) throws SQLException {
        Conversation c = new Conversation();
        c.setId(rs.getLong("id"));
        c.setEventId(rs.getLong("event_id"));
        if (rs.wasNull()) {
            c.setEventId(null);
        }
        c.setTravelerUserId(rs.getInt("traveler_user_id"));
        if (rs.wasNull()) {
            c.setTravelerUserId(null);
        }
        c.setAgencyResponsableUserId(rs.getInt("agency_responsable_user_id"));
        if (rs.wasNull()) {
            c.setAgencyResponsableUserId(null);
        }
        c.setCreatedAt(ts(rs.getTimestamp("created_at")));
        c.setUpdatedAt(ts(rs.getTimestamp("updated_at")));
        c.setLastMessageAt(ts(rs.getTimestamp("last_message_at")));
        c.setEventTitle(rs.getString("event_title"));
        c.setAgencyName(rs.getString("agency_name"));
        c.setTravelerName(rs.getString("traveler_name"));
        return c;
    }

    private ChatMessage mapMessage(ResultSet rs) throws SQLException {
        ChatMessage m = new ChatMessage();
        m.setId(rs.getLong("id"));
        m.setConversationId(rs.getLong("conversation_id"));
        if (rs.wasNull()) {
            m.setConversationId(null);
        }
        m.setSenderUserId(rs.getInt("sender_user_id"));
        if (rs.wasNull()) {
            m.setSenderUserId(null);
        }
        m.setContent(rs.getString("content"));
        m.setMessageType(rs.getString("message_type"));
        m.setFileUrl(rs.getString("file_url"));
        m.setCreatedAt(ts(rs.getTimestamp("created_at")));
        m.setSenderDisplayName(rs.getString("sender_display_name"));
        return m;
    }

    private ConversationFollowUp mapFollowUp(ResultSet rs) throws SQLException {
        ConversationFollowUp f = new ConversationFollowUp();
        f.setId(rs.getLong("id"));
        f.setConversationId(rs.getLong("conversation_id"));
        if (rs.wasNull()) {
            f.setConversationId(null);
        }
        f.setCreatedByUserId(rs.getInt("created_by_user_id"));
        if (rs.wasNull()) {
            f.setCreatedByUserId(null);
        }
        f.setAssignedToUserId(rs.getInt("assigned_to_user_id"));
        if (rs.wasNull()) {
            f.setAssignedToUserId(null);
        }
        f.setTitle(rs.getString("title"));
        f.setDescription(rs.getString("description"));
        f.setStatus(rs.getString("status"));
        f.setDueAt(ts(rs.getTimestamp("due_at")));
        f.setDoneAt(ts(rs.getTimestamp("done_at")));
        f.setCreatedAt(ts(rs.getTimestamp("created_at")));
        f.setUpdatedAt(ts(rs.getTimestamp("updated_at")));
        f.setAssignedToDisplayName(rs.getString("assigned_to_display_name"));
        return f;
    }

    private MessagingNotification mapNotification(ResultSet rs) throws SQLException {
        MessagingNotification n = new MessagingNotification();
        n.setId(rs.getLong("id"));
        n.setRecipientUserId(rs.getInt("recipient_user_id"));
        if (rs.wasNull()) {
            n.setRecipientUserId(null);
        }
        n.setActorUserId(rs.getInt("actor_user_id"));
        if (rs.wasNull()) {
            n.setActorUserId(null);
        }
        n.setType(rs.getString("type"));
        n.setTitle(rs.getString("title"));
        n.setBody(rs.getString("body"));
        n.setConversationId(rs.getLong("conversation_id"));
        if (rs.wasNull()) {
            n.setConversationId(null);
        }
        n.setFollowUpId(rs.getLong("follow_up_id"));
        if (rs.wasNull()) {
            n.setFollowUpId(null);
        }
        n.setIsRead(rs.getBoolean("is_read"));
        n.setCreatedAt(ts(rs.getTimestamp("created_at")));
        n.setReadAt(ts(rs.getTimestamp("read_at")));
        n.setActorDisplayName(rs.getString("actor_display_name"));
        return n;
    }

    private int countScalar(String sql) throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private static LocalDateTime ts(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
