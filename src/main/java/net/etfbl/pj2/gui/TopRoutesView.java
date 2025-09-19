package net.etfbl.pj2.gui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.etfbl.pj2.model.RouteSegment;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * View class responsible for displaying the Top Routes window in the application.
 * Loads the corresponding FXML layout, initializes the controller with the provided routes,
 * and attaches the scene to the given stage.
 */
public class TopRoutesView {

    /**
     * Displays the Top Routes view on the specified stage.
     * @param stage  the JavaFX stage on which to show the view
     * @param routes list of routes, each route represented as a list of RouteSegment
     * @throws IOException if the FXML layout cannot be loaded
     */
    public void displayTopRoutesView(@NotNull Stage stage, ArrayList<List<RouteSegment>> routes) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("top-routes-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        TopRoutesController controller = fxmlLoader.getController();
        controller.setRoutePickerComboBoxItems(routes);
        stage.setTitle("Pet najboljih ruta");
        stage.setScene(scene);
        stage.show();
    }
}
