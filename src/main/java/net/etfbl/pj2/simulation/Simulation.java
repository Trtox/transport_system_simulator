package net.etfbl.pj2.simulation;

import javafx.application.Application;
import javafx.stage.Stage;
import net.etfbl.pj2.gui.InitialView;

import java.io.IOException;

/**
 * Class that runs the actual simulation.
 */
public class Simulation extends Application {
    /**
     * Starts the simulation.
     * @param stage Stage to be used for displaying the simulation.
     * @throws IOException If there is an error while loading the GUI.
     */
    @Override
    public void start(Stage stage) throws IOException {

        System.setProperty("org.graphstream.ui", "swing");

        InitialView.getInstance().displayInitialView(stage);

    }

    public static void main(String[] args) {
        launch();
    }
}