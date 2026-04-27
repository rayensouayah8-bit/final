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
                    created_by_user_id INT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    CONSTRAINT fk_travel_event_user FOREIGN KEY (created_by_user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "event_participation", """
                CREATE TABLE IF NOT EXISTS event_participation (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    event_id BIGINT NOT NULL,
                    user_id INT NOT NULL,
                    status VARCHAR(24) NOT NULL DEFAULT 'PARTICIPATING',
                    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    CONSTRAINT uk_event_user UNIQUE (event_id, user_id),
                    CONSTRAINT fk_event_participation_event FOREIGN KEY (event_id) REFERENCES travel_event(id) ON DELETE CASCADE,
                    CONSTRAINT fk_event_participation_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
                )
                """);

        executeIgnoreSql(connection, "event_sponsorship", """
                CREATE TABLE IF NOT EXISTS event_sponsorship (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    nom VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL,
                    telephone VARCHAR(255) NULL,
                    montant_contribution DECIMAL(10,3) NOT NULL,
                    message LONGTEXT NULL,
                    statut VARCHAR(50) NOT NULL DEFAULT 'en_attente',
                    is_paid TINYINT(1) NOT NULL DEFAULT 0,
                    sponsored_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    evenement_id BIGINT NOT NULL,
                    user_id INT NULL,
                    CONSTRAINT fk_event_sponsorship_event FOREIGN KEY (evenement_id) REFERENCES travel_event(id) ON DELETE CASCADE,
                    CONSTRAINT fk_event_sponsorship_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE SET NULL
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
