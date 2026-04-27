package models.welcome;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import javafx.animation.Animation;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * Layered SmartVoyage globe: (1) dark premium core, (2) low-opacity geography shell, (3) soft atmosphere shell,
 * plus one orbit ring and subtle nodes. Geography does not share the core's specular — travel cue without
 * fighting the stylized base.
 */
public final class GlobeModelFactory {

    private static final double CORE_RADIUS = 181.0;
    private static final double GEO_RADIUS = 185.2;
    private static final double ATMOSPHERE_RADIUS = 199.0;

    private record GeoEllipse(double lonDeg, double latDeg, double rxDeg, double ryDeg, double rotDeg) {}

    private GlobeModelFactory() {
    }

    public static SubScene createGlobeSubScene(double width, double height) {
        Group root3D = new Group();
        Group world = new Group();
        root3D.getChildren().add(world);

        Sphere core = new Sphere(CORE_RADIUS);
        PhongMaterial coreMat = new PhongMaterial();
        coreMat.setDiffuseColor(Color.web("#05030c"));
        coreMat.setSpecularColor(Color.web("#f5d0fe", 0.84));
        coreMat.setSpecularPower(430);
        core.setMaterial(coreMat);

        Sphere geoOverlay = new Sphere(GEO_RADIUS);
        PhongMaterial geoMat = new PhongMaterial();
        geoMat.setDiffuseColor(Color.WHITE);
        geoMat.setDiffuseMap(buildGeographyOverlayDiffuseMap());
        geoMat.setSpecularColor(Color.rgb(0, 0, 0, 0.04));
        geoMat.setSpecularPower(12);
        geoOverlay.setMaterial(geoMat);

        Sphere atmosphereShell = new Sphere(ATMOSPHERE_RADIUS);
        PhongMaterial atmoMat = new PhongMaterial();
        atmoMat.setDiffuseColor(Color.web("#6d28d9", 0.11));
        atmoMat.setSpecularColor(Color.web("#f0abfc", 0.18));
        atmoMat.setSpecularPower(90);
        atmosphereShell.setMaterial(atmoMat);

        Group globeStack = new Group(core, geoOverlay, atmosphereShell);

        PhongMaterial orbitMat = new PhongMaterial();
        orbitMat.setDiffuseColor(Color.web("#5b21b8", 0.52));
        orbitMat.setSpecularColor(Color.web("#e9d5ff", 0.48));
        orbitMat.setSpecularPower(200);
        MeshView orbit = createTorusRingMesh(218.0, 0.92, 140, 10, 0.0, Math.PI * 2.0, orbitMat);
        Group orbitGroup = new Group(orbit);
        orbitGroup.getTransforms().addAll(
                new Rotate(74, Rotate.X_AXIS),
                new Rotate(-20, Rotate.Z_AXIS),
                new Rotate(12, Rotate.Y_AXIS)
        );
        orbitGroup.setTranslateZ(18);

        Sphere nodeA = createSubtleNode(102, -70, 118);
        Sphere nodeB = createSubtleNode(-108, 58, 112);
        Sphere nodeC = createSubtleNode(38, 118, 88);

        world.getChildren().addAll(globeStack, orbitGroup, nodeA, nodeB, nodeC);

        AmbientLight ambient = new AmbientLight(Color.web("#64748b", 0.17));
        PointLight key = new PointLight(Color.web("#a78bfa"));
        key.setTranslateX(-270);
        key.setTranslateY(-195);
        key.setTranslateZ(-340);
        PointLight fill = new PointLight(Color.web("#ec4899"));
        fill.setTranslateX(245);
        fill.setTranslateY(128);
        fill.setTranslateZ(-220);
        PointLight rim = new PointLight(Color.web("#f5f3ff"));
        rim.setTranslateX(22);
        rim.setTranslateY(-238);
        rim.setTranslateZ(32);
        root3D.getChildren().addAll(ambient, key, fill, rim);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(5000);
        camera.setTranslateZ(-1200);
        camera.setTranslateY(height / 2.0);
        camera.setTranslateX(width / 2.0);
        SubScene subScene = new SubScene(root3D, width, height, true, null);
        subScene.setFill(Color.TRANSPARENT);
        subScene.setCamera(camera);
        subScene.setManaged(false);
        subScene.setMouseTransparent(true);
        world.setTranslateX(width / 2.0);
        world.setTranslateY(height / 2.0);
        subScene.widthProperty().addListener((obs, oldV, newV) -> {
            world.setTranslateX(newV.doubleValue() / 2.0);
            camera.setTranslateX(newV.doubleValue() / 2.0);
        });
        subScene.heightProperty().addListener((obs, oldV, newV) -> {
            world.setTranslateY(newV.doubleValue() / 2.0);
            camera.setTranslateY(newV.doubleValue() / 2.0);
        });

        RotateTransition worldSpin = new RotateTransition(Duration.seconds(36), world);
        worldSpin.setAxis(Rotate.Y_AXIS);
        worldSpin.setByAngle(360);
        worldSpin.setCycleCount(Animation.INDEFINITE);
        worldSpin.play();

        RotateTransition ringDrift = new RotateTransition(Duration.seconds(88), orbitGroup);
        ringDrift.setAxis(Rotate.Y_AXIS);
        ringDrift.setByAngle(360);
        ringDrift.setCycleCount(Animation.INDEFINITE);
        ringDrift.play();

        ScaleTransition shellPulse = new ScaleTransition(Duration.seconds(8.0), atmosphereShell);
        shellPulse.setFromX(0.992);
        shellPulse.setFromY(0.992);
        shellPulse.setFromZ(0.992);
        shellPulse.setToX(1.008);
        shellPulse.setToY(1.008);
        shellPulse.setToZ(1.008);
        shellPulse.setAutoReverse(true);
        shellPulse.setCycleCount(Animation.INDEFINITE);
        shellPulse.play();

        return subScene;
    }

