package utils;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

/**
 * Bouton + popup pour insérer des émojis dans un {@link TextArea} (position du curseur).
 * Grille en images Twemoji (couleurs « type Facebook ») ; le bouton déclencheur affiche 😊 en couleur.
 */
public final class EmojiPickerHelper {

    private static final String CELL_BASE =
            "-fx-background-color: rgba(255,255,255,0.12); -fx-background-radius: 10; -fx-border-radius: 10; "
                    + "-fx-min-width: 44px; -fx-min-height: 44px; -fx-padding: 4; "
                    + "-fx-cursor: hand; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;";

    private EmojiPickerHelper() {
    }

    private record EmojiCategory(String title, String titleColorHex, String rowBg, String cellHoverBg, String[] glyphs) {}

    private static String[] coloredSquares() {
        // U+1F7E5 … U+1F7EB : carrés rouge → violet
        String[] out = new String[7];
        int i = 0;
        for (int cp = 0x1F7E5; cp <= 0x1F7EB; cp++) {
            out[i++] = new String(Character.toChars(cp));
        }
        return out;
    }

    private static String[] coloredCircles() {
        // U+1F7E0 … U+1F7E4 : disques orange → marron
        String[] out = new String[5];
        int i = 0;
        for (int cp = 0x1F7E0; cp <= 0x1F7E4; cp++) {
            out[i++] = new String(Character.toChars(cp));
        }
        return out;
    }

    private static final EmojiCategory[] CATEGORIES = {
            new EmojiCategory(
                    "Visages & joie",
                    "#fbbf24",
                    "rgba(251,191,36,0.16)",
                    "rgba(251,191,36,0.42)",
                    new String[]{
                            "\uD83D\uDE00", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83E\uDD23", "\uD83D\uDE0A", "\uD83D\uDE0D", "\uD83E\uDD70", "\uD83D\uDE18",
                            "\uD83D\uDE0E", "\uD83E\uDD29", "\uD83E\uDD73", "\uD83D\uDE07", "\uD83E\uDD14", "\uD83D\uDE2D", "\uD83D\uDE22", "\uD83D\uDE24"
                    }),
            new EmojiCategory(
                    "Mains & énergie",
                    "#fb7185",
                    "rgba(251,113,133,0.16)",
                    "rgba(251,113,133,0.42)",
                    new String[]{
                            "\uD83D\uDC4D", "\uD83D\uDC4E", "\u270C\uFE0F", "\uD83E\uDD1E", "\uD83D\uDC4F", "\uD83D\uDE4F", "\uD83D\uDCAA", "\uD83D\uDD25",
                            "\u2728", "\uD83D\uDCAF", "\uD83D\uDCA5", "\uD83D\uDCA2"
                    }),
            new EmojiCategory(
                    "Cœurs & arc-en-ciel",
                    "#f472b6",
                    "rgba(244,114,182,0.18)",
                    "rgba(244,114,182,0.45)",
                    concat(
                            new String[]{
                                    "\u2764\uFE0F", "\uD83D\uDC94", "\uD83D\uDC9A", "\uD83D\uDC99", "\uD83D\uDC9C", "\uD83D\uDC9B", "\uD83D\uDC9D", "\uD83D\uDC95",
                                    "\uD83C\uDF08", "\uD83C\uDFA8"
                            },
                            coloredCircles())),
            new EmojiCategory(
                    "Voyage & fête",
                    "#38bdf8",
                    "rgba(56,189,248,0.16)",
                    "rgba(56,189,248,0.42)",
                    new String[]{
                            "\uD83E\uDDF3", "\u2708\uFE0F", "\uD83C\uDF0D", "\uD83C\uDFD6\uFE0F", "\u26F0\uFE0F", "\uD83C\uDF05", "\uD83C\uDF0A", "\uD83C\uDFDE\uFE0F",
                            "\uD83D\uDCF7", "\uD83D\uDDFA\uFE0F", "\u26FA", "\uD83D\uDE97", "\uD83C\uDF89", "\uD83C\uDF81", "\uD83C\uDF7A", "\u2615"
                    }),
            new EmojiCategory(
                    "Carrés colorés",
                    "#a78bfa",
                    "rgba(167,139,250,0.18)",
                    "rgba(167,139,250,0.45)",
                    concat(coloredSquares(), new String[]{"\u2B1B", "\u2B1C", "\uD83D\uDD34", "\uD83D\uDD35", "\u26AA", "\u26AB"})),
            new EmojiCategory(
                    "Divers",
                    "#94a3b8",
                    "rgba(148,163,184,0.14)",
                    "rgba(148,163,184,0.38)",
                    new String[]{
                            "\u2B50", "\uD83C\uDF1F", "\u2705", "\u274C", "\u2753", "\u2757", "\uD83D\uDCA1", "\uD83D\uDD12"
                    })
    };

