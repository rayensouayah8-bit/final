package controllers.admin;

import auth.AuthSession;
import enums.gestionutilisateurs.UserRole;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Rectangle;
import models.gestionmessagerie.ConversationFollowUp;
import models.gestionmessagerie.MessagingNotification;
import models.gestionutilisateurs.User;
import services.gestionmessagerie.ConversationService;
import utils.NavigationManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Static admin dashboard shell (Connect Sales style). Gated by {@link AuthSession} roles
 * {@link UserRole#ADMIN} and {@link UserRole#AGENCY_ADMIN}.
 */
public class AdminDashboardController {

    @FXML
    private StackPane chartHost;

    @FXML
    private TableView<InvoiceRow> invoiceTable;

    @FXML
    private TableColumn<InvoiceRow, String> colNum;
    @FXML
    private TableColumn<InvoiceRow, String> colInvoiceNo;
    @FXML
    private TableColumn<InvoiceRow, String> colDate;
    @FXML
    private TableColumn<InvoiceRow, String> colClient;
    @FXML
    private TableColumn<InvoiceRow, String> colSubtotal;
    @FXML
    private TableColumn<InvoiceRow, String> colNet;
    @FXML
    private TableColumn<InvoiceRow, String> colDue;
    @FXML
    private TableColumn<InvoiceRow, String> colStatus;

    @FXML
    private Label avatarInitialsLabel;
    @FXML
    private Label profileNameLabel;

    @FXML
    private VBox profileMenu;

    @FXML
    private Button tabInvoices;
    @FXML
    private Button tabPurchases;
    @FXML
    private Button tabExpenses;
    @FXML
    private Button tabTransactions;
    @FXML
    private Label messagingConversationsCountLabel;
    @FXML
    private Label messagingMessagesCountLabel;
    @FXML
    private Label messagingOpenFollowUpsCountLabel;
    @FXML
    private VBox messagingRecentFollowUpsBox;
    @FXML
    private VBox messagingRecentNotificationsBox;

    private final ConversationService conversationService = new ConversationService();

    @FXML
    private void initialize() {
        User user = AuthSession.getCurrentUser().orElse(null);
        if (user == null) {
            NavigationManager.getInstance().showWelcome();
            return;
        }
        if (!canAccessAdminDashboard(user)) {
            NavigationManager.getInstance().showSignedInHome();
            return;
        }
        bindProfile(user);
        buildSemiDonutChart();
        wireInvoiceTable();
        loadMessagingMonitoring();
    }

    private void loadMessagingMonitoring() {
        if (messagingConversationsCountLabel == null || messagingMessagesCountLabel == null
                || messagingOpenFollowUpsCountLabel == null) {
            return;
        }
        try {
            int conversations = conversationService.adminCountConversations();
            int messages = conversationService.adminCountMessages();
            int openFollowUps = conversationService.adminCountOpenFollowUps();

            messagingConversationsCountLabel.setText(String.valueOf(conversations));
            messagingMessagesCountLabel.setText(String.valueOf(messages));
            messagingOpenFollowUpsCountLabel.setText(String.valueOf(openFollowUps));

            renderRecentFollowUps(conversationService.adminRecentFollowUps(6));
            renderRecentNotifications(conversationService.adminRecentNotifications(6));
        } catch (SQLException ex) {
            messagingConversationsCountLabel.setText("-");
            messagingMessagesCountLabel.setText("-");
            messagingOpenFollowUpsCountLabel.setText("-");
            if (messagingRecentFollowUpsBox != null) {
                messagingRecentFollowUpsBox.getChildren().setAll(new Label("Messaging monitor unavailable: " + ex.getMessage()));
            }
            if (messagingRecentNotificationsBox != null) {
                messagingRecentNotificationsBox.getChildren().setAll(new Label("Notifications unavailable."));
            }
        }
    }

    private void renderRecentFollowUps(List<ConversationFollowUp> rows) {
        if (messagingRecentFollowUpsBox == null) {
            return;
        }
        messagingRecentFollowUpsBox.getChildren().clear();
        if (rows.isEmpty()) {
            messagingRecentFollowUpsBox.getChildren().add(new Label("No follow-up created yet."));
            return;
        }
        for (ConversationFollowUp f : rows) {
            String line = safe(f.getTitle(), "Follow-up")
                    + " - " + safe(f.getStatus(), "OPEN")
                    + " - assigned to " + safe(f.getAssignedToDisplayName(), "User");
            Label l = new Label(line);
            l.setWrapText(true);
            l.getStyleClass().add("admin-card-more");
            messagingRecentFollowUpsBox.getChildren().add(l);
        }
    }

    private void renderRecentNotifications(List<MessagingNotification> rows) {
        if (messagingRecentNotificationsBox == null) {
            return;
        }
        messagingRecentNotificationsBox.getChildren().clear();
        if (rows.isEmpty()) {
            messagingRecentNotificationsBox.getChildren().add(new Label("No notifications."));
            return;
        }
        for (MessagingNotification n : rows) {
            String line = safe(n.getTitle(), "Notification");
            if (n.getBody() != null && !n.getBody().isBlank()) {
                line += " - " + n.getBody();
            }
            Label l = new Label(line);
            l.setWrapText(true);
            l.getStyleClass().add("admin-card-more");
            messagingRecentNotificationsBox.getChildren().add(l);
        }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean canAccessAdminDashboard(User user) {
        List<String> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream().anyMatch(r ->
                UserRole.ADMIN.getValue().equals(r) || UserRole.AGENCY_ADMIN.getValue().equals(r));
    }

    private void bindProfile(User user) {
        String display = displayName(user);
        if (profileNameLabel != null) {
            profileNameLabel.setText(display.toUpperCase(Locale.ROOT));
        }
        if (avatarInitialsLabel != null) {
            avatarInitialsLabel.setText(initials(display));
        }
    }

    private static String displayName(User user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername().trim();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            String email = user.getEmail().trim();
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }
        return "Admin";
    }

    private static String initials(String displayName) {
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length >= 2) {
            String a = parts[0].substring(0, 1);
            String b = parts[1].substring(0, 1);
            return (a + b).toUpperCase(Locale.ROOT);
        }
        String compact = displayName.replaceAll("\\s+", "");
        if (compact.length() >= 2) {
            return compact.substring(0, 2).toUpperCase(Locale.ROOT);
        }
        return (compact + "XX").substring(0, 2).toUpperCase(Locale.ROOT);
    }

    private void buildSemiDonutChart() {
        if (chartHost == null) {
            return;
        }
        // PieChart lives in the separate javafx-charts module, which is not reliably resolvable from Maven Central
        // for this project. Canvas + fillArc only needs javafx-graphics (already on the classpath).
        double w = 300;
        double h = 200;
        Canvas canvas = new Canvas(w, h);
        canvas.getStyleClass().add("admin-sales-chart");
        GraphicsContext gc = canvas.getGraphicsContext2D();
        record Slice(String label, double value, Color color) {
        }
        Slice[] slices = {
                new Slice("iPhone 11 Pro", 32, Color.web("#8B80F9")),
                new Slice("Galaxy S21", 24, Color.web("#38bdf8")),
                new Slice("MacBook Air", 22, Color.web("#e2e8f0")),
                new Slice("iPad Pro", 22, Color.web("#166534"))
        };
        double total = 0;
        for (Slice s : slices) {
            total += s.value();
        }
        double cx = w / 2;
        double cy = h / 2 + 8;
        double diam = 200;
        double x = cx - diam / 2;
        double y = cy - diam / 2;
        double start = 180;
        for (Slice s : slices) {
            double sweep = 180 * (s.value() / total);
            gc.setFill(s.color());
            gc.fillArc(x, y, diam, diam, start, sweep, ArcType.ROUND);
            start += sweep;
        }

        Label center = new Label("- 20%");
        center.getStyleClass().add("admin-chart-center-label");
        center.setMouseTransparent(true);

        StackPane holder = new StackPane(canvas, center);
        holder.setAlignment(Pos.TOP_CENTER);
        StackPane.setAlignment(center, Pos.CENTER);
        center.setTranslateY(28);

        double clipW = 300;
        double clipH = 118;
        Rectangle clip = new Rectangle(clipW, clipH);
        holder.setClip(clip);

        chartHost.getChildren().setAll(holder);
    }

    private void wireInvoiceTable() {
        if (invoiceTable == null) {
            return;
        }
        colNum.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("rowNum"));
        colInvoiceNo.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("invoiceNo"));
        colDate.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("invoiceDate"));
        colClient.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("client"));
        colSubtotal.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("subtotal"));
        colNet.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("netTotal"));
        colDue.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalDue"));
        colStatus.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("status"));
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(item);
                    pill.getStyleClass().add("admin-status-pill");
                    setGraphic(pill);
                    setText(null);
                }
            }
        });
        invoiceTable.setItems(FXCollections.observableArrayList(
                new InvoiceRow("1", "INV-24001", "2024-03-02", "Northwind LLC", "1,200.00", "1,140.00", "0.00", "Active"),
                new InvoiceRow("2", "INV-24002", "2024-03-05", "Blue Harbor", "980.00", "931.00", "120.00", "Active"),
                new InvoiceRow("3", "INV-24008", "2024-03-11", "Atlas Tours", "2,450.00", "2,327.50", "0.00", "Active"),
                new InvoiceRow("4", "INV-24012", "2024-03-18", "Cedar Retail", "640.00", "608.00", "608.00", "Active"),
                new InvoiceRow("5", "INV-24019", "2024-03-22", "Orbit Media", "3,100.00", "2,945.00", "0.00", "Active")));
        invoiceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    @FXML
    private void onProfileToggle(MouseEvent event) {
        event.consume();
        if (profileMenu == null) {
            return;
        }
        boolean next = !profileMenu.isVisible();
        profileMenu.setVisible(next);
        profileMenu.setManaged(next);
    }

    @FXML
    private void onProfileMenuProfile() {
        closeProfileMenu();
    }

    @FXML
    private void onProfileMenuSetup() {
        closeProfileMenu();
    }

    @FXML
    private void onProfileMenuLogout() {
        closeProfileMenu();
        AuthSession.clear();
        NavigationManager.getInstance().showWelcome();
    }

    private void closeProfileMenu() {
        if (profileMenu != null) {
            profileMenu.setVisible(false);
            profileMenu.setManaged(false);
        }
    }

    @FXML
    private void onBackToApp(MouseEvent event) {
        event.consume();
        NavigationManager.getInstance().showSignedInHome();
    }

    @FXML
    private void onHamburgerClick(MouseEvent event) {
        event.consume();
    }

    @FXML
    private void onTabInvoices() {
        setActiveTab(tabInvoices);
    }

    @FXML
    private void onTabPurchases() {
        setActiveTab(tabPurchases);
    }

    @FXML
    private void onTabExpenses() {
        setActiveTab(tabExpenses);
    }

    @FXML
    private void onTabTransactions() {
        setActiveTab(tabTransactions);
    }

    @FXML
    private void onRecommandation() {
        NavigationManager.getInstance().showSignedInPosts();
    }

    private void setActiveTab(Button active) {
        Objects.requireNonNull(active);
        for (Button b : List.of(tabInvoices, tabPurchases, tabExpenses, tabTransactions)) {
            if (b == null) {
                continue;
            }
            b.getStyleClass().remove("admin-tab-active");
        }
        if (!active.getStyleClass().contains("admin-tab-active")) {
            active.getStyleClass().add("admin-tab-active");
        }
    }

    /** Row model for the static invoices grid. */
    public static final class InvoiceRow {
        private final String rowNum;
        private final String invoiceNo;
        private final String invoiceDate;
        private final String client;
        private final String subtotal;
        private final String netTotal;
        private final String totalDue;
        private final String status;

        public InvoiceRow(String rowNum, String invoiceNo, String invoiceDate, String client,
                          String subtotal, String netTotal, String totalDue, String status) {
            this.rowNum = rowNum;
            this.invoiceNo = invoiceNo;
            this.invoiceDate = invoiceDate;
            this.client = client;
            this.subtotal = subtotal;
            this.netTotal = netTotal;
            this.totalDue = totalDue;
            this.status = status;
        }

        public String getRowNum() {
            return rowNum;
        }

        public String getInvoiceNo() {
            return invoiceNo;
        }

        public String getInvoiceDate() {
            return invoiceDate;
        }

        public String getClient() {
            return client;
        }

        public String getSubtotal() {
            return subtotal;
        }

        public String getNetTotal() {
            return netTotal;
        }

        public String getTotalDue() {
            return totalDue;
        }

        public String getStatus() {
            return status;
        }
    }
}
