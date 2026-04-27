package utils;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.Random;

/**
 * Shared animated starfield used by welcome and signed-in home main canvas.
 */
public final class StarfieldHelper {

    private StarfieldHelper() {
    }

    public static void populate(Pane starfieldPane) {
        if (starfieldPane == null) {
            return;
        }
        starfieldPane.getChildren().clear();
        Random random = new Random(13);
        for (int i = 0; i < 110; i++) {
            Circle star = new Circle(0.6 + random.nextDouble() * 1.6);
            star.getStyleClass().add("star");
            star.setOpacity(0.25 + random.nextDouble() * 0.7);
            star.centerXProperty().bind(starfieldPane.widthProperty().multiply(random.nextDouble()));
            star.centerYProperty().bind(starfieldPane.heightProperty().multiply(random.nextDouble()));
            starfieldPane.getChildren().add(star);

            Timeline pulse = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(star.opacityProperty(), star.getOpacity() * 0.55)),
                    new KeyFrame(Duration.seconds(1.6 + random.nextDouble() * 2.8), new KeyValue(star.opacityProperty(), star.getOpacity())),
                    new KeyFrame(Duration.seconds(3.2 + random.nextDouble() * 2.8), new KeyValue(star.opacityProperty(), star.getOpacity() * 0.55))
            );
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.play();
        }
    }
}
