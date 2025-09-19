package net.etfbl.pj2.gui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Singleton view manager for the initial application screen.
 * Responsible for loading and displaying the initial FXML layout on the primary stage.
 */
public class InitialView {

    /**
     * Singleton instance of the InitialView.
     * */
    public static final InitialView INSTANCE = new InitialView();


    /**
     *  Private constructor to enforce singleton usage.
     *  */
    private InitialView() {}

    /**
     * Returns the singleton instance of InitialView.
     * @return the single instance of this view
     */
    public static InitialView getInstance() {
        return INSTANCE;
    }

    /**
     * Displays the initial application view by loading the FXML layout and applying it to the provided stage.
     * @param stage the primary stage where the scene will be set
     * @throws IOException if the FXML file cannot be loaded
     */
    public void displayInitialView(@NotNull Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("initial-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1920, 1080);
        stage.setTitle("Sistem za simulaciju transportas");
        stage.setScene(scene);
        stage.show();

    }

}