    private static String[] concat(String[] a, String[] b) {
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static String cellHoverStyle(String hoverBg) {
        return "-fx-background-color: " + hoverBg + "; -fx-background-radius: 10; -fx-border-radius: 10; "
                + "-fx-border-color: rgba(255,255,255,0.45); -fx-border-width: 1; "
                + "-fx-min-width: 44px; -fx-min-height: 44px; -fx-padding: 4; "
                + "-fx-cursor: hand; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;";
    }

    /**
     * Au clic sur {@code trigger}, ouvre un panneau et insère l'émoji choisi dans {@code target} au curseur.
     */
    public static void wire(Button trigger, TextArea target) {
        if (trigger == null || target == null) {
            return;
        }
        trigger.setMnemonicParsing(false);
        trigger.setText(null);
        ImageView smile = TwemojiUtil.smilingFaceImageView(26);
        trigger.setGraphic(smile);
        trigger.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        trigger.setOnAction(e -> {
            e.consume();
            showNear(trigger, target);
        });
    }

    private static void showNear(Button anchor, TextArea target) {
        Window window = anchor.getScene().getWindow();
        if (window == null) {
            return;
        }

        Popup popup = new Popup();
        popup.setAutoHide(true);

        VBox sections = new VBox(10);
        sections.setPadding(new Insets(8, 10, 10, 10));
        sections.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(48,32,72,0.98), rgba(16,14,28,0.99)); "
                + "-fx-background-radius: 14; -fx-border-color: rgba(251,191,36,0.25); -fx-border-radius: 14; -fx-border-width: 1;");

        for (EmojiCategory cat : CATEGORIES) {
            Label head = new Label(cat.title);
            head.setPadding(new Insets(4, 0, 2, 4));
            head.setStyle("-fx-text-fill: " + cat.titleColorHex + "; -fx-font-weight: 800; -fx-font-size: 11px; "
                    + "-fx-effect: dropshadow(gaussian, " + cat.titleColorHex + "66, 10, 0.4, 0, 0);");

            FlowPane row = new FlowPane(6, 6);
            row.setPadding(new Insets(8, 10, 10, 10));
            row.setPrefWrapLength(268);
            row.setStyle("-fx-background-color: " + cat.rowBg + "; -fx-background-radius: 10; "
                    + "-fx-border-color: " + cat.titleColorHex + "44; -fx-border-radius: 10; -fx-border-width: 1;");

            String hover = cellHoverStyle(cat.cellHoverBg);

            for (String emoji : cat.glyphs) {
                Button cell = new Button();
                ImageView glyph = TwemojiUtil.createImageView(emoji, 26);
                cell.setGraphic(glyph);
                cell.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                cell.setText(null);
                cell.setStyle(CELL_BASE);
                cell.setOnMouseEntered(ev -> cell.setStyle(hover));
                cell.setOnMouseExited(ev -> cell.setStyle(CELL_BASE));
                cell.setOnAction(ev -> {
                    ev.consume();
                    insertAtCaret(target, emoji);
                    popup.hide();
                });
                row.getChildren().add(cell);
            }

            sections.getChildren().addAll(head, row);
        }

        Label hint = new Label("Émojis en couleurs — cliquez pour insérer");
        hint.setStyle("-fx-text-fill: #e9d5ff; -fx-font-size: 11px; -fx-font-weight: 700;");

        VBox root = new VBox(6, hint, wrapScroll(sections));
        root.setStyle("-fx-effect: dropshadow(gaussian, rgba(167,139,250,0.5), 28, 0.28, 0, 8);");

        popup.getContent().add(root);

        Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        double x = b.getMinX();
        double y = b.getMaxY() + 4;
        if (x + 300 > window.getX() + window.getWidth()) {
            x = Math.max(window.getX() + 8, window.getX() + window.getWidth() - 308);
        }
        popup.show(window, x, y);
        target.requestFocus();
    }

    private static ScrollPane wrapScroll(VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(320);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scroll;
    }

    private static void insertAtCaret(TextArea area, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int pos = area.getCaretPosition();
        String cur = area.getText();
        if (pos < 0) {
            pos = 0;
        }
        if (pos > cur.length()) {
            pos = cur.length();
        }
        area.replaceText(pos, pos, text);
        area.positionCaret(pos + text.length());
        area.requestFocus();
    }
}
