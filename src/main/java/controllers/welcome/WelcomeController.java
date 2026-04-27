package controllers.welcome;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import auth.AuthServices;
import auth.AuthSession;
import enums.gestionutilisateurs.UserRole;
import models.gestionutilisateurs.User;
import models.welcome.GlobeModelFactory;
import utils.NavigationManager;
import utils.PasswordHasher;
import utils.StarfieldHelper;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

public class WelcomeController {
    private static final String PRIMARY_LOGO = "/images/brand/travel-agency-logo.png";
    private static final String FALLBACK_FILE = "../public/assets/img/gallery/travel-agency-logo.png";
    /** Same pattern as {@code LoginController} in Pi-java2. */
    private static final Pattern EMAIL_SIMPLE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private enum AuthDockHighlight {
        MARKETING,
        SIGN_IN,
        SIGN_UP
    }

    private enum AuthSidebarPaneMode {
        MARKETING,
        SIGN_IN,
        SIGN_UP
    }

    private static final Duration AUTH_PANE_CROSSFADE = Duration.millis(360);
    private static final Duration FIELD_GLOW_MS = Duration.millis(280);
    private static final String PROP_FIELD_GLOW_TL = "smartvoyage.fieldGlowTimeline";

    @FXML
    private StackPane root;

    @FXML
    private StackPane authSidebarHost;

    @FXML
    private Region authEdgeAura;

    @FXML
    private VBox authSidebarShell;

    @FXML
    private Region brandUnderlineBar;

    @FXML
    private Pane starfieldPane;

    @FXML
    private ImageView logoImage;

    @FXML
    private AnchorPane globeLayer;

    @FXML
    private Button primaryButton;

    @FXML
    private Button secondaryButton;

    @FXML
    private VBox sidebarMarketing;

    @FXML
    private VBox sidebarSignIn;

    @FXML
    private VBox sidebarSignUp;

    @FXML
    private VBox sidebarFooter;

    @FXML
    private Button sidebarOpenSignInButton;

    @FXML
    private Button sidebarOpenSignUpButton;

    @FXML
    private Button backFromSignInButton;

    @FXML
    private Button backFromSignUpButton;

    @FXML
    private TextField signInEmailField;

    @FXML
    private PasswordField signInPasswordField;

    @FXML
    private Button signInSubmitButton;

    @FXML
    private Label signInMessageLabel;

    @FXML
    private TextField signUpNameField;

    @FXML
    private TextField signUpEmailField;

    @FXML
    private ComboBox<String> signUpAccountTypeCombo;

    @FXML
    private PasswordField signUpPasswordField;

    @FXML
    private Button signUpSubmitButton;

    @FXML
    private Label signUpMessageLabel;

    private AuthSidebarPaneMode authPaneMode = AuthSidebarPaneMode.MARKETING;
    private boolean suppressAuthContentAnimation;
    private Timeline sidebarContentTimeline;
    private Timeline edgeAuraPulseTimeline;
    private Timeline brandUnderlineTimeline;

    @FXML
    private void initialize() {
        loadLogo();
        buildStarfield();
        mountGlobe();
        wireButtonHovers();
        initAuthSidebar();
    }

    private void initAuthSidebar() {
        signUpAccountTypeCombo.getItems().setAll("Traveler", "Agency");
        suppressAuthContentAnimation = true;
        showSidebarMarketing();
        suppressAuthContentAnimation = false;
        startSidebarGlassMotion();
        wireAuthFieldFocusMotion();
    }

    private void showSidebarMarketing() {
        if (suppressAuthContentAnimation) {
            snapPanelsToMode(AuthSidebarPaneMode.MARKETING);
            authPaneMode = AuthSidebarPaneMode.MARKETING;
            refreshAuthDock(AuthDockHighlight.MARKETING);
            clearAuthMessages();
            return;
        }
        if (authPaneMode == AuthSidebarPaneMode.MARKETING) {
            snapPanelsToMode(AuthSidebarPaneMode.MARKETING);
            refreshAuthDock(AuthDockHighlight.MARKETING);
            clearAuthMessages();
            return;
        }
        VBox from = paneFor(authPaneMode);
        crossfadePanels(from, sidebarMarketing, AuthSidebarPaneMode.MARKETING);
        refreshAuthDock(AuthDockHighlight.MARKETING);
        clearAuthMessages();
    }

    private void clearAuthMessages() {
        signInMessageLabel.setText("");
        signUpMessageLabel.setText("");
        signInMessageLabel.getStyleClass().removeAll("smart-auth-message-error", "smart-auth-message-success");
        signUpMessageLabel.getStyleClass().removeAll("smart-auth-message-error", "smart-auth-message-success");
    }

    private void styleAuthMessage(Label label, boolean success) {
        label.getStyleClass().removeAll("smart-auth-message-error", "smart-auth-message-success");
        label.getStyleClass().add(success ? "smart-auth-message-success" : "smart-auth-message-error");
    }

    /**
     * Bottom dock stays fixed; only toggles premium active vs idle chrome per mode.
     */
    private void refreshAuthDock(AuthDockHighlight mode) {
        boolean inActive = mode == AuthDockHighlight.SIGN_IN;
        boolean upActive = mode == AuthDockHighlight.SIGN_UP;
        applyDockChrome(sidebarOpenSignInButton, inActive);
        applyDockChrome(sidebarOpenSignUpButton, upActive);
    }

    private void applyDockChrome(Button btn, boolean active) {
        btn.getStyleClass().removeAll(
                "auth-segment-active",
                "auth-segment-inactive",
                "sv-auth-active",
                "sv-auth-off");
        btn.getStyleClass().add(active ? "auth-segment-active" : "auth-segment-inactive");
    }

    private VBox paneFor(AuthSidebarPaneMode mode) {
        return switch (mode) {
            case MARKETING -> sidebarMarketing;
            case SIGN_IN -> sidebarSignIn;
            case SIGN_UP -> sidebarSignUp;
        };
    }

    private void snapPanelsToMode(AuthSidebarPaneMode mode) {
        VBox[] all = {sidebarMarketing, sidebarSignIn, sidebarSignUp};
        for (VBox v : all) {
            boolean on = v == paneFor(mode);
            v.setManaged(on);
            v.setVisible(on);
            v.setOpacity(1);
            v.setTranslateY(0);
        }
    }

    private void stopSidebarContentMotion() {
        if (sidebarContentTimeline != null) {
            sidebarContentTimeline.stop();
            sidebarContentTimeline.setOnFinished(null);
            sidebarContentTimeline = null;
        }
        snapPanelsToMode(authPaneMode);
    }

    private void crossfadePanels(VBox from, VBox to, AuthSidebarPaneMode newMode) {
        if (from == to) {
            authPaneMode = newMode;
            snapPanelsToMode(newMode);
            return;
        }
        stopSidebarContentMotion();

        to.toFront();

        to.setManaged(true);
        to.setVisible(true);
        to.setOpacity(0);
        to.setTranslateY(10);

        from.setOpacity(1);
        from.setTranslateY(0);

        var ease = Interpolator.SPLINE(0.33, 0, 0.2, 1);
        sidebarContentTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(from.opacityProperty(), from.getOpacity()),
                        new KeyValue(from.translateYProperty(), from.getTranslateY()),
                        new KeyValue(to.opacityProperty(), to.getOpacity()),
                        new KeyValue(to.translateYProperty(), to.getTranslateY())),
                new KeyFrame(AUTH_PANE_CROSSFADE,
                        new KeyValue(from.opacityProperty(), 0, ease),
                        new KeyValue(from.translateYProperty(), -8, ease),
                        new KeyValue(to.opacityProperty(), 1, ease),
                        new KeyValue(to.translateYProperty(), 0, ease))
        );
        sidebarContentTimeline.setOnFinished(ev -> {
            authPaneMode = newMode;
            snapPanelsToMode(newMode);
            sidebarContentTimeline = null;
        });
        sidebarContentTimeline.play();
    }

    private void startSidebarGlassMotion() {
        if (authEdgeAura != null) {
            authEdgeAura.setOpacity(0.5);
            edgeAuraPulseTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(authEdgeAura.opacityProperty(), 0.42)),
                    new KeyFrame(Duration.seconds(2.8), new KeyValue(authEdgeAura.opacityProperty(), 0.68, Interpolator.EASE_BOTH))
            );
            edgeAuraPulseTimeline.setAutoReverse(true);
            edgeAuraPulseTimeline.setCycleCount(Animation.INDEFINITE);
            edgeAuraPulseTimeline.play();
        }

        if (brandUnderlineBar != null) {
            brandUnderlineBar.setScaleX(0.68);
            brandUnderlineTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(brandUnderlineBar.scaleXProperty(), 0.68)),
                    new KeyFrame(Duration.seconds(2.2), new KeyValue(brandUnderlineBar.scaleXProperty(), 1.0, Interpolator.EASE_BOTH))
            );
            brandUnderlineTimeline.setAutoReverse(true);
            brandUnderlineTimeline.setCycleCount(Animation.INDEFINITE);
            brandUnderlineTimeline.play();
        }

        if (authSidebarShell != null && authEdgeAura != null) {
            authSidebarShell.setOnMouseEntered(e -> authEdgeAura.getStyleClass().add("smart-auth-edge-aura-hot"));
            authSidebarShell.setOnMouseExited(e -> authEdgeAura.getStyleClass().remove("smart-auth-edge-aura-hot"));
        }
    }

    private void wireAuthFieldFocusMotion() {
        wireFocusGlow(signInEmailField);
        wireFocusGlow(signInPasswordField);
        wireFocusGlow(signUpNameField);
        wireFocusGlow(signUpEmailField);
        wireFocusGlow(signUpPasswordField);
        wireFocusGlow(signUpAccountTypeCombo);
    }

    private void wireFocusGlow(javafx.scene.Node field) {
        field.focusedProperty().addListener((obs, had, has) -> {
            Object oldTl = field.getProperties().get(PROP_FIELD_GLOW_TL);
            if (oldTl instanceof Timeline t) {
                t.stop();
            }
            field.getProperties().remove(PROP_FIELD_GLOW_TL);

            if (has) {
                DropShadow glow = new DropShadow();
                glow.setRadius(0);
                glow.setSpread(0.22);
                glow.setColor(Color.color(0.58, 0.38, 1.0, 0.0));
                glow.setOffsetX(0);
                glow.setOffsetY(0);
                field.setEffect(glow);
                Timeline in = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 0)),
                        new KeyFrame(FIELD_GLOW_MS,
                                new KeyValue(glow.radiusProperty(), 24, Interpolator.SPLINE(0.33, 0, 0.2, 1)),
                                new KeyValue(glow.colorProperty(), Color.color(0.62, 0.45, 1.0, 0.38), Interpolator.SPLINE(0.33, 0, 0.2, 1)))
                );
                field.getProperties().put(PROP_FIELD_GLOW_TL, in);
                in.setOnFinished(e -> field.getProperties().remove(PROP_FIELD_GLOW_TL));
                in.play();
            } else {
                Effect eff = field.getEffect();
                if (eff instanceof DropShadow glow) {
                    var easeOut = Interpolator.SPLINE(0.33, 0, 0.2, 1);
                    Timeline out = new Timeline(
                            new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), glow.getRadius())),
                            new KeyFrame(FIELD_GLOW_MS,
                                    new KeyValue(glow.radiusProperty(), 0, easeOut),
                                    new KeyValue(glow.colorProperty(), Color.color(0.62, 0.45, 1.0, 0.0), easeOut))
                    );
                    field.getProperties().put(PROP_FIELD_GLOW_TL, out);
                    out.setOnFinished(e -> {
                        field.setEffect(null);
                        field.getProperties().remove(PROP_FIELD_GLOW_TL);
                    });
                    out.play();
                } else {
                    field.setEffect(null);
                }
            }
        });
    }

    @FXML
    private void onOpenSignIn() {
        if (authPaneMode == AuthSidebarPaneMode.SIGN_IN) {
            return;
        }
        VBox from = paneFor(authPaneMode);
        crossfadePanels(from, sidebarSignIn, AuthSidebarPaneMode.SIGN_IN);
        clearAuthMessages();
        refreshAuthDock(AuthDockHighlight.SIGN_IN);
    }

    @FXML
    private void onOpenSignUp() {
        if (authPaneMode == AuthSidebarPaneMode.SIGN_UP) {
            return;
        }
        VBox from = paneFor(authPaneMode);
        crossfadePanels(from, sidebarSignUp, AuthSidebarPaneMode.SIGN_UP);
        clearAuthMessages();
        refreshAuthDock(AuthDockHighlight.SIGN_UP);
    }

    @FXML
    private void onSidebarBack() {
        showSidebarMarketing();
    }

    @FXML
    private void onSignInSubmit() {
        clearAuthMessages();
        String email = signInEmailField.getText() != null ? signInEmailField.getText().trim() : "";
        String password = signInPasswordField.getText() != null ? signInPasswordField.getText() : "";

        if (email.isEmpty() && password.isEmpty()) {
            styleAuthMessage(signInMessageLabel, false);
            signInMessageLabel.setText("Email and password are required.");
            return;
        }
        if (email.isEmpty()) {
            styleAuthMessage(signInMessageLabel, false);
            signInMessageLabel.setText("Email is required.");
            return;
        }
        if (!EMAIL_SIMPLE.matcher(email).matches()) {
            styleAuthMessage(signInMessageLabel, false);
            signInMessageLabel.setText("Invalid email format.");
            return;
        }
        if (password.isEmpty()) {
            styleAuthMessage(signInMessageLabel, false);
            signInMessageLabel.setText("Password is required.");
            return;
        }

        try {
            var userService = AuthServices.userService();
            Optional<User> existing = userService.findByEmail(email.toLowerCase(Locale.ROOT));
            if (existing.isEmpty()) {
                styleAuthMessage(signInMessageLabel, false);
                signInMessageLabel.setText("No account found with this email.");
                return;
            }
            if (!PasswordHasher.matches(password, existing.get().getPassword())) {
                styleAuthMessage(signInMessageLabel, false);
                signInMessageLabel.setText("Incorrect password.");
                return;
            }

            Optional<User> user = userService.login(email, password);
            if (user.isPresent()) {
                AuthSession.setCurrentUser(user.get());
                styleAuthMessage(signInMessageLabel, true);
                signInMessageLabel.setText("Sign-in successful. Welcome, " + user.get().getUsername() + ".");
                NavigationManager.getInstance().showSignedInHome();
            } else {
                styleAuthMessage(signInMessageLabel, false);
                signInMessageLabel.setText("Sign-in failed. Please verify your credentials.");
            }
        } catch (SQLException e) {
            styleAuthMessage(signInMessageLabel, false);
            signInMessageLabel.setText("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void onSignUpSubmit() {
        clearAuthMessages();
        String name = signUpNameField.getText() != null ? signUpNameField.getText().trim() : "";
        String email = signUpEmailField.getText() != null ? signUpEmailField.getText().trim() : "";
        String type = signUpAccountTypeCombo.getValue();
        String password = signUpPasswordField.getText() != null ? signUpPasswordField.getText() : "";

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || type == null) {
            styleAuthMessage(signUpMessageLabel, false);
            if (name.isEmpty() && email.isEmpty() && password.isEmpty()) {
                signUpMessageLabel.setText("Name, email, and password are required.");
            } else if (name.isEmpty()) {
                signUpMessageLabel.setText("Name is required.");
            } else if (email.isEmpty()) {
                signUpMessageLabel.setText("Email is required.");
            } else if (password.isEmpty()) {
                signUpMessageLabel.setText("Password is required.");
            } else {
                signUpMessageLabel.setText("Please choose account type.");
            }
            return;
        }
        if (!EMAIL_SIMPLE.matcher(email).matches()) {
            styleAuthMessage(signUpMessageLabel, false);
            signUpMessageLabel.setText("Invalid email format.");
            return;
        }

        try {
            UserRole selectedRole = "Agency".equalsIgnoreCase(type.trim())
                    ? UserRole.AGENCY_ADMIN
                    : UserRole.USER;
            User created = AuthServices.userService().signUp(name, email, password, selectedRole);
            styleAuthMessage(signUpMessageLabel, true);
            signUpMessageLabel.setText("Account created successfully (id = " + created.getId() + ").");
        } catch (IllegalArgumentException e) {
            styleAuthMessage(signUpMessageLabel, false);
            signUpMessageLabel.setText(e.getMessage());
        } catch (SQLException e) {
            styleAuthMessage(signUpMessageLabel, false);
            signUpMessageLabel.setText("Database error: " + e.getMessage());
        }
    }

    private void loadLogo() {
        if (logoImage == null) {
            return;
        }
        Image image = tryLoad(PRIMARY_LOGO);
        if (image == null) {
            File fallback = new File(FALLBACK_FILE);
            if (fallback.exists()) {
                image = new Image(fallback.toURI().toString(), true);
            }
        }
        if (image != null) {
            logoImage.setImage(image);
        }
    }

    private Image tryLoad(String path) {
        URL url = getClass().getResource(path);
        if (url == null) {
            return null;
        }
        return new Image(url.toExternalForm(), true);
    }

    private void buildStarfield() {
        StarfieldHelper.populate(starfieldPane);
    }

    private void mountGlobe() {
        Platform.runLater(() -> {
            double w = Math.max(globeLayer.getWidth(), 560);
            double h = Math.max(globeLayer.getHeight(), 520);

            SubScene globeScene = GlobeModelFactory.createGlobeSubScene(w, h);
            globeScene.widthProperty().bind(globeLayer.widthProperty());
            globeScene.heightProperty().bind(globeLayer.heightProperty());
            globeScene.setMouseTransparent(true);

            globeLayer.getChildren().setAll(globeScene);
            globeLayer.setPickOnBounds(false);
            globeLayer.toFront();
            globeLayer.setManaged(true);
            globeLayer.setVisible(true);

            AnchorPane.setTopAnchor(globeScene, 0.0);
            AnchorPane.setBottomAnchor(globeScene, 0.0);
            AnchorPane.setLeftAnchor(globeScene, 0.0);
            AnchorPane.setRightAnchor(globeScene, 0.0);
        });
    }

    private void wireButtonHovers() {
        Objects.requireNonNull(primaryButton);
        Objects.requireNonNull(secondaryButton);
        installCtaHover(primaryButton);
        installCtaHover(secondaryButton);
        installPremiumDockHover(sidebarOpenSignInButton);
        installPremiumDockHover(sidebarOpenSignUpButton);
        installLift(signInSubmitButton);
        installLift(signUpSubmitButton);
        installLift(backFromSignInButton);
        installLift(backFromSignUpButton);
    }

    private void installLift(Button button) {
        button.setOnMouseEntered(e -> {
            button.setScaleX(1.03);
            button.setScaleY(1.03);
        });
        button.setOnMouseExited(e -> {
            button.setScaleX(1.0);
            button.setScaleY(1.0);
        });
    }

    /** Hero CTAs: gentle lift, soft press (CSS handles glow depth). */
    private void installCtaHover(Button button) {
        button.setOnMouseEntered(e -> {
            button.setScaleX(1.02);
            button.setScaleY(1.02);
        });
        button.setOnMouseExited(e -> {
            button.setScaleX(1.0);
            button.setScaleY(1.0);
        });
        button.setOnMousePressed(e -> {
            button.setScaleX(0.985);
            button.setScaleY(0.985);
        });
        button.setOnMouseReleased(e -> {
            if (button.isHover()) {
                button.setScaleX(1.02);
                button.setScaleY(1.02);
            } else {
                button.setScaleX(1.0);
                button.setScaleY(1.0);
            }
        });
    }

    private void installPremiumDockHover(Button button) {
        ScaleTransition lift = new ScaleTransition(Duration.millis(180), button);
        lift.setToX(1.025);
        lift.setToY(1.025);
        lift.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition settle = new ScaleTransition(Duration.millis(220), button);
        settle.setToX(1.0);
        settle.setToY(1.0);
        settle.setInterpolator(Interpolator.EASE_OUT);
        button.setOnMouseEntered(e -> {
            settle.stop();
            lift.playFromStart();
        });
        button.setOnMouseExited(e -> {
            lift.stop();
            settle.playFromStart();
        });
    }
}
