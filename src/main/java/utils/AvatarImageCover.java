package utils;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Scales an {@link ImageView} so the image covers a square region (like CSS {@code object-fit: cover})
 * for circular avatars. JavaFX defaults to "contain" when preserveRatio is true, which leaves gaps.
 */
public final class AvatarImageCover {

    private AvatarImageCover() {
    }

    /** Reset fit and translation so the view fills a {@code box} square (no image scaling logic). */
    public static void resetToSquare(ImageView imageView, double box) {
        if (imageView == null) {
            return;
        }
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(box);
        imageView.setFitHeight(box);
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
    }

    public static void apply(ImageView imageView, Image image, double box) {
        if (imageView == null) {
            return;
        }
        if (image == null) {
            resetToSquare(imageView, box);
            return;
        }
        Runnable sizing = () -> {
            double iw = image.getWidth();
            double ih = image.getHeight();
            if (iw <= 0 || ih <= 0 || image.isError()) {
                return;
            }
            double scale = Math.max(box / iw, box / ih);
            double rw = iw * scale;
            double rh = ih * scale;
            imageView.setPreserveRatio(false);
            imageView.setSmooth(true);
            imageView.setFitWidth(rw);
            imageView.setFitHeight(rh);
            imageView.setTranslateX((box - rw) / 2);
            imageView.setTranslateY((box - rh) / 2);
        };
        if (image.getWidth() > 0 && image.getHeight() > 0) {
            sizing.run();
            return;
        }
        InvalidationListener dimensionListener = new InvalidationListener() {
            @Override
            public void invalidated(Observable observable) {
                if (image.getWidth() > 0 && image.getHeight() > 0 && !image.isError()) {
                    image.widthProperty().removeListener(this);
                    image.heightProperty().removeListener(this);
                    image.progressProperty().removeListener(this);
                    Platform.runLater(sizing);
                } else if (image.isError()) {
                    image.widthProperty().removeListener(this);
                    image.heightProperty().removeListener(this);
                    image.progressProperty().removeListener(this);
                }
            }
        };
        image.widthProperty().addListener(dimensionListener);
        image.heightProperty().addListener(dimensionListener);
        image.progressProperty().addListener(dimensionListener);
    }
}
