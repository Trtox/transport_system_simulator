package net.etfbl.pj2.gui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import net.etfbl.pj2.algorithms.BreadthFirstSearch;
import net.etfbl.pj2.algorithms.LowestPriceDijkstra;
import net.etfbl.pj2.algorithms.ShortestTimeDijkstra;
import net.etfbl.pj2.model.*;
import net.etfbl.pj2.model.PathResult;
import net.etfbl.pj2.utility.DataParser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.LocalTime;

/**
 * Controller class for handling user interactions in the transport system simulator.
 * This controller manages the user interface for searching routes, displaying optimal and top routes,
 * and generating tickets for selected routes. It processes user inputs, interacts with the underlying
 * data and algorithms, and updates the JavaFX UI components accordingly.
 */
public class UserController {

    @FXML
    private ComboBox<String> startCityComboBox;
    @FXML
    private ComboBox<String> endCityComboBox;
    @FXML
    private ToggleGroup criteriaGroup;
    @FXML
    private RadioButton timeRadio;
    @FXML
    private RadioButton priceRadio;
    @FXML
    private RadioButton transferRadio;
    @FXML
    private TableView<RouteSegment> optimalRouteTable;
    @FXML
    private TableColumn<RouteSegment, String> colStart;
    @FXML
    private TableColumn<RouteSegment, String> colEnd;
    @FXML
    private TableColumn<RouteSegment, String> colType;
    @FXML
    private TableColumn<RouteSegment, Integer> colPrice;
    @FXML
    private Label summaryLabel;

