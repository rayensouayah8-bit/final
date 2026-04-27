package services.gestionagences;

import enums.gestionagences.AgencyApplicationStatus;
import models.gestionagences.AgencyAccount;
import models.gestionagences.AgencyAdminApplication;
import services.gestionutilisateurs.UserService;
import utils.DbConnexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Logique demandes d'agence + approbation / rejet admin (aligne sur {@code AdminController} Symfony).
 */
public class AgencyAdminApplicationService {

    private final UserService userService;
    private final AgencyAccountService agencyAccountService;

    public AgencyAdminApplicationService() {
        this(new UserService(), new AgencyAccountService());
    }

    public AgencyAdminApplicationService(UserService userService, AgencyAccountService agencyAccountService) {
        this.userService = userService;
        this.agencyAccountService = agencyAccountService;
    }

    private static final String INSERT = """
            INSERT INTO agency_admin_application (
                status, agency_name_requested, country, message_to_admin, requested_at,
                applicant_id, reviewed_by_id, reviewed_at, review_note, created_agency_account_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE = """
            UPDATE agency_admin_application SET
                status = ?, agency_name_requested = ?, country = ?, message_to_admin = ?, requested_at = ?,
                applicant_id = ?, reviewed_by_id = ?, reviewed_at = ?, review_note = ?, created_agency_account_id = ?
            WHERE id = ?
            """;

    private static final String SELECT_PENDING = """
            SELECT id, status, agency_name_requested, country, message_to_admin, requested_at,
                   reviewed_at, review_note, applicant_id, reviewed_by_id, created_agency_account_id
            FROM agency_admin_application WHERE status = 'PENDING' ORDER BY requested_at DESC
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, status, agency_name_requested, country, message_to_admin, requested_at,
                   reviewed_at, review_note, applicant_id, reviewed_by_id, created_agency_account_id
            FROM agency_admin_application WHERE id = ?
            """;

    private static final String SELECT_LATEST_BY_APPLICANT = """
            SELECT id, status, agency_name_requested, country, message_to_admin, requested_at,
                   reviewed_at, review_note, applicant_id, reviewed_by_id, created_agency_account_id
            FROM agency_admin_application
            WHERE applicant_id = ?
            ORDER BY requested_at DESC, id DESC
            LIMIT 1
            """;

    /**
     * Soumet une nouvelle demande (statut {@link AgencyApplicationStatus#PENDING}).
     */
    public void submit(AgencyAdminApplication application) throws SQLException {
        if (application.getApplicantId() == null) {
            throw new IllegalArgumentException("applicant_id is required.");
        }
        if (application.getAgencyNameRequested() == null || application.getAgencyNameRequested().isBlank()) {
            throw new IllegalArgumentException("Requested agency name is required.");
        }
        String countryRaw = application.getCountry() != null ? application.getCountry().trim() : "";
        if (countryRaw.isEmpty()) {
            throw new IllegalArgumentException("Country is required (2-letter ISO code, e.g. TN).");
        }
        if (!countryRaw.matches("(?i)[a-z]{2}")) {
            throw new IllegalArgumentException("Le pays doit être un code ISO à 2 lettres.");
        }
        application.setCountry(countryRaw.toUpperCase(Locale.ROOT));

        application.setStatus(AgencyApplicationStatus.PENDING);
        application.setRequestedAt(LocalDateTime.now());
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            bindFull(application, ps, false);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    application.setId(keys.getLong(1));
                }
            }
        }
    }

    public List<AgencyAdminApplication> findPending() throws SQLException {
        Connection c = DbConnexion.getInstance().getConnection();
        List<AgencyAdminApplication> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_PENDING);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Optional<AgencyAdminApplication> get(Long id) throws SQLException {
        if (id == null) {
            return Optional.empty();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<AgencyAdminApplication> findLatestByApplicant(Integer applicantId) throws SQLException {
        if (applicantId == null) {
            return Optional.empty();
        }
        Connection c = DbConnexion.getInstance().getConnection();
        try (PreparedStatement ps = c.prepareStatement(SELECT_LATEST_BY_APPLICANT)) {
            ps.setInt(1, applicantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Approuve une demande en attente : cree l'{@link AgencyAccount} si besoin, lie la demande, ajoute {@code ROLE_AGENCY_ADMIN} au demandeur.
     */
    public void approve(Long applicationId, Integer reviewerUserId) throws SQLException {
        if (applicationId == null || reviewerUserId == null) {
            throw new IllegalArgumentException("applicationId and reviewerUserId are required.");
        }
        Optional<AgencyAdminApplication> opt = get(applicationId);
        if (opt.isEmpty() || opt.get().getStatus() != AgencyApplicationStatus.PENDING) {
            throw new IllegalArgumentException("Request not found or already processed.");
        }
        AgencyAdminApplication app = opt.get();
        Connection conn = DbConnexion.getInstance().getConnection();
        boolean prev = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            AgencyAccount agency = agencyAccountService.findByResponsableId(app.getApplicantId()).orElse(null);
            if (agency == null) {
                agency = new AgencyAccount();
                agency.setAgencyName(app.getAgencyNameRequested());
                agency.setDescription("Describe your agency here.");
                agency.setResponsableId(app.getApplicantId());
                agency.setVerified(Boolean.FALSE);
                mergeIsoCountryFromApplication(agency, app);
                agencyAccountService.insert(agency);
            } else {
                mergeIsoCountryFromApplication(agency, app);
                agencyAccountService.update(agency);
            }
            app.setStatus(AgencyApplicationStatus.APPROVED);
            app.setReviewedById(reviewerUserId);
            app.setReviewedAt(LocalDateTime.now());
            app.setCreatedAgencyAccountId(agency.getId());
            updateRow(conn, app);

            userService.addAgencyAdminRole(app.getApplicantId());
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e instanceof SQLException ? (SQLException) e : new SQLException(e.getMessage(), e);
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    /**
     * Rejete une demande (note admin optionnelle).
     */
    public void reject(Long applicationId, Integer reviewerUserId, String reviewNote) throws SQLException {
        if (applicationId == null || reviewerUserId == null) {
            throw new IllegalArgumentException("applicationId and reviewerUserId are required.");
        }
        Optional<AgencyAdminApplication> opt = get(applicationId);
        if (opt.isEmpty() || opt.get().getStatus() != AgencyApplicationStatus.PENDING) {
            throw new IllegalArgumentException("Request not found or already processed.");
        }
        AgencyAdminApplication app = opt.get();
        app.setStatus(AgencyApplicationStatus.REJECTED);
        app.setReviewedById(reviewerUserId);
        app.setReviewedAt(LocalDateTime.now());
        app.setReviewNote(reviewNote);
        Connection c = DbConnexion.getInstance().getConnection();
        updateRow(c, app);
    }

    /**
     * Copies ISO-3166-1 alpha-2 from the approved application (proposal country choice) onto the agency row.
     */
    private static void mergeIsoCountryFromApplication(AgencyAccount agency, AgencyAdminApplication application) {
        if (application == null || agency == null) {
            return;
        }
        String c = application.getCountry();
        if (c == null || c.trim().isEmpty()) {
            return;
        }
        agency.setCountry(c.trim().toUpperCase(Locale.ROOT));
    }

    private void updateRow(Connection c, AgencyAdminApplication app) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(UPDATE)) {
            bindFull(app, ps, true);
        }
    }

    private void bindFull(AgencyAdminApplication a, PreparedStatement ps, boolean includeId) throws SQLException {
        int i = 1;
        ps.setString(i++, a.getStatus().asDb());
        ps.setString(i++, a.getAgencyNameRequested());
        nullableString(ps, i++, a.getCountry());
        nullableString(ps, i++, a.getMessageToAdmin());
        ps.setTimestamp(i++, Timestamp.valueOf(a.getRequestedAt() != null ? a.getRequestedAt() : LocalDateTime.now()));
        ps.setInt(i++, a.getApplicantId());
        if (a.getReviewedById() == null) {
            ps.setNull(i++, Types.INTEGER);
        } else {
            ps.setInt(i++, a.getReviewedById());
        }
        if (a.getReviewedAt() == null) {
            ps.setNull(i++, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(i++, Timestamp.valueOf(a.getReviewedAt()));
        }
        nullableString(ps, i++, a.getReviewNote());
        if (a.getCreatedAgencyAccountId() == null) {
            ps.setNull(i++, Types.BIGINT);
        } else {
            ps.setLong(i++, a.getCreatedAgencyAccountId());
        }
        if (includeId) {
            ps.setLong(i, a.getId());
            ps.executeUpdate();
        }
    }

    private static void nullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private AgencyAdminApplication mapRow(ResultSet rs) throws SQLException {
        AgencyAdminApplication a = new AgencyAdminApplication();
        a.setId(rs.getLong("id"));
        a.setStatus(AgencyApplicationStatus.fromDb(rs.getString("status")));
        a.setAgencyNameRequested(rs.getString("agency_name_requested"));
        a.setCountry(rs.getString("country"));
        if (rs.wasNull()) {
            a.setCountry(null);
        }
        a.setMessageToAdmin(rs.getString("message_to_admin"));
        if (rs.wasNull()) {
            a.setMessageToAdmin(null);
        }
        Timestamp req = rs.getTimestamp("requested_at");
        a.setRequestedAt(req != null ? req.toLocalDateTime() : null);
        Timestamp rev = rs.getTimestamp("reviewed_at");
        a.setReviewedAt(rs.wasNull() ? null : rev.toLocalDateTime());
        a.setReviewNote(rs.getString("review_note"));
        if (rs.wasNull()) {
            a.setReviewNote(null);
        }
        a.setApplicantId(rs.getInt("applicant_id"));
        int rb = rs.getInt("reviewed_by_id");
        a.setReviewedById(rs.wasNull() ? null : rb);
        long ca = rs.getLong("created_agency_account_id");
        a.setCreatedAgencyAccountId(rs.wasNull() ? null : ca);
        return a;
    }
}
