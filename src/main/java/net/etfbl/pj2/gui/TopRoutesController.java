package net.etfbl.pj2.gui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import net.etfbl.pj2.model.RouteSegment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.beans.property.ReadOnlyStringWrapper;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.nio.file.Paths;

/**
 * Controller for the Top Routes dialog.
 * Displays up to five candidate routes, shows segment details in a table, and
 * computes a schedule-based total (first departure -> final arrival) with price.
 * Provides an action to generate and save a ticket for the currently selected route.
 */
public class TopRoutesController {

    private final ArrayList<List<RouteSegment>> topRoutes = new ArrayList<>();

    private ArrayList<List<RouteSegment>> pendingRoutes = null;

    /**
     * Aggregate of total schedule minutes and summed price for a route.
     */
    private record TripTotals(long minutes, int price) {
    }

    /**
     * Computes the total duration and price for a route using only segment HH:mm strings.
     * @param route ordered list of segments composing a candidate route
     * @return total minutes from first departure to last arrival, and summed price
     */
    private TripTotals computeTotalsBySchedule(List<RouteSegment> route) {
        if (route == null || route.isEmpty()) return new TripTotals(0L, 0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        int totalPrice = 0;
        Integer firstDepAbs = null;
        int lastArrivalAbs = -1;
        int dayOffset = 0;

        for (RouteSegment segment : route) {
            totalPrice += segment.price();
            try {
                String depStr = segment.departure();
                String arrStr = segment.arrival();
                if (depStr == null || depStr.isEmpty() || arrStr == null || arrStr.isEmpty()) {
                    continue;
                }
                LocalTime depTime = LocalTime.parse(depStr, formatter);
                LocalTime arrTime = LocalTime.parse(arrStr, formatter);

                int depMin = depTime.getHour() * 60 + depTime.getMinute();
                int arrMin = arrTime.getHour() * 60 + arrTime.getMinute();

                if (firstDepAbs == null) {
                    int arrAbs = arrMin;
                    if (arrAbs < depMin) arrAbs += 24 * 60;
                    firstDepAbs = depMin;
                    lastArrivalAbs = arrAbs;
                    continue;
                }

                int depAbs = depMin + dayOffset * 24 * 60;
                while (depAbs < lastArrivalAbs) {
                    dayOffset++;
                    depAbs += 24 * 60;
                }
                int arrAbs = arrMin + dayOffset * 24 * 60;
                if (arrAbs < depAbs) arrAbs += 24 * 60;

                lastArrivalAbs = arrAbs;
            } catch (Exception ignore) {}
        }
        long minutes = (firstDepAbs != null && lastArrivalAbs >= 0) ? (long)(lastArrivalAbs - firstDepAbs) : 0L;
        return new TripTotals(minutes, totalPrice);
    }

    /**
     * Populates the combo box and table from the current topRoutes list, wires the
     * selection listener to recompute and display the summary, and selects the first route by default.
     */
    private void populateFromTopRoutes() {
        if (routePickerComboBox == null || routeTable == null) return;

        routePickerComboBox.getItems().clear();
        int count = Math.min(5, topRoutes.size());
        for (int i = 0; i < count; i++) {
            routePickerComboBox.getItems().add("Ruta " + (i + 1));
        }
        routePickerComboBox.getSelectionModel().selectedIndexProperty().removeListener((obs, o, n) -> {});
        routePickerComboBox.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            int idx = newIdx == null ? -1 : newIdx.intValue();
            if (idx >= 0 && idx < topRoutes.size()) {
                List<RouteSegment> selectedRoute = topRoutes.get(idx);
                routeTable.setItems(FXCollections.observableArrayList(selectedRoute));

                TripTotals t = computeTotalsBySchedule(selectedRoute);
                long hours = t.minutes / 60;
                long minutes = t.minutes % 60;
                String totalText = hours + "h " + minutes + "min";
                summaryLabel.setText("Ukupno: " + totalText + ", Price: " + t.price + " BAM");
            } else {
                routeTable.getItems().clear();
                summaryLabel.setText("");
            }
        });