    /**
     * Equirectangular ARGB texture: transparent ocean, dark violet-gray continents (muted, no blue/green).
     */
    private static Image buildGeographyOverlayDiffuseMap() {
        final int width = 1024;
        final int height = 512;
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.clearRect(0, 0, width, height);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        java.awt.Color land = new java.awt.Color(78, 62, 102, 138);
        java.awt.Color landDeep = new java.awt.Color(56, 46, 78, 155);

        GeoEllipse[] masses = {
                new GeoEllipse(-95, 48, 32, 26, -22),
                new GeoEllipse(-118, 58, 22, 16, 12),
                new GeoEllipse(-72, 12, 18, 38, 8),
                new GeoEllipse(-58, -18, 16, 40, 10),
                new GeoEllipse(-84, 14, 14, 10, 22),
                new GeoEllipse(18, 52, 26, 16, -12),
                new GeoEllipse(22, 6, 26, 44, 4),
                new GeoEllipse(48, -8, 14, 34, 6),
                new GeoEllipse(48, 10, 10, 18, 0),
                new GeoEllipse(50, 26, 18, 14, -12),
                new GeoEllipse(92, 38, 48, 28, -10),
                new GeoEllipse(108, 18, 28, 18, -6),
                new GeoEllipse(118, 52, 36, 18, 0),
                new GeoEllipse(78, 62, 40, 14, 0),
                new GeoEllipse(100, 8, 22, 12, -8),
                new GeoEllipse(132, -26, 18, 12, 0),
                new GeoEllipse(142, -6, 14, 10, -15),
                new GeoEllipse(110, -2, 26, 10, -8),
                new GeoEllipse(138, 36, 8, 7, 0),
                new GeoEllipse(-42, 72, 12, 20, -12),
                new GeoEllipse(12, -78, 88, 10, 0),
                new GeoEllipse(-158, 62, 20, 12, 0),
                new GeoEllipse(-172, -42, 10, 8, 0),
                new GeoEllipse(-6, 56, 6, 8, 0),
                new GeoEllipse(28, 22, 12, 10, 0),
                new GeoEllipse(-82, 10, 14, 22, 15),
        };
        g.setColor(land);
        for (GeoEllipse e : masses) {
            fillGeoEllipse(g, width, height, e);
        }
        g.setColor(landDeep);
        for (GeoEllipse e : new GeoEllipse[] {
                new GeoEllipse(22, 6, 18, 32, 4),
                new GeoEllipse(92, 38, 30, 20, -10),
                new GeoEllipse(-58, -18, 12, 30, 10),
        }) {
            fillGeoEllipse(g, width, height, e);
        }
        g.dispose();
        blendLongitudeSeamArgb(bi, 5);
        return argbBufferedImageToFx(bi);
    }

