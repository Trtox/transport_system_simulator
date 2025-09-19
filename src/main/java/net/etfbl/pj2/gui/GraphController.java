package net.etfbl.pj2.gui;

import javafx.application.Platform;
import net.etfbl.pj2.model.Departure;
import net.etfbl.pj2.model.Node;
import net.etfbl.pj2.utility.DataParser;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.MultiGraph;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Singleton controller responsible for creating and managing the GraphStream visualization of the transport network.
 * Provides methods for initializing the graph, applying styles, and marking a selected path.
 */
public class GraphController {

    /**
     * Singleton instance of the GraphController.
     * */
    public static final GraphController INSTANCE = new GraphController();

    private Graph graph;

    /**
     * Private constructor to enforce a singleton pattern.
     * */
    private GraphController() {
    }

    /**
     * Returns the singleton instance of the GraphController.
     * @return the singleton instance
     */
    public static GraphController getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the underlying GraphStream graph instance.
     * @return the graph, or null if not initialized
     */
    public Graph getGraph() {
        return graph;
    }

    /**
     * Initializes the transport graph with nodes and edges parsed from departure data.
     * Applies UI styles, creates nodes for stations, and connects them with directed edges.
     */
    public void initializeGraph() {
        graph = new MultiGraph("G");
        graph.setAutoCreate(true);
        graph.setStrict(false);

        graph.setAttribute("ui.stylesheet",
                "graph { fill-color: white; } " +
                        "node { size: 40px; text-size: 20; fill-color: blue; stroke-mode: plain; stroke-color: black; } " +
                        "edge { size: 5px; fill-color: gray; }" +
                        "node.selected { fill-color: red; } " +
                        "edge.selected { fill-color: red; }" +
                        "node.train { fill-color: green; }" +
                        "node.bus { fill-color: yellow; }" +
                        "node.deselected { fill-color: blue; }" +
                        "edge.deselected { fill-color: gray; }"
        );
        graph.setAttribute("ui.quality");
        graph.setAttribute("ui.antialias");

        for (Vector<Departure> departuresVector : DataParser.getInstance().getDepartures().values()) {

            for (Departure departure : departuresVector) {
                StringBuilder startNodeSB = new StringBuilder(departure.start().getStationName());
                startNodeSB.setCharAt(0, 'G');

                StringBuilder destinationNodeSB = new StringBuilder(departure.destination().name());
                destinationNodeSB.setCharAt(0, 'G');

                String startNode = startNodeSB.toString();
                String destinationNode = destinationNodeSB.toString();

                if (graph.getNode(startNode) == null) {
                    graph.addNode(startNode).setAttribute("ui.label", startNode);
                }
                if (graph.getNode(destinationNode) == null) {
                    graph.addNode(destinationNode).setAttribute("ui.label", destinationNode);
                }

                String edgeId = startNode + "-" + destinationNode;
                if (graph.getEdge(edgeId) == null && startNode.compareTo(destinationNode) != 0) {
                    graph.addEdge(edgeId, startNode, destinationNode, true);
                }
            }
        }
    }

    /**
     * Highlights the given path on the graph.
     * All nodes and edges in the path are marked as "selected" while others are reset to "deselected".
     * @param bestPath the list of nodes representing the best path
     */
    public void markGraph(List<Node> bestPath) {
        if (graph == null) return;
        if (bestPath == null || bestPath.isEmpty()) {
            clearMarks();
            return;
        }

        List<String> nodeIds = bestPath.stream().map(GraphController::toCityId).toList();
        List<String[]> edgePairs = new ArrayList<>();
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            edgePairs.add(new String[]{ nodeIds.get(i), nodeIds.get(i + 1) });
        }

        Platform.runLater(() -> {
            clearMarks();
            for (String id : nodeIds) {
                var n = graph.getNode(id);
                if (n != null) n.setAttribute("ui.class", "selected");
            }
            for (String[] pair : edgePairs) {
                String a = pair[0];
                String b = pair[1];
                String forwardId = a + "-" + b;
                String reverseId = b + "-" + a;
                var e = graph.getEdge(forwardId);
                if (e == null) e = graph.getEdge(reverseId);
                if (e != null) e.setAttribute("ui.class", "selected");
            }
        });
    }


    /**
     * Clears all highlights from the graph, resetting nodes and edges to "deselected".
     */
    private void clearMarks() {
        if (graph == null) return;
        graph.nodes().forEach(n -> n.setAttribute("ui.class", "deselected" ));
        graph.edges().forEach(e -> e.setAttribute("ui.class", "deselected" ));
    }

    /**
     * Converts a Node into a string identifier used in the GraphStream graph.
     * @param node the node to convert
     * @return the graph node identifier string
     */
    @NotNull
    private static String toCityId(@NotNull Node node) {
        String raw = node.toString();
        int space = raw.indexOf(' ');
        String id = (space >= 0) ? raw.substring(0, space) : raw;
        if (id.isEmpty()) return "G_0_0";
        StringBuilder sb = new StringBuilder(id);
        sb.setCharAt(0, 'G');
        return sb.toString();
    }

}