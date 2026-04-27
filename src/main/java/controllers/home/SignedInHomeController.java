package controllers.home;

import auth.AuthSession;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import models.gestionutilisateurs.User;
import utils.NavigationManager;
import utils.StarfieldHelper;
import javafx.util.Duration;

/**
 * Signed-in home: cosmic center + greeting. Sidebar markup is inlined in {@code signed-in-home.fxml}.
 */
public class SignedInHomeController extends SignedInPageControllerBase {

    @FXML
    private Label userGreetingLabel;
    @FXML
    private Label roleLabel;

    @FXML
    private Pane signedInStarfieldPane;
    @FXML
    private StackPane signedInEntryOverlay;
    @FXML
    private VBox signedInEntryBrandCard;
    @FXML
    private Region signedInEntryUnderlineBar;

    @FXML
    private void initialize() {
        NavigationManager nav = NavigationManager.getInstance();
        if (!nav.canAccessSignedInShell()) {
            nav.showWelcome();
            return;
        }
        initSignedInSidebar();

        StarfieldHelper.populate(signedInStarfieldPane);

        User user = AuthSession.getCurrentUser().orElse(null);
        if (user != null && userGreetingLabel != null) {
            String displayName = user.getUsername() != null && !user.getUsername().isBlank()
                    ? user.getUsername()
                    : user.getEmail();
            userGreetingLabel.setText("Welcome back, " + displayName);
        }
        if (roleLabel != null) {
            boolean agencyAdmin = nav.canAccessAgencyAdminFeatures();
            roleLabel.setText(agencyAdmin ? "Agency Admin session active" : "User session active");
        }
        if (nav.consumeSignedInEntryTransition()) {
            playSignedInEntryTransition();
        }
        Platform.runLater(this::ensureWelcomeStylesLoaded);
    }

    private void ensureWelcomeStylesLoaded() {
        if (userGreetingLabel == null || userGreetingLabel.getScene() == null) {
            return;
        }
        String styles = SignedInHomeController.class.getResource("/css/styles.css") != null
                ? SignedInHomeController.class.getResource("/css/styles.css").toExternalForm()
                : null;
        if (styles != null && !userGreetingLabel.getScene().getStylesheets().contains(styles)) {
            userGreetingLabel.getScene().getStylesheets().add(0, styles);
        }
    }

    private void playSignedInEntryTransition() {
        if (signedInEntryOverlay == null || signedInEntryBrandCard == null) {
            return;
        }
        signedInEntryOverlay.setManaged(true);
        signedInEntryOverlay.setVisible(true);
        signedInEntryOverlay.setOpacity(0);
        signedInEntryBrandCard.setOpacity(0);
        signedInEntryBrandCard.setTranslateY(18);
        signedInEntryBrandCard.setScaleX(0.98);
        signedInEntryBrandCard.setScaleY(0.98);
        if (signedInEntryUnderlineBar != null) {
            signedInEntryUnderlineBar.setScaleX(0.58);
        }

        Interpolator ease = Interpolator.SPLINE(0.33, 0.0, 0.2, 1.0);

        FadeTransition overlayIn = new FadeTransition(Duration.millis(240), signedInEntryOverlay);
        overlayIn.setFromValue(0);
        overlayIn.setToValue(1);
        overlayIn.setInterpolator(ease);

        FadeTransition cardIn = new FadeTransition(Duration.millis(320), signedInEntryBrandCard);
        cardIn.setFromValue(0);
        cardIn.setToValue(1);
        cardIn.setInterpolator(ease);

        TranslateTransition cardRise = new TranslateTransition(Duration.millis(320), signedInEntryBrandCard);
        cardRise.setFromY(18);
        cardRise.setToY(0);
        cardRise.setInterpolator(ease);

        Timeline cardScale = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(signedInEntryBrandCard.scaleXProperty(), 0.98),
                        new KeyValue(signedInEntryBrandCard.scaleYProperty(), 0.98)),
                new KeyFrame(Duration.millis(320),
                        new KeyValue(signedInEntryBrandCard.scaleXProperty(), 1.0, ease),
                        new KeyValue(signedInEntryBrandCard.scaleYProperty(), 1.0, ease))
        );

        Timeline underlineSweep = null;
        if (signedInEntryUnderlineBar != null) {
            underlineSweep = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(signedInEntryUnderlineBar.scaleXProperty(), 0.58)),
                    new KeyFrame(Duration.millis(420), new KeyValue(signedInEntryUnderlineBar.scaleXProperty(), 1.0, ease))
            );
        }

        PauseTransition hold = new PauseTransition(Duration.millis(520));

        FadeTransition cardOut = new FadeTransition(Duration.millis(280), signedInEntryBrandCard);
        cardOut.setFromValue(1);
        cardOut.setToValue(0);
        cardOut.setInterpolator(ease);

        FadeTransition overlayOut = new FadeTransition(Duration.millis(300), signedInEntryOverlay);
        overlayOut.setFromValue(1);
        overlayOut.setToValue(0);
        overlayOut.setInterpolator(ease);

        ParallelTransition inMotion;
        if (underlineSweep != null) {
            inMotion = new ParallelTransition(overlayIn, cardIn, cardRise, cardScale, underlineSweep);
        } else {
            inMotion = new ParallelTransition(overlayIn, cardIn, cardRise, cardScale);
        }
        ParallelTransition outMotion = new ParallelTransition(cardOut, overlayOut);
        SequentialTransition seq = new SequentialTransition(inMotion, hold, outMotion);
        seq.setOnFinished(e -> {
            signedInEntryOverlay.setVisible(false);
            signedInEntryOverlay.setManaged(false);
            signedInEntryOverlay.setOpacity(1.0);
            signedInEntryBrandCard.setOpacity(1.0);
            signedInEntryBrandCard.setTranslateY(0);
            signedInEntryBrandCard.setScaleX(1.0);
            signedInEntryBrandCard.setScaleY(1.0);
            if (signedInEntryUnderlineBar != null) {
                signedInEntryUnderlineBar.setScaleX(1.0);
            }
        });
        seq.playFromStart();
    }
}
