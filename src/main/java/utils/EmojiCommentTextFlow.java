package utils;

import javafx.geometry.Insets;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construit un {@link TextFlow} avec texte + images Twemoji pour un rendu couleur (comme sur le web).
 */
public final class EmojiCommentTextFlow {

    /** Grapheme étendu (Java 9+), pour garder ❤️ + FE0F, ZWJ, etc. ensemble. */
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private EmojiCommentTextFlow() {
    }

    public static TextFlow build(String raw, double emojiSize, double textFontSize) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(2);
        flow.setPadding(new Insets(0, 0, 2, 0));

        if (raw == null || raw.isEmpty()) {
            return flow;
        }

        Matcher m = GRAPHEME.matcher(raw);
        while (m.find()) {
            String g = m.group();
            if (g.isEmpty()) {
                continue;
            }
            if (shouldUseTwemoji(g)) {
                var iv = TwemojiUtil.createImageView(g, emojiSize);
                iv.setTranslateY(2);
                flow.getChildren().add(iv);
            } else {
                Text t = new Text(g);
                t.setFill(Color.web("#e2e8f0"));
                t.setFont(Font.font("Segoe UI", textFontSize));
                flow.getChildren().add(t);
            }
        }
        return flow;
    }

    private static boolean shouldUseTwemoji(String g) {
        return g.codePoints().anyMatch(EmojiCommentTextFlow::isEmojiCodePoint);
    }

    /** Compatible Java 11+ (sans {@code Character.isEmoji} Java 15+). */
    private static boolean isEmojiCodePoint(int cp) {
        return (cp >= 0x1F600 && cp <= 0x1F64F)
                || (cp >= 0x1F300 && cp <= 0x1F5FF)
                || (cp >= 0x1F680 && cp <= 0x1F6FF)
                || (cp >= 0x1F900 && cp <= 0x1FAFF)
                || (cp >= 0x2600 && cp <= 0x26FF)
                || (cp >= 0x2700 && cp <= 0x27BF)
                || (cp >= 0xFE00 && cp <= 0xFE0F)
                || (cp >= 0x1F1E6 && cp <= 0x1F1FF)
                || (cp >= 0x1F3FB && cp <= 0x1F3FF)
                || cp == 0x200D
                || cp == 0x20E3
                || (cp >= 0x203C && cp <= 0x2049)
                || (cp >= 0x231A && cp <= 0x23FA);
    }

    /** Expose les graphemes pour tests / réutilisation. */
    public static List<String> graphemes(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null) {
            return list;
        }
        Matcher m = GRAPHEME.matcher(raw);
        while (m.find()) {
            list.add(m.group());
        }
        return list;
    }
}
