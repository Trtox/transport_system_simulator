package net.etfbl.pj2.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Pair;
import net.etfbl.pj2.utility.DataParser;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JavaFX controller for the initial screen.
 * Validates the grid dimensions, loads data, opens the GraphStream visualization in a Swing window
 * (maximized), and then shows the user-facing JavaFX window.
 * Aggregates ticket statistics for the welcome panel.
 */
public class InitialController {

    /** Text field for entering the number of rows in the grid. */
    @FXML
    private TextField rowsTextField;

    /** Text field for entering the number of columns in the grid. */
    @FXML
    private TextField columnsTextField;

    /** Label showing the total number of sold tickets detected. */
    @FXML
    private Label ticketsCountLabel;

    /** Label showing the total income aggregated from ticket files. */
    @FXML
    private Label incomeLabel;

    /**
     * Initializes summary labels by scanning the ticket folder and showing totals.
     * Called by JavaFX after FXML injection.
     */
    @FXML
    public void initialize() {

        Pair<String, String> ticketsInfo = getTicketsInfo();
        ticketsCountLabel.setText("Ukupno prodatih karata: " + ticketsInfo.getKey());
        incomeLabel.setText("Ukupna zarada: " + ticketsInfo.getValue());

    }

    /**
     * Validates form input and, if valid, parses data, hides the current stage, and opens the main views.
     * Shows an error dialog when input is invalid.
     */
    @FXML
    private void onConfirmButtonClick() {
        String rows = rowsTextField.getText();
        String columns = columnsTextField.getText();

        if (isPositiveNumber(rows) && isPositiveNumber(columns)) {
            int rowsValue = Integer.parseInt(rows);
            int columnsValue = Integer.parseInt(columns);

            DataParser.getInstance().parseDataFromJSON(rowsValue, columnsValue);


            Stage stage = (Stage) rowsTextField.getScene().getWindow();
            stage.hide();
            Platform.setImplicitExit(false);

            new Thread(this::openViews).start();

        } else {
            showValidationError();
        }
    }

    /** Opens the Swing graph (maximized) and then the JavaFX user view on their appropriate UI threads. */
    private void openViews() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            GraphView.getInstance().displayGraph();
            for (java.awt.Frame frame : java.awt.Frame.getFrames()) {
                if (frame.isDisplayable() && frame.isVisible()) {
                    frame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
                }
            }
        });
        Platform.runLater(() -> {
            try {
                UserView.getInstance().displayUserView(new Stage());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * @return true if the input is a valid integer greater than zero;  false otherwise.
     */
    private boolean isPositiveNumber(String input) {
        try {
            int value = Integer.parseInt(input);
            return value > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Shows an error dialog informing the user that form inputs are invalid. */
    private void showValidationError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText("Invalid Input");
        alert.setContentText("Please enter positive non-zero numbers in both fields.");
        alert.showAndWait();
    }

    /**
     * Aggregates ticket statistics from text files under the "racuni" folder.
     *
     * @return a pair where key is the ticket count and value is the total income with currency suffix
     */
    @NotNull
    public Pair<String, String> getTicketsInfo()
    {
        Path ticketsFolderPath = Paths.get("racuni");
        if (!(Files.exists(ticketsFolderPath) && Files.isDirectory(ticketsFolderPath))) {
            return new Pair<>("0", "0 BAM");
        }

        File[] files = ticketsFolderPath.toFile().listFiles(f ->
                f.isFile() && f.getName().toLowerCase().endsWith(".txt") && f.getName().startsWith("racun_"));
        if (files == null || files.length == 0) {
            return new Pair<>("0", "0 BAM");
        }

        int ticketsCount = files.length;
        int total = 0;

        for (File file : files) {
            try {
                java.util.List<String> lines = Files.readAllLines(file.toPath());
                if (lines.size() >= 4) {
                    String line = lines.get(3);

                    int priceStart = line.indexOf("Cijena:");
                    if (priceStart >= 0) {
                        String afterCijena = line.substring(priceStart + "Cijena:".length()).trim();
                        int bamPos = afterCijena.indexOf("BAM");
                        if (bamPos > 0) {
                            String amountStr = afterCijena.substring(0, bamPos).trim();
                            total += amountStr.isEmpty() ? 0 : Integer.parseInt(amountStr);
                        }
                    }
                }
            } catch (IOException ignored) {

            }
        }

        String totalStr = total + " BAM";
        return new Pair<>(String.valueOf(ticketsCount), totalStr);
    }
}