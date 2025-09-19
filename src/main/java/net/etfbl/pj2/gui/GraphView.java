package net.etfbl.pj2.gui;

import java.util.List;

import net.etfbl.pj2.model.Node;

/**
 * Singleton view layer for displaying and marking the transport graph.
 * Provides methods to show the graph visualization and highlight a given best path.
 * Delegates rendering logic to GraphController.
 */
public class GraphView {

    /**
     * Singleton instance of the GraphView.
     * */
    public static final GraphView INSTANCE = new GraphView();

    /**
     * Private constructor that initializes the underlying graph via GraphController.
     */
    private GraphView() {
        GraphController.getInstance().initializeGraph();
    }

    /**
     * Returns the singleton instance of the GraphView.
     * @return the GraphView instance
     */
    public static GraphView getInstance() {
        return INSTANCE;
    }

    /**
     * Opens a window to display the transport graph visualization.
     */
    public void displayGraph() {
        GraphController.getInstance().getGraph().display();
    }

    /**
     * Highlights the given path in the graph visualization.
     * @param bestPath list of nodes representing the path to highlight
     */
    public void markGraph(List<Node> bestPath) { GraphController.getInstance().markGraph(bestPath);}


}
