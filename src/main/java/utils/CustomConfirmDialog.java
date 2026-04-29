package utils;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class CustomConfirmDialog {
    private CustomConfirmDialog() {
    }

    public static boolean show(Window owner, String message, String confirmText) {
        final boolean[] confirmed = {false};

        Label icon = new Label("⚠");
        icon.setStyle("-fx-font-size: 34px; -fx-text-fill: #F59E0B;");
        Label title = new Label("Confirmer la suppression");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: 700;");
        Label content = new Label(message);
        content.setWrapText(true);
        content.setStyle("-fx-text-fill: #C4B5FD; -fx-font-size: 14px;");

        Button cancel = new Button("Annuler");
        cancel.setStyle("-fx-background-color: transparent; -fx-border-color: #7C3AED; -fx-text-fill: #C4B5FD; -fx-border-radius: 10; -fx-background-radius: 10;");
        Button confirm = new Button(confirmText);
        confirm.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-background-radius: 10;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox actions = new HBox(10, spacer, cancel, confirm);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(16, icon, title, content, actions);
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(400);
        card.setStyle("-fx-background-color: rgba(34, 26, 58, 0.97); -fx-border-color: #2E2550; -fx-border-radius: 16; -fx-background-radius: 16;");
        card.setPadding(new Insets(32));

        StackPane root = new StackPane(card);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        Scene scene = new Scene(root, Color.TRANSPARENT);
        stage.setScene(scene);

        cancel.setOnAction(e -> stage.close());
        confirm.setOnAction(e -> {
            confirmed[0] = true;
            stage.close();
        });

        stage.showAndWait();
        return confirmed[0];
    }
}
