package net.etfbl.pj2.model;

import java.time.LocalTime;
import java.util.*;

/**
 * Represents a directed graph of stations and routes.
 * Maintains nodes (stations) and edges (departures between stations).
 * Used separately from a GraphStream Graph type.
 */
public class Graph {
    private final Map<Node, List<Edge>> adjacencyList = new HashMap<>();
    private final Map<String, Node> nodesById = new HashMap<>();

    /**
     * Returns an existing node with the given id, or creates it if it does not exist.
     * @param id   unique identifier of the node
     * @param type the type of station this node represents
     * @param city the city this node belongs to
     * @return the existing or newly created Node.
     */
    public Node getOrCreateNode(String id, StationType type, City city) {
        return nodesById.computeIfAbsent(id, k -> {
            Node n = new Node(id, type, city);
            adjacencyList.put(n, new ArrayList<>());
            return n;
        });
    }

    /**
     * Finds a node by its identifier.
     * @param id the identifier of the node
     * @return an Optional containing the node if found, or empty otherwise
     */
    public Optional<Node> findNode(String id) {
        return Optional.ofNullable(nodesById.get(id));
    }

    /**
     * Returns all nodes in this graph.
     * @return an unmodifiable collection of all nodes
     */
    public Collection<Node> getNodes() {
        return Collections.unmodifiableCollection(nodesById.values());
    }

    /**
     * Adds a directed edge (departure) between two nodes.
     * @param from            the origin node
     * @param to              the destination node
     * @param departureTime   the time of departure
     * @param durationMin     travel duration in minutes
     * @param price           travel price
     * @param minTransferTime minimum transfer time required before next departure
     */
    public void addDeparture(Node from, Node to,
                             LocalTime departureTime,
                             int durationMin,
                             int price,
                             int minTransferTime) {
        adjacencyList.computeIfAbsent(from, __ -> new ArrayList<>());
        adjacencyList.computeIfAbsent(to, __ -> new ArrayList<>());
        adjacencyList.get(from).add(new Edge(to, departureTime, durationMin, price, minTransferTime));
    }

    /**
     * Gets all outgoing edges from the given node.
     * @param node the node whose neighbors are requested
     * @return a list of edges from the given node, or an empty list if none exist
     */
    public List<Edge> getNeighbors(Node node) {
        return adjacencyList.getOrDefault(node, List.of());
    }

}