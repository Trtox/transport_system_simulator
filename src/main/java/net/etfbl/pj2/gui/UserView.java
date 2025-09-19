package net.etfbl.pj2.gui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Singleton view manager for displaying the user interface of the application.
 * Loads the user-specific FXML layout and attaches it to the provided JavaFX stage.
 */
public class UserView {

    /**
     * Singleton instance of the UserView.
     * */
    public static final UserView INSTANCE = new UserView();

    /**
     * Private constructor to enforce singleton usage.
     * */
    private UserView() {

    }

    /**
     * Returns the singleton instance of UserView.
     * @return the UserView instance
     */
    public static UserView getInstance()  {
        return INSTANCE;
    }

    /**
     * Displays the user interface on the given stage by loading the corresponding FXML layout.
     * @param stage the JavaFX stage where the user interface will be shown
     * @throws IOException if the FXML resource cannot be loaded
     */
    public void displayUserView(@NotNull Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("user-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1920, 1080);
        stage.setTitle("Sistem za simulaciju transporta");
        stage.setScene(scene);
        stage.show();
    }
}
