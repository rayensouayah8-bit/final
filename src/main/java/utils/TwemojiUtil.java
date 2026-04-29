package utils;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Affiche les émojis en couleur (style « Facebook ») via les assets PNG Twemoji (CDN jsDelivr).
 * Contourne le rendu monochrome de JavaFX / Prism sur certains systèmes.
 */
public final class TwemojiUtil {

    private static final String CDN_BASE =
            "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/";

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private TwemojiUtil() {
    }

    /** Nom de fichier Twemoji pour un grapheme (ex. 😊 → {@code 1f60a.png}). */
    public static String toFilename(String grapheme) {
        if (grapheme == null || grapheme.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        grapheme.codePoints().forEach(cp -> {
            if (sb.length() > 0) {
                sb.append('-');
            }
            sb.append(String.format("%x", cp));
        });
        return sb + ".png";
    }

    public static String toUrl(String grapheme) {
        String fn = toFilename(grapheme);
        return fn.isEmpty() ? "" : CDN_BASE + fn;
    }

    /**
     * Image 72×72 Twemoji (mise en cache). Peut être en chargement ou en erreur si glyphe absent du pack.
     */
    public static Image getImage72(String grapheme) {
        if (grapheme == null || grapheme.isEmpty()) {
            return null;
        }
        return CACHE.computeIfAbsent(grapheme, g -> {
            String url = toUrl(g);
            if (url.isEmpty()) {
                return null;
            }
            return new Image(url, true);
        });
    }

    public static ImageView createImageView(String grapheme, double displaySize) {
        ImageView iv = new ImageView();
        iv.setFitWidth(displaySize);
        iv.setFitHeight(displaySize);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        Image img = getImage72(grapheme);
        iv.setImage(img);
        return iv;
    }

    /** Bouton / ligne : icône 😊 (U+1F60A) en couleur. */
    public static ImageView smilingFaceImageView(double displaySize) {
        return createImageView("\uD83D\uDE0A", displaySize);
    }
}
