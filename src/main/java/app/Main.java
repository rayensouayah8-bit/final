package app;

import javafx.application.Application;
import javafx.stage.Stage;
import utils.NavigationManager;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        NavigationManager.getInstance().configure(stage);
        NavigationManager.getInstance().showWelcome();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