    /**
     * Initializes the UserController by setting up UI components, such as combo boxes and toggle groups,
     * and populates city selection lists. Also configures table columns for displaying route segments.
     */
    @FXML
    public void initialize() {
        criteriaGroup = new ToggleGroup();
        timeRadio.setToggleGroup(criteriaGroup);
        priceRadio.setToggleGroup(criteriaGroup);
        transferRadio.setToggleGroup(criteriaGroup);

        colStart.setText("Polazak");
        colStart.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().start() +
                (cd.getValue().departure() == null || cd.getValue().departure().isEmpty() ? "" : " (" + cd.getValue().departure() + ")")
        ));

        colEnd.setText("Dolazak");
        colEnd.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().end() +
                (cd.getValue().arrival() == null || cd.getValue().arrival().isEmpty() ? "" : " (" + cd.getValue().arrival() + ")")
        ));

        colType.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().type()));
        colPrice.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().price()));

        List<String> sorted = getSortedCityNames();
        ObservableList<String> cityList = FXCollections.observableArrayList(sorted);
        startCityComboBox.setItems(cityList);
        endCityComboBox.setItems(cityList);
    }

    /**
     * Handles the action of searching for an optimal route based on selected criteria (time, price, or transfers).
     * Reads the selected start and end cities and search criteria, computes and displays the optimal route
     * in the table, and updates the summary label with total time and price.
     * If no valid input is provided or no route is found, displays an appropriate message.
     */
    public void onSearchRoute() {
        Toggle selectedCriteria = criteriaGroup.getSelectedToggle();
        if (selectedCriteria == null) {
            summaryLabel.setText("Kriterijum nije izabran.");
            optimalRouteTable.setItems(FXCollections.observableArrayList());
            return;
        }

        Criteria criteria = null;
        if (selectedCriteria.equals(timeRadio)) {
            criteria = Criteria.TIME;
        } else if (selectedCriteria.equals(priceRadio)) {
            criteria = Criteria.PRICE;
        } else if (selectedCriteria.equals(transferRadio)) {
            criteria = Criteria.TRANSFERS;
        }

        String startCity = startCityComboBox.getValue();
        String endCity = endCityComboBox.getValue();

        if (startCity == null || endCity == null) {
            summaryLabel.setText("Odaberite i početni i odredišni grad.");
            optimalRouteTable.setItems(FXCollections.observableArrayList());
            return;
        }

        Graph graph = DataParser.getInstance().getGraph();
        List<PathResult> sorted = computeSortedResults(criteria, startCity, endCity, graph);
        if (sorted.isEmpty()) {
            summaryLabel.setText("Nije pronađena nijedna ruta.");
            optimalRouteTable.setItems(FXCollections.observableArrayList());
            return;
        }
        List<Node> bestPath = sorted.getFirst().path();

        GraphView.getInstance().markGraph(bestPath);

        SegmentsResult sr = buildSegmentsBySchedule(bestPath, graph);
        ObservableList<RouteSegment> data = FXCollections.observableArrayList(sr.segments);
        int totalPrice = sr.segments.stream().mapToInt(RouteSegment::price).sum();
        optimalRouteTable.setItems(data);
        int hours = sr.totalMinutes / 60;
        int minutes = sr.totalMinutes % 60;
        String totalText = (hours < 24)
                ? String.format("%02d:%02d", hours, minutes)
                : (hours + "h " + minutes + "min");
        summaryLabel.setText("Ukupno: " + totalText + ", Cijena: " + totalPrice + " BAM.");
    }

    /**
     * Displays the top available routes between the selected cities based on the chosen criteria.
     * Opens a new window showing multiple route options for the user to compare.
     * If no routes are available or input is invalid, shows an alert dialog.
     * @throws IOException if there is an error displaying the top routes view.
     */
    @FXML
    private void onShowTopRoutes() throws IOException {
        Toggle selectedCriteria = criteriaGroup.getSelectedToggle();
        if (selectedCriteria == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Kriterijum nije izabran.");
            a.showAndWait();
            return;
        }
        Criteria criteria = selectedCriteria.equals(timeRadio) ? Criteria.TIME
                : selectedCriteria.equals(priceRadio) ? Criteria.PRICE
                : Criteria.TRANSFERS;

        String startCity = startCityComboBox.getValue();
        String endCity = endCityComboBox.getValue();
        if (startCity == null || endCity == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Odaberite i početni i odredišni grad.");
            a.showAndWait();
            return;
        }

        Graph graph = DataParser.getInstance().getGraph();
        List<PathResult> results = computeSortedResults(criteria, startCity, endCity, graph);

        ArrayList<List<RouteSegment>> routesForView = new ArrayList<>();
        for (PathResult pr : results) {
            List<RouteSegment> segments = buildSegmentsForPath(pr, graph);
            if (!segments.isEmpty()) {
                routesForView.add(segments);
            }
        }

        if (routesForView.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Nema dostupnih ruta za prikaz.");
            a.showAndWait();
            return;
        }

        TopRoutesView topRoutesView = new TopRoutesView();
        topRoutesView.displayTopRoutesView(new Stage(), routesForView);
    }

    /**
     * Generates and saves a ticket  for the currently displayed route in the table.
     * The ticket includes route details, purchase time, and a breakdown of all route segments.
     * Saves the ticket to a file in the "racuni" directory and informs the user of the outcome.
     * If no route is selected, shows an alert to the user.
     */
    @FXML
    private void onBuySelectedRoute() {
        var items = optimalRouteTable.getItems();
        if (items == null || items.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Nema prikazane rute. Prvo pritisnite \"Nađi rutu\".").showAndWait();
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String startCity = startCityComboBox.getValue();
        String endCity = endCityComboBox.getValue();

        StringBuilder sb = new StringBuilder();
        sb.append("KARTA\n");
        if (startCity != null && endCity != null) {
            sb.append("Ruta: ").append(startCity).append(" -> ").append(endCity).append('\n');
        } else {
            sb.append("Ruta: (nepoznata)\n");
        }
        sb.append("Vrijeme kupovine: ")
                .append(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").format(now))
                .append('\n');

        if (summaryLabel != null && summaryLabel.getText() != null && !summaryLabel.getText().isEmpty()) {
            sb.append(summaryLabel.getText()).append('\n');
        }
        sb.append('\n');

        sb.append(String.format("%-20s %-20s %-15s %-8s%n", "Polazak", "Dolazak", "Vrijeme", "Cijena"));

        for (var seg : items) {
            String dep = seg.departure() == null ? "" : seg.departure();
            String arr = seg.arrival() == null ? "" : seg.arrival();
            String time = (dep.isEmpty() && arr.isEmpty()) ? "" : (dep + " -> " + arr);
            sb.append(String.format("%-20s %-20s %-15s %-8s%n",
                    seg.start(), seg.end(), time, seg.price()));
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
                DateTimeFormatter.ofPattern("ddMMyyyy").format(now),
                DateTimeFormatter.ofPattern("HHmmss").format(now)
        );
        Path out = folder.resolve(fileName);

        try {
            Files.writeString(out, sb.toString());
            new Alert(Alert.AlertType.INFORMATION, "Račun je generisan i sačuvan u: " + out.toAbsolutePath()).showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Greška pri čuvanju računa: " + ex.getMessage()).showAndWait();
        }
    }

    /**
     * Builds a list of RouteSegment objects for a given path result using the provided graph.
     * @param pr    the path result containing the sequence of nodes
     * @param graph the graph to extract edge and schedule information from
     * @return list of route segments representing the path (never null)
     */
    @NotNull
    private List<RouteSegment> buildSegmentsForPath(PathResult pr, Graph graph) {
        ArrayList<RouteSegment> data = new ArrayList<>();
        if (pr == null || pr.path() == null || pr.path().size() < 2) return data;
        List<Node> path = pr.path();
        SegmentsResult sr = buildSegmentsBySchedule(path, graph);
        return new ArrayList<>(sr.segments);
    }

    /**
     * Computes and sorts the best path results between start and end cities according to the given criteria.
     * @param criteria  the optimization criteria (time, price, or transfers)
     * @param startCity the starting city name
     * @param endCity   the destination city name
     * @param graph     the graph containing all nodes and edges
     * @return a list of up to 5 best path results sorted accordingly (never null)
     */
    @NotNull
    private List<PathResult> computeSortedResults(Criteria criteria, String startCity, String endCity, Graph graph) {
        String startBusName  = (startCity != null && !startCity.isEmpty()) ? "A" + startCity.substring(1) : null;
        String startTrainName= (startCity != null && !startCity.isEmpty()) ? "Z" + startCity.substring(1) : null;
        String endBusName    = (endCity  != null && !endCity.isEmpty())   ? "A" + endCity.substring(1)   : null;
        String endTrainName  = (endCity  != null && !endCity.isEmpty())   ? "Z" + endCity.substring(1)   : null;

        Node startBus  = startBusName  == null ? null : graph.findNode(startBusName).orElse(null);
        Node startTrain= startTrainName== null ? null : graph.findNode(startTrainName).orElse(null);
        Node endBus    = endBusName    == null ? null : graph.findNode(endBusName).orElse(null);
        Node endTrain  = endTrainName  == null ? null : graph.findNode(endTrainName).orElse(null);

        Set<Node> goalNodes = new HashSet<>();
        if (endBus != null) goalNodes.add(endBus);
        if (endTrain != null) goalNodes.add(endTrain);

        List<PathResult> results = new ArrayList<>();
        if (criteria == Criteria.TRANSFERS) {
            if (startBus  != null) results.addAll(BreadthFirstSearch.bfsKBest(graph, startBus, goalNodes, 5));
            if (startTrain!= null) results.addAll(BreadthFirstSearch.bfsKBest(graph, startTrain, goalNodes, 5));
        } else if (criteria == Criteria.TIME) {
            if (startBus  != null) results.addAll(ShortestTimeDijkstra.shortestKTimesFromNow(graph, startBus, goalNodes, 5));
            if (startTrain!= null) results.addAll(ShortestTimeDijkstra.shortestKTimesFromNow(graph, startTrain, goalNodes, 5));
        } else { // PRICE
            if (startBus  != null) results.addAll(LowestPriceDijkstra.lowestKPricesNow(graph, startBus, goalNodes, 5));
            if (startTrain!= null) results.addAll(LowestPriceDijkstra.lowestKPricesNow(graph, startTrain, goalNodes, 5));
        }

        Comparator<PathResult> cmp = getPathResultComparator(criteria, graph);
        results.sort(cmp);
        if (results.size() > 5) results = results.subList(0, 5);
        return results;
    }

    /**
     * Returns a comparator for sorting path results based on the selected criteria.
     * @param criteria the optimization criteria
     * @param graph    the graph for cost/time/transfer calculations
     * @return comparator for PathResult objects
     */
    @NotNull
    private Comparator<PathResult> getPathResultComparator(Criteria criteria, Graph graph) {
        Comparator<PathResult> cmp;
        if (criteria == Criteria.PRICE) {
            cmp = Comparator.<PathResult>comparingInt(pr -> getTotalCost(pr.path(), graph))
                    .thenComparingInt(pr -> getTotalTime(pr.path(), graph))
                    .thenComparingInt(pr -> countTransfersOnPath(graph, pr.path()));
        } else if (criteria == Criteria.TIME) {
            cmp = Comparator.<PathResult>comparingInt(pr -> getTotalTime(pr.path(), graph))
                    .thenComparingInt(pr -> getTotalCost(pr.path(), graph))
                    .thenComparingInt(pr -> countTransfersOnPath(graph, pr.path()));
        } else { // TRANSFERS
            cmp = Comparator.comparingInt((PathResult pr) -> countTransfersOnPath(graph, pr.path()))
                    .thenComparingInt(pr -> getTotalTime(pr.path(), graph))
                    .thenComparingInt(pr -> getTotalCost(pr.path(), graph));
        }
        return cmp;
    }

    /**
     * Counts the number of transfers (i.e., connector edges) along a given path.
     * @param g    the graph containing the edges
     * @param path the list of nodes representing the path
     * @return the number of transfers on the path
     */
    private int countTransfersOnPath(Graph g, @NotNull List<Node> path) {
        int transfers = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Node from = path.get(i);
            Node to = path.get(i + 1);
            for (Edge e : g.getNeighbors(from)) {
                if (e.to().equals(to) && isTransferConnector(e)) {
                    transfers++;
                    break;
                }
            }
        }
        return transfers;
    }

    /**
     * Sorts city names for comboBoxes in ascending order.
     * @return List of strings that represent sorted city names
     */
    @NotNull
    private static List<String> getSortedCityNames() {
        var cities = DataParser.getInstance().getCities().keySet();
        List<String> sorted = new ArrayList<>(cities);
        sorted.sort((a, b) -> {
            int[] pa = parseGridCoords(a);
            int[] pb = parseGridCoords(b);
            if (pa != null && pb != null) {
                int cmp = Integer.compare(pa[0], pb[0]);
                if (cmp != 0) return cmp;
                return Integer.compare(pa.length > 1 ? pa[1] : 0, pb.length > 1 ? pb[1] : 0);
            }
            return a.compareTo(b);
        });
        return sorted;
    }

    /**
     * Builds route segments for a given path according to the schedule, calculating absolute times and prices.
     * @param path  the list of nodes representing the path
     * @param graph the graph providing edge and schedule information
     * @return a {@link SegmentsResult} containing the list of segments and total travel time in minutes
     */
    @NotNull
    @Contract("null, _ -> new")
    private SegmentsResult buildSegmentsBySchedule(List<Node> path, Graph graph) {
        ArrayList<RouteSegment> out = new ArrayList<>();
        if (path == null || path.size() < 2) return new SegmentsResult(out, 0);

        Integer firstDepartureAbs = null;
        int clockAbs = 0;

        for (int i = 0; i < path.size() - 1; i++) {
            Node from = path.get(i);
            Node to = path.get(i + 1);

            Edge edge = null;
            for (Edge e : graph.getNeighbors(from)) {
                if (e.to().equals(to)) { edge = e; break; }
            }
            if (edge == null) continue;

            String start = from.toString();
            String end = to.toString();
            String type;
            if (!start.isEmpty()) {
                char p = start.charAt(0);
                if (p == 'A') type = "Autobus"; else if (p == 'Z') type = "Voz"; else type = "Prevoz";
            } else type = "Prevoz";

            int price = edge.price();
            String depStr;
            String arrStr;

            if (isTransferConnector(edge)) {
                int transferMin = (edge.minTransferTime() > 0 ? edge.minTransferTime() : 5);
                int depAbs = (firstDepartureAbs == null ? 0 : clockAbs);
                int arrAbs = depAbs + transferMin;
                if (firstDepartureAbs == null) firstDepartureAbs = depAbs;
                depStr = fmtHm(depAbs);
                arrStr = fmtHm(arrAbs);
                clockAbs = arrAbs;
                out.add(new RouteSegment(start, end, type, price, depStr, arrStr));
                continue;
            }

            if (edge.departureTime() != null) {
                int duration = edge.durationMin();
                int depAbs = edge.departureTime().getHour() * 60 + edge.departureTime().getMinute();
                if (firstDepartureAbs == null) {
                    firstDepartureAbs = depAbs;
                } else {
                    while (depAbs < clockAbs) depAbs += 24 * 60;
                }
                int arrAbs = depAbs + duration;

                depStr = fmtHm(depAbs);
                arrStr = fmtHm(arrAbs);
                clockAbs = arrAbs;

                out.add(new RouteSegment(start, end, type, price, depStr, arrStr));
            }
        }

        int total = 0;
        if (firstDepartureAbs != null && !out.isEmpty()) {
            total = clockAbs - firstDepartureAbs;
        }
        return new SegmentsResult(out, total);
    }

    /**
     * Calculates the total travel time in minutes for a given path.
     * @param path  the list of nodes representing the path
     * @param graph the graph for segment calculations
     * @return total travel time in minutes
     */
    private int getTotalTime(List<Node> path, Graph graph) {
        SegmentsResult sr = buildSegmentsBySchedule(path, graph);
        return sr.totalMinutes;
    }

    /**
     * Calculates the total cost for a given path.
     * @param path  the list of nodes representing the path
     * @param graph the graph for segment calculations
     * @return the total cost (sum of all segment prices)
     */
    private int getTotalCost(List<Node> path, Graph graph) {
        SegmentsResult sr = buildSegmentsBySchedule(path, graph);
        int sum = 0;
        for (RouteSegment seg : sr.segments) sum += seg.price();
        return sum;
    }

    /** Parses a city id of the form G_<row>_<col> to [row, col]; returns null if not parsable. */
    private static int[] parseGridCoords(String name) {
        if (name == null) return null;
        if (!name.startsWith("G_")) return null;
        String[] parts = name.split("_");
        if (parts.length != 3) return null;
        try {
            int r = Integer.parseInt(parts[1]);
            int c = Integer.parseInt(parts[2]);
            return new int[]{r, c};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Formats absolute minutes since midnight into a "HH:mm" string, wrapping around 24 hours.
     * @param absMinutes the absolute minutes to format
     * @return formatted time string in "HH:mm" format
     */
    @NotNull
    @Contract(pure = true)
    private static String fmtHm(int absMinutes) {
        int m = ((absMinutes % (24 * 60)) + (24 * 60)) % (24 * 60);
        int h = m / 60;
        int min = m % 60;
        return String.format("%02d:%02d", h, min);
    }

    /**
     * Determines if a given edge represents a transfer connector (zero price and midnight departure).
     * @param e the edge to check
     * @return true if the edge is a transfer connector, false otherwise
     */
    private static boolean isTransferConnector(@NotNull Edge e) {
        return e.price() == 0 && e.departureTime() != null && e.departureTime().equals(LocalTime.MIDNIGHT);
    }

    /**
     * Simple record class holding a list of route segments and the total travel time in minutes.
     * Used as a helper result for segment-building methods.
     * @param segments     the list of route segments
     * @param totalMinutes the total travel time in minutes
     */
    private record SegmentsResult(List<RouteSegment> segments, int totalMinutes) {}

}
