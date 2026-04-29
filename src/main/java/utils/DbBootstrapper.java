package utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Minimal bootstrap for feature tables that are managed by this JavaFX app.
 */
public final class DbBootstrapper {

    private DbBootstrapper() {
    }

    public static void ensureSchema(Connection connection) {
        if (connection == null) {
            return;
        }
        executeIgnoreSql(connection, "image_asset", """
                CREATE TABLE IF NOT EXISTS image_asset (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    path VARCHAR(500) NULL,
                    thumbnail_path VARCHAR(500) NULL,
                    file_name VARCHAR(255) NULL,
                    mime_type VARCHAR(100) NOT NULL,
                    size_bytes BIGINT NULL,
                    data LONGBLOB NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                    owner_id INT NULL
                )
                """);

        executeIgnoreSql(connection, "user", """
                CREATE TABLE IF NOT EXISTS `user` (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(180) NOT NULL,
                    email VARCHAR(255) NULL,
                    password VARCHAR(255) NULL,
                    roles JSON NOT NULL,
                    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
                    is_active TINYINT(1) NULL DEFAULT 1,
                    profile_picture VARCHAR(255) NULL,
                    profile_image_id BIGINT NULL,
                    face_verified TINYINT(1) NULL DEFAULT 0,
                    face_verified_at DATETIME NULL,
                    email_verified TINYINT(1) NULL DEFAULT 0,
                    phone VARCHAR(32) NULL,
                    reset_token VARCHAR(64) NULL,
                    reset_token_expires_at DATETIME NULL,
                    points INT NULL DEFAULT 0,
                    UNIQUE KEY uk_user_username (username),
                    UNIQUE KEY uk_user_email (email),
                    CONSTRAINT fk_user_profile_image FOREIGN KEY (profile_image_id) REFERENCES image_asset(id) ON DELETE SET NULL
                )
                """);

        executeIgnoreSql(connection, "image_asset_owner_fk", """
                ALTER TABLE image_asset
                ADD CONSTRAINT fk_image_asset_owner
                FOREIGN KEY (owner_id) REFERENCES `user`(id) ON DELETE SET NULL
                """);

        executeIgnoreSql(connection, "agency_account", """
                CREATE TABLE IF NOT EXISTS agency_account (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    agency_name VARCHAR(255) NOT NULL,
                    description LONGTEXT NOT NULL,
                    website_url VARCHAR(500) NULL,
                    phone VARCHAR(50) NULL,
                    address VARCHAR(500) NULL,
                    country VARCHAR(2) NULL,
                    latitude DOUBLE NULL,
                    longitude DOUBLE NULL,
                    verified TINYINT(1) NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    responsable_id INT NOT NULL,
                    cover_image_id BIGINT NULL,
                    agency_profile_image_id BIGINT NULL,
                    CONSTRAINT fk_agency_account_user FOREIGN KEY (responsable_id) REFERENCES `user`(id) ON DELETE CASCADE,
                    CONSTRAINT fk_agency_cover_image FOREIGN KEY (cover_image_id) REFERENCES image_asset(id) ON DELETE SET NULL,
                    CONSTRAINT fk_agency_profile_image FOREIGN KEY (agency_profile_image_id) REFERENCES image_asset(id) ON DELETE SET NULL
                )
                """);

        executeIgnoreSql(connection, "agency_admin_application", """
                CREATE TABLE IF NOT EXISTS agency_admin_application (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    agency_name_requested VARCHAR(255) NOT NULL,
                    country VARCHAR(2) NULL,
                    message_to_admin TEXT NULL,
                    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    applicant_id INT NOT NULL,
                    reviewed_by_id INT NULL,
                    reviewed_at DATETIME(6) NULL,
                    review_note TEXT NULL,
                    created_agency_account_id BIGINT NULL,
                    CONSTRAINT fk_agency_app_applicant FOREIGN KEY (applicant_id) REFERENCES `user`(id) ON DELETE CASCADE,
                    CONSTRAINT fk_agency_app_reviewer FOREIGN KEY (reviewed_by_id) REFERENCES `user`(id) ON DELETE SET NULL,
                    CONSTRAINT fk_agency_app_agency FOREIGN KEY (created_agency_account_id) REFERENCES agency_account(id) ON DELETE SET NULL
                )
                """);

        executeIgnoreSql(connection, "travel_event", """
                CREATE TABLE IF NOT EXISTS travel_event (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    title VARCHAR(180) NOT NULL,
                    description TEXT NULL,
                    location VARCHAR(180) NOT NULL,
                    event_date DATETIME NOT NULL,
                    max_participants INT NOT NULL DEFAULT 100,
                    image_path VARCHAR(512) NULL,
                    status VARCHAR(24) NOT NULL DEFAULT 'APPROVED',
                    created_by_user_id INT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    CONSTRAINT fk_travel_event_user FOREIGN KEY (created_by_user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "travel_event_status", """
                ALTER TABLE travel_event ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'APPROVED'
                """);

        executeIgnoreSql(connection, "event_participation", """
                CREATE TABLE IF NOT EXISTS event_participation (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    event_id BIGINT NOT NULL,
                    user_id INT NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PARTICIPATION',
                    requester_name VARCHAR(120) NULL,
                    contact_phone VARCHAR(30) NULL,
                    request_note TEXT NULL,
                    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    CONSTRAINT uk_event_user UNIQUE (event_id, user_id),
                    CONSTRAINT fk_event_participation_event FOREIGN KEY (event_id) REFERENCES travel_event(id) ON DELETE CASCADE,
                    CONSTRAINT fk_event_participation_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "event_participation_requester_name", """
                ALTER TABLE event_participation ADD COLUMN requester_name VARCHAR(120) NULL
                """);
        executeIgnoreSql(connection, "event_participation_contact_phone", """
                ALTER TABLE event_participation ADD COLUMN contact_phone VARCHAR(30) NULL
                """);
        executeIgnoreSql(connection, "event_participation_request_note", """
                ALTER TABLE event_participation ADD COLUMN request_note TEXT NULL
                """);
        executeIgnoreSql(connection, "event_participation_status_v32", """
                ALTER TABLE event_participation MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PARTICIPATION'
                """);

        executeIgnoreSql(connection, "event_participation_legacy_status", """
                UPDATE event_participation SET status = 'APPROVED_PARTICIPATION' WHERE UPPER(TRIM(status)) = 'PARTICIPATING'
                """);

        executeIgnoreSql(connection, "event_sponsorship", """
                CREATE TABLE IF NOT EXISTS event_sponsorship (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    nom VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL,
                    telephone VARCHAR(255) NULL,
                    montant_contribution DECIMAL(10,3) NOT NULL,
                    message LONGTEXT NULL,
                    statut VARCHAR(50) NOT NULL DEFAULT 'PENDING_SPONSOR',
                    is_paid TINYINT(1) NOT NULL DEFAULT 0,
                    sponsored_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    evenement_id BIGINT NOT NULL,
                    user_id INT NULL,
                    CONSTRAINT fk_event_sponsorship_event FOREIGN KEY (evenement_id) REFERENCES travel_event(id) ON DELETE CASCADE,
                    CONSTRAINT fk_event_sponsorship_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE SET NULL
                )
                """);

        executeIgnoreSql(connection, "event_like", """
                CREATE TABLE IF NOT EXISTS event_like (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    event_id BIGINT NOT NULL,
                    user_id INT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_event_like UNIQUE (event_id, user_id),
                    CONSTRAINT fk_event_like_event FOREIGN KEY (event_id) REFERENCES travel_event(id) ON DELETE CASCADE,
                    CONSTRAINT fk_event_like_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "event_comment", """
                CREATE TABLE IF NOT EXISTS event_comment (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    event_id BIGINT NOT NULL,
                    user_id INT NOT NULL,
                    content TEXT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                    CONSTRAINT fk_event_comment_event FOREIGN KEY (event_id) REFERENCES travel_event(id) ON DELETE CASCADE,
                    CONSTRAINT fk_event_comment_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "travel_offer", """
                CREATE TABLE IF NOT EXISTS travel_offer (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    title VARCHAR(180) NOT NULL,
                    countries JSON NOT NULL,
                    description TEXT NULL,
                    departure_date DATETIME NULL,
                    return_date DATETIME NULL,
                    price DOUBLE NULL,
                    currency VARCHAR(16) NULL,
                    available_seats INT NULL,
                    image VARCHAR(500) NULL,
                    agency_id BIGINT NULL,
                    created_by_id INT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    approval_status VARCHAR(24) NOT NULL DEFAULT 'pending',
                    CONSTRAINT fk_travel_offer_agency FOREIGN KEY (agency_id) REFERENCES agency_account(id) ON DELETE SET NULL,
                    CONSTRAINT fk_travel_offer_user FOREIGN KEY (created_by_id) REFERENCES `user`(id) ON DELETE SET NULL
                )
                """);

        executeIgnoreSql(connection, "reservation", """
                CREATE TABLE IF NOT EXISTS reservation (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    offer_id BIGINT NOT NULL,
                    user_id INT NOT NULL,
                    contact_info VARCHAR(255) NULL,
                    reserved_seats INT NOT NULL DEFAULT 1,
                    reservation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    status VARCHAR(24) NOT NULL DEFAULT 'pending',
                    is_paid TINYINT(1) NOT NULL DEFAULT 0,
                    CONSTRAINT uk_offer_user UNIQUE (offer_id, user_id),
                    CONSTRAINT fk_reservation_offer FOREIGN KEY (offer_id) REFERENCES travel_offer(id) ON DELETE CASCADE,
                    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "user_app_feedback", """
                CREATE TABLE IF NOT EXISTS user_app_feedback (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id INT NOT NULL,
                    stars TINYINT NOT NULL,
                    note TEXT NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    CONSTRAINT fk_user_app_feedback_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
                    CONSTRAINT chk_user_app_feedback_stars CHECK (stars >= 1 AND stars <= 5)
                )
                """);

        executeIgnoreSql(connection, "agency_post", """
                CREATE TABLE IF NOT EXISTS agency_post (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    title VARCHAR(255) NOT NULL,
                    content LONGTEXT NOT NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                    agency_id BIGINT NOT NULL,
                    author_id INT NOT NULL,
                    CONSTRAINT fk_agency_post_agency FOREIGN KEY (agency_id) REFERENCES agency_account(id) ON DELETE CASCADE,
                    CONSTRAINT fk_agency_post_author FOREIGN KEY (author_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "agency_post_images", """
                CREATE TABLE IF NOT EXISTS agency_post_images (
                    agency_post_id BIGINT NOT NULL,
                    image_asset_id BIGINT NOT NULL,
                    PRIMARY KEY (agency_post_id, image_asset_id),
                    CONSTRAINT fk_ap_images_post FOREIGN KEY (agency_post_id) REFERENCES agency_post(id) ON DELETE CASCADE,
                    CONSTRAINT fk_ap_images_asset FOREIGN KEY (image_asset_id) REFERENCES image_asset(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "agency_post_comment", """
                CREATE TABLE IF NOT EXISTS agency_post_comment (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    content LONGTEXT NOT NULL,
                    created_at DATETIME NOT NULL,
                    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                    agency_post_id BIGINT NOT NULL,
                    author_id INT NOT NULL,
                    CONSTRAINT fk_ap_comment_post FOREIGN KEY (agency_post_id) REFERENCES agency_post(id) ON DELETE CASCADE,
                    CONSTRAINT fk_ap_comment_author FOREIGN KEY (author_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "agency_post_like", """
                CREATE TABLE IF NOT EXISTS agency_post_like (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    created_at DATETIME NOT NULL,
                    agency_post_id BIGINT NOT NULL,
                    user_id INT NOT NULL,
                    CONSTRAINT uk_agency_post_like UNIQUE (agency_post_id, user_id),
                    CONSTRAINT fk_ap_like_post FOREIGN KEY (agency_post_id) REFERENCES agency_post(id) ON DELETE CASCADE,
                    CONSTRAINT fk_ap_like_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "post_latitude", """
                ALTER TABLE post ADD COLUMN latitude DOUBLE NULL
                """);
        executeIgnoreSql(connection, "post_longitude", """
                ALTER TABLE post ADD COLUMN longitude DOUBLE NULL
                """);
        executeIgnoreSql(connection, "comment_sentiment", """
                ALTER TABLE comment ADD COLUMN sentiment VARCHAR(16) NOT NULL DEFAULT 'NEUTRAL'
                """);
        executeIgnoreSql(connection, "user_reputation_score", """
                ALTER TABLE `user` ADD COLUMN reputation_score INT NOT NULL DEFAULT 0
                """);
        executeIgnoreSql(connection, "user_level", """
                ALTER TABLE `user` ADD COLUMN level VARCHAR(32) NOT NULL DEFAULT 'NOVICE'
                """);
        executeIgnoreSql(connection, "user_points", """
                ALTER TABLE `user` ADD COLUMN points INT NULL DEFAULT 0
                """);
        executeIgnoreSql(connection, "post_trending_score", """
                ALTER TABLE post ADD COLUMN trending_score DOUBLE NOT NULL DEFAULT 0
                """);
        executeIgnoreSql(connection, "post_is_trending", """
                ALTER TABLE post ADD COLUMN is_trending TINYINT(1) NOT NULL DEFAULT 0
                """);
        executeIgnoreSql(connection, "post_trending_growth_pct", """
                ALTER TABLE post ADD COLUMN trending_growth_pct DOUBLE NOT NULL DEFAULT 0
                """);
        executeIgnoreSql(connection, "post_admin_pinned", """
                ALTER TABLE post ADD COLUMN admin_pinned TINYINT(1) NOT NULL DEFAULT 0
                """);
        executeIgnoreSql(connection, "post_trend_excluded", """
                ALTER TABLE post ADD COLUMN trend_excluded TINYINT(1) NOT NULL DEFAULT 0
                """);
        executeIgnoreSql(connection, "reputation_log", """
                CREATE TABLE IF NOT EXISTS reputation_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id INT NOT NULL,
                    action VARCHAR(80) NOT NULL,
                    points INT NOT NULL,
                    reference_type VARCHAR(60) NULL,
                    reference_id BIGINT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_reputation_log_user_created (user_id, created_at),
                    CONSTRAINT fk_reputation_log_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);
        executeIgnoreSql(connection, "trending_history", """
                CREATE TABLE IF NOT EXISTS trending_history (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    post_id BIGINT NOT NULL,
                    score DOUBLE NOT NULL,
                    growth_pct DOUBLE NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_trending_history_post_created (post_id, created_at),
                    CONSTRAINT fk_trending_history_post FOREIGN KEY (post_id) REFERENCES post(id) ON DELETE CASCADE
                )
                """);
        executeIgnoreSql(connection, "comment_like", """
                CREATE TABLE IF NOT EXISTS comment_like (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id INT NOT NULL,
                    comment_id BIGINT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_comment_like (user_id, comment_id),
                    INDEX idx_comment_like_comment (comment_id),
                    CONSTRAINT fk_comment_like_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
                    CONSTRAINT fk_comment_like_comment FOREIGN KEY (comment_id) REFERENCES comment(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "conversation", """
                CREATE TABLE IF NOT EXISTS conversation (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    event_id BIGINT NULL,
                    traveler_user_id INT NOT NULL,
                    agency_responsable_user_id INT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    last_message_at DATETIME NULL,
                    CONSTRAINT uk_conversation_unique UNIQUE (event_id, traveler_user_id, agency_responsable_user_id),
                    CONSTRAINT fk_conversation_event FOREIGN KEY (event_id) REFERENCES travel_event(id) ON DELETE CASCADE,
                    CONSTRAINT fk_conversation_traveler FOREIGN KEY (traveler_user_id) REFERENCES `user`(id) ON DELETE CASCADE,
                    CONSTRAINT fk_conversation_agency_responsable FOREIGN KEY (agency_responsable_user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);
        executeIgnoreSql(connection, "conversation_event_nullable", """
                ALTER TABLE conversation MODIFY COLUMN event_id BIGINT NULL
                """);
        executeIgnoreSql(connection, "chat_message", """
                CREATE TABLE IF NOT EXISTS chat_message (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    conversation_id BIGINT NOT NULL,
                    sender_user_id INT NOT NULL,
                    content TEXT NOT NULL,
                    message_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
                    file_url VARCHAR(512) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
                    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);
        executeIgnoreSql(connection, "chat_message_add_message_type", """
                ALTER TABLE chat_message
                ADD COLUMN message_type VARCHAR(16) NOT NULL DEFAULT 'TEXT'
                """);
        executeIgnoreSql(connection, "chat_message_add_file_url", """
                ALTER TABLE chat_message
                ADD COLUMN file_url VARCHAR(512) NULL
                """);
        executeIgnoreSql(connection, "conversation_follow_up", """
                CREATE TABLE IF NOT EXISTS conversation_follow_up (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    conversation_id BIGINT NOT NULL,
                    created_by_user_id INT NOT NULL,
                    assigned_to_user_id INT NOT NULL,
                    title VARCHAR(180) NOT NULL,
                    description TEXT NULL,
                    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
                    due_at DATETIME NULL,
                    done_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_followup_conv_status (conversation_id, status),
                    INDEX idx_followup_assigned_status (assigned_to_user_id, status),
                    CONSTRAINT fk_followup_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
                    CONSTRAINT fk_followup_created_by FOREIGN KEY (created_by_user_id) REFERENCES `user`(id) ON DELETE CASCADE,
                    CONSTRAINT fk_followup_assigned_to FOREIGN KEY (assigned_to_user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);
        executeIgnoreSql(connection, "messaging_notification", """
                CREATE TABLE IF NOT EXISTS messaging_notification (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    recipient_user_id INT NOT NULL,
                    actor_user_id INT NULL,
                    type VARCHAR(40) NOT NULL,
                    title VARCHAR(180) NOT NULL,
                    body TEXT NULL,
                    conversation_id BIGINT NULL,
                    follow_up_id BIGINT NULL,
                    is_read TINYINT(1) NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    read_at DATETIME NULL,
                    INDEX idx_msg_notif_recipient_read (recipient_user_id, is_read, created_at),
                    CONSTRAINT fk_msg_notif_recipient FOREIGN KEY (recipient_user_id) REFERENCES `user`(id) ON DELETE CASCADE,
                    CONSTRAINT fk_msg_notif_actor FOREIGN KEY (actor_user_id) REFERENCES `user`(id) ON DELETE SET NULL,
                    CONSTRAINT fk_msg_notif_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
                    CONSTRAINT fk_msg_notif_followup FOREIGN KEY (follow_up_id) REFERENCES conversation_follow_up(id) ON DELETE SET NULL
                )
                """);
    }

    private static void executeIgnoreSql(Connection connection, String label, String sql) {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            // Keep startup resilient in dev environments with limited DB privileges.
            System.err.println("DB bootstrap skipped for " + label + ": " + e.getMessage());
        }
    }
}
