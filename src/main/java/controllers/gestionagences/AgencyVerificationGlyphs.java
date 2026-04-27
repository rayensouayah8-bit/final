package controllers.gestionagences;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

/**
 * Small verification / pending marks for agency cards. Verified badge paths are derived from
 * Lucide {@code badge-check} (ISC) — see {@code META-INF/licenses/LUCIDE_ICONS_NOTICE.txt}.
 */
public final class AgencyVerificationGlyphs {

    private AgencyVerificationGlyphs() {
    }

    /**
     * Lucide {@code badge-check} paths (24×24 viewBox), filled/stroked for a compact “verified” mark.
     */
    public static Node verifiedMark(double px) {
        SVGPath outer = new SVGPath();
        outer.setContent(
                "M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z");
        outer.setFill(Color.web("#2563eb"));
        outer.setStroke(null);

        SVGPath tick = new SVGPath();
        tick.setContent("m9 12 2 2 4-4");
        tick.setFill(Color.TRANSPARENT);
        tick.setStroke(Color.web("#f8fafc"));
        tick.setStrokeWidth(2);
        tick.setStrokeLineCap(StrokeLineCap.ROUND);
        tick.setStrokeLineJoin(StrokeLineJoin.ROUND);

        Group g = new Group();
        g.getChildren().addAll(outer, tick);
        return wrapScaled(g, px);
    }

    /**
     * Amber clock (24×24 geometry) for “pending verification”.
     */
    public static Node pendingClockMark(double px) {
        Color amber = Color.web("#eab308");
        Circle ring = new Circle(12, 12, 10);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(amber);
        ring.setStrokeWidth(2);

        Polyline hands = new Polyline(12, 6, 12, 12, 16, 14);
        hands.setFill(Color.TRANSPARENT);
        hands.setStroke(amber);
        hands.setStrokeWidth(2);
        hands.setStrokeLineCap(StrokeLineCap.ROUND);
        hands.setStrokeLineJoin(StrokeLineJoin.ROUND);

        Group g = new Group();
        g.getChildren().addAll(ring, hands);
        return wrapScaled(g, px);
    }

    private static Node wrapScaled(Group g, double px) {
        g.getTransforms().setAll(new Scale(px / 24.0, px / 24.0, 0, 0));
        StackPane host = new StackPane(g);
        host.setMinSize(px, px);
        host.setPrefSize(px, px);
        host.setMaxSize(px, px);
        host.getStyleClass().add("agency-directory-verification-glyph-host");
        return host;
    }
}