    private static void fillGeoEllipse(Graphics2D g, int w, int h, GeoEllipse e) {
        double cx = (e.lonDeg() + 180.0) / 360.0 * w;
        double cy = (90.0 - e.latDeg()) / 180.0 * h;
        double rx = e.rxDeg() / 360.0 * w;
        double ry = e.ryDeg() / 180.0 * h;
        var saved = g.getTransform();
        g.translate(cx, cy);
        g.rotate(Math.toRadians(e.rotDeg()));
        g.fill(new Ellipse2D.Double(-rx, -ry, 2.0 * rx, 2.0 * ry));
        g.setTransform(saved);
    }

    private static void blendLongitudeSeamArgb(BufferedImage bi, int overlap) {
        int w = bi.getWidth();
        int h = bi.getHeight();
        for (int y = 0; y < h; y++) {
            for (int k = 0; k < overlap; k++) {
                double t = 0.32 * (k + 1) / overlap;
                int xl = k;
                int xr = w - overlap + k;
                bi.setRGB(xl, y, blendArgb(bi.getRGB(xl, y), bi.getRGB(xr, y), t));
                int xrr = w - 1 - k;
                int xll = overlap - 1 - k;
                bi.setRGB(xrr, y, blendArgb(bi.getRGB(xrr, y), bi.getRGB(xll, y), t));
            }
        }
    }

    private static int blendArgb(int c0, int c1, double t) {
        int a0 = (c0 >>> 24) & 0xff;
        int r0 = (c0 >> 16) & 0xff;
        int g0 = (c0 >> 8) & 0xff;
        int b0 = c0 & 0xff;
        int a1 = (c1 >>> 24) & 0xff;
        int r1 = (c1 >> 16) & 0xff;
        int g1 = (c1 >> 8) & 0xff;
        int b1 = c1 & 0xff;
        int a = (int) (a0 * (1.0 - t) + a1 * t + 0.5);
        int r = (int) (r0 * (1.0 - t) + r1 * t + 0.5);
        int g = (int) (g0 * (1.0 - t) + g1 * t + 0.5);
        int b = (int) (b0 * (1.0 - t) + b1 * t + 0.5);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static WritableImage argbBufferedImageToFx(BufferedImage bi) {
        int w = bi.getWidth();
        int h = bi.getHeight();
        WritableImage out = new WritableImage(w, h);
        PixelWriter pw = out.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = bi.getRGB(x, y);
                int a = (argb >>> 24) & 0xff;
                int r = (argb >> 16) & 0xff;
                int g = (argb >> 8) & 0xff;
                int b = argb & 0xff;
                pw.setColor(x, y, Color.rgb(r, g, b, a / 255.0));
            }
        }
        return out;
    }

    private static Sphere createSubtleNode(double x, double y, double z) {
        Sphere s = new Sphere(4.2);
        PhongMaterial m = new PhongMaterial();
        m.setDiffuseColor(Color.web("#c4b5fd", 0.28));
        m.setSpecularColor(Color.web("#fce7f3", 0.45));
        m.setSpecularPower(120);
        s.setMaterial(m);
        s.setTranslateX(x);
        s.setTranslateY(y);
        s.setTranslateZ(z);
        return s;
    }

    private static MeshView createTorusRingMesh(
            double majorR,
            double minorR,
            int uSeg,
            int vSeg,
            double uStart,
            double uEnd,
            PhongMaterial material
    ) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0f, 0f);
        for (int i = 0; i <= uSeg; i++) {
            double u = uStart + (uEnd - uStart) * (i / (double) uSeg);
            double cu = Math.cos(u);
            double su = Math.sin(u);
            for (int j = 0; j < vSeg; j++) {
                double v = (Math.PI * 2.0) * j / vSeg;
                double cv = Math.cos(v);
                double sv = Math.sin(v);
                double x = (majorR + minorR * cv) * cu;
                double y = minorR * sv;
                double z = (majorR + minorR * cv) * su;
                mesh.getPoints().addAll((float) x, (float) y, (float) z);
            }
        }
        for (int i = 0; i < uSeg; i++) {
            for (int j = 0; j < vSeg; j++) {
                int jn = (j + 1) % vSeg;
                int a = i * vSeg + j;
                int b = i * vSeg + jn;
                int c = (i + 1) * vSeg + jn;
                int d = (i + 1) * vSeg + j;
                mesh.getFaces().addAll(
                        a, 0, d, 0, b, 0,
                        b, 0, d, 0, c, 0
                );
            }
        }
        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setDrawMode(DrawMode.FILL);
        view.setCullFace(CullFace.BACK);
        return view;
    }
}