        if (count > 0) {
            routePickerComboBox.getSelectionModel().select(0);
        }
    }


    /**
     * Supplies the controller with routes to display.
     * If the view is not initialized yet, routes are stored in pendingRoutes and applied on initialize.
     * @param routes up to five routes (each a list of {@link RouteSegment}); may be {@code null}
     */
    public void setRoutePickerComboBoxItems(ArrayList<List<RouteSegment>> routes) {
        topRoutes.clear();
        if (routes != null) topRoutes.addAll(routes);
        pendingRoutes = (routes == null) ? new ArrayList<>() : new ArrayList<>(routes);

        if (routePickerComboBox != null) {
            populateFromTopRoutes();
            pendingRoutes = null;
        }
    }

    @FXML
    private ComboBox<String> routePickerComboBox;

    @FXML
    private Button buyButton;

    @FXML
    private Label summaryLabel;

    @FXML
    private TableView<RouteSegment> routeTable;

    @FXML
    private TableColumn<RouteSegment, String> startColumn;

    @FXML
    private TableColumn<RouteSegment, String> destinationColumn;

    @FXML
    private TableColumn<RouteSegment, String> timeColumn;

    @FXML
    private TableColumn<RouteSegment, String> priceColumn;


    /**
     * Initializes table columns and, if pendingRoutes exist, populates the UI.
     * This method is called by JavaFX after FXML injection.
     */
    @FXML
    public void initialize() {
        startColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().start()));
        destinationColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().end()));
        timeColumn.setCellValueFactory(cd -> {
            String dep = cd.getValue().departure();
            String arr = cd.getValue().arrival();
            String val = (dep == null ? "" : dep) + ((dep != null && !dep.isEmpty() && arr != null && !arr.isEmpty()) ? " → " : "") + (arr == null ? "" : arr);
            return new ReadOnlyStringWrapper(val);
        });
        priceColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Integer.toString(cd.getValue().price())));

        if (pendingRoutes != null) {
            topRoutes.clear();
            topRoutes.addAll(pendingRoutes);
            populateFromTopRoutes();
            pendingRoutes = null;
        }
    }

    /**
     * Generates a plain-text ticket for the currently selected route and saves it under the "racuni" folder.
     * Shows a confirmation dialog on success or an error dialog on failure.
     */
    @FXML
    private void onBuySelectedRoute() {
        if (routePickerComboBox == null || routeTable == null) {
            return;
        }

        int idx = routePickerComboBox.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= topRoutes.size()) {
            new Alert(Alert.AlertType.WARNING, "Nije odabrana ruta.").showAndWait();
            return;
        }

        List<RouteSegment> selectedRoute = topRoutes.get(idx);
        if (selectedRoute == null || selectedRoute.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Prazna ruta.").showAndWait();
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        TripTotals t = computeTotalsBySchedule(selectedRoute);
        long hours = t.minutes / 60;
        long minutes = t.minutes % 60;
        int totalPrice = t.price;

        StringBuilder sb = new StringBuilder();
        sb.append("KARTA\n");
        String startCity = selectedRoute.getFirst().start();
        String endCity = selectedRoute.getLast().end();
        StringBuilder sbs = new StringBuilder(startCity);
        sbs.setCharAt(0, 'G');
        startCity = sbs.toString();

        sbs = new StringBuilder(endCity);
        sbs.setCharAt(0, 'G');
        endCity = sbs.toString();

        sb.append("Ruta: ")
          .append(startCity)
          .append(" -> ")
          .append(endCity)
          .append('\n');
        sb.append("Vrijeme kupovine: ").append(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").format(now)).append('\n');
        String totalText = (hours < 24) ? String.format("%02d:%02d", hours, minutes) : (hours + "h " + minutes + "m");
        sb.append("Ukupno: ").append(totalText).append(", Cijena: ").append(totalPrice).append(" BAM\n");
        sb.append("\n");
        sb.append(String.format("%-20s %-20s %-15s %-8s\n", "Polazak", "Dolazak", "Vrijeme", "Cijena"));
        for (RouteSegment seg : selectedRoute) {
            String dep = seg.departure() == null ? "" : seg.departure();
            String arr = seg.arrival() == null ? "" : seg.arrival();
            String time = (dep.isEmpty() && arr.isEmpty()) ? "" : (dep + " -> " + arr);
            sb.append(String.format("%-20s %-20s %-15s %-8s\n", seg.start(), seg.end(), time, seg.price()));
        }

        Path folder = null;
        Path p = Paths.get("racuni");
        if (Files.exists(p) && Files.isDirectory(p)) {
            folder = p;
        }
        if (folder == null) {
            folder = p;
            try {
                Files.createDirectories(folder);
            } catch (Exception ignored) {}
        }

        String fileName = String.format(
                "racun_%s_%s.txt",
                java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy").format(now),
                java.time.format.DateTimeFormatter.ofPattern("HHmmss").format(now)
        );
        Path out = folder.resolve(fileName);
        try {
            Files.writeString(out, sb.toString());
            new Alert(Alert.AlertType.INFORMATION, "Karta je generisana i sačuvana u: " + out.toAbsolutePath()).showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Greška pri čuvanju karte: " + ex.getMessage()).showAndWait();
        }
    }
}
