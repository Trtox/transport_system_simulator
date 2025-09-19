package net.etfbl.pj2.algorithms;

import net.etfbl.pj2.model.Edge;
import net.etfbl.pj2.model.Graph;
import net.etfbl.pj2.model.Node;
import net.etfbl.pj2.model.PathResult;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.time.LocalTime;

/**
 * Implements a breadth-first search (BFS) algorithm to find k-best paths in a transport graph.
 * Provides functionality for finding multiple path alternatives ranked by transfers, travel time, and cost.
 */
public class BreadthFirstSearch {

    private record EdgeRef(Node from, Node to) {}

    /**
     * Finds k best paths from the start node to any of the goal nodes.
     * Paths are ordered by number of transfers, then by travel time, then by total price.
     * @param graph the graph containing nodes and edges
     * @param start the starting node
     * @param goals the set of goal nodes
     * @param k the maximum number of paths to find
     * @return a list of up to k path results, ordered by efficiency
     */
    @NotNull
    public static List<PathResult> bfsKBest(Graph graph, Node start, Set<Node> goals, int k) {
        if (k <= 0) return Collections.emptyList();
        PathResult first = bfs(graph, start, goals, Collections.emptySet(), Collections.emptySet());
        if (first.path().isEmpty() || first.totalTime() == Integer.MAX_VALUE) return Collections.emptyList();

        class Scored {
            final PathResult r; final int tr; final int tm; final int pr;
            Scored(@NotNull PathResult r) { this.r = r; this.tr = countTransfers(graph, r.path()); this.tm = r.totalTime(); this.pr = r.totalCost(); }
        }

        Comparator<Scored> ORDER = Comparator
                .comparingInt((Scored s) -> s.tr)
                .thenComparingInt(s -> s.tm)
                .thenComparingInt(s -> s.pr);

        List<PathResult> A = new ArrayList<>();
        A.add(first);
        PriorityQueue<Scored> B = new PriorityQueue<>(ORDER);

        for (int idx = 1; idx < k; idx++) {
            PathResult prevBest = A.get(idx - 1);
            List<Node> prevPath = prevBest.path();

            for (int i = 0; i < prevPath.size() - 1; i++) {
                Node spurNode = prevPath.get(i);
                List<Node> rootPath = new ArrayList<>(prevPath.subList(0, i + 1));

                Set<EdgeRef> excludedEdges = new HashSet<>();
                for (PathResult ar : A) {
                    List<Node> p = ar.path();
                    if (p.size() > i && p.subList(0, i + 1).equals(rootPath)) {
                        excludedEdges.add(new EdgeRef(p.get(i), p.get(i + 1)));
                    }
                }

                Set<Node> excludedNodes = new HashSet<>(rootPath);
                excludedNodes.remove(spurNode);

                PathResult spur = bfs(graph, spurNode, goals, excludedEdges, excludedNodes);
                if (spur.path().isEmpty() || spur.totalTime() == Integer.MAX_VALUE) continue;

                List<Node> combined = new ArrayList<>(rootPath);
                List<Node> spurPath = spur.path();
                combined.addAll(spurPath.subList(1, spurPath.size()));

                int totalPrice = sumPrice(graph, combined);
                int totalTime = sumTime(graph, combined);
                if (totalPrice == Integer.MAX_VALUE || totalTime == Integer.MAX_VALUE) continue;

                PathResult cand = new PathResult(combined, totalPrice, totalTime);

                boolean duplicate = A.stream().anyMatch(r -> r.path().equals(cand.path())) ||
                                    B.stream().anyMatch(s -> s.r.path().equals(cand.path()));
                if (!duplicate) B.add(new Scored(cand));
            }

            if (B.isEmpty()) break;
            A.add(B.poll().r);
        }

        return A;
    }

    /**
     * Runs BFS to find a single path from the start node to any of the goals, excluding given edges and nodes.
     * @param graph the graph
     * @param start the starting node
     * @param goals the set of goal nodes
     * @param excludedEdges edges to exclude during search
     * @param excludedNodes nodes to exclude during search
     * @return the found PathResult, or an empty one if no path exists
     */
    @NotNull
    @Contract("_, _, _, _, _ -> new")
    private static PathResult bfs(@NotNull Graph graph, Node start, Set<Node> goals,
                                  Set<EdgeRef> excludedEdges, Set<Node> excludedNodes) {
        Map<Node, Node> parent = new HashMap<>();
        Map<Node, Integer> rides = new HashMap<>();
        Map<Node, Integer> transfers = new HashMap<>();
        Map<Node, Integer> totalPrice = new HashMap<>();
        Map<Node, Integer> totalTime = new HashMap<>();

        Queue<Node> queue = new LinkedList<>();

        for (Node node : graph.getNodes()) {
            rides.put(node, Integer.MAX_VALUE);
            transfers.put(node, Integer.MAX_VALUE);
            totalPrice.put(node, Integer.MAX_VALUE);
            totalTime.put(node, Integer.MAX_VALUE);
        }

        rides.put(start, 0);
        transfers.put(start, 0);
        totalPrice.put(start, 0);
        totalTime.put(start, 0);
        queue.add(start);

        Node reachedGoal = null;

        while (!queue.isEmpty()) {
            Node current = queue.poll();

            if (goals.contains(current)) { reachedGoal = current; break; }
            if (excludedNodes.contains(current) && !current.equals(start)) continue;

            for (Edge edge : graph.getNeighbors(current)) {
                if (edge == null || edge.to() == null) continue;
                if (excludedEdges.contains(new EdgeRef(current, edge.to()))) continue;
                if (excludedNodes.contains(edge.to())) continue;

                Node neighbor = edge.to();

                boolean isIntraCityConnector = (edge.price() == 0 && edge.departureTime() != null && edge.departureTime().equals(LocalTime.MIDNIGHT));
                boolean startsNewRide = !isIntraCityConnector;

                int newRides = rides.get(current) + (startsNewRide ? 1 : 0);
                int newTransfers = Math.max(0, newRides - 1);

                int newPrice = safeAdd(totalPrice.get(current), edge.price());
                int newTime = safeAdd(totalTime.get(current), edge.durationMin());

                int bestTransfers = transfers.get(neighbor);
                int bestTime = totalTime.get(neighbor);
                int bestPrice = totalPrice.get(neighbor);
                int bestRides = rides.get(neighbor);

                boolean better =
                        (newTransfers < bestTransfers) ||
                        (newTransfers == bestTransfers && newTime < bestTime) ||
                        (newTransfers == bestTransfers && newTime == bestTime && newPrice < bestPrice) ||
                        (newTransfers == bestTransfers && newTime == bestTime && newPrice == bestPrice && newRides < bestRides);

                if (better) {
                    rides.put(neighbor, newRides);
                    transfers.put(neighbor, newTransfers);
                    totalPrice.put(neighbor, newPrice);
                    totalTime.put(neighbor, newTime);
                    parent.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        if (reachedGoal == null) {
            return new PathResult(Collections.emptyList(), Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        List<Node> path = reconstructPath(parent, start, reachedGoal);
        return new PathResult(path, totalPrice.get(reachedGoal), totalTime.get(reachedGoal));
    }

    /**
     * Safely adds two integers treating null or large values as infinity.
     * @param a the first value may be null
     * @param b the second value
     * @return the sum or Integer.MAX_VALUE if overflow occurs
     */
    private static int safeAdd(Integer a, int b) {
        int x = (a == null ? Integer.MAX_VALUE / 4 : a);
        if (x >= Integer.MAX_VALUE / 4) return Integer.MAX_VALUE;
        long sum = (long) x + b;
        return (sum > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) sum;
    }

    /**
     * Reconstructs the path from start to goal using a parent map.
     * @param parent map of each node to its parent
     * @param start the starting node
     * @param goal the reached goal node
     * @return the reconstructed path as a list of nodes
     */
    @NotNull
    private static List<Node> reconstructPath(Map<Node, Node> parent, Node start, Node goal) {
        LinkedList<Node> path = new LinkedList<>();
        Node current = goal;

        while (current != null && !current.equals(start)) {
            path.addFirst(current);
            current = parent.get(current);
        }
        if (current != null) {
            path.addFirst(start);
        }
        return path;
    }

    /**
     * Finds an edge in the graph between two nodes.
     * @param graph the graph
     * @param u the source node
     * @param v the destination node
     * @return the Edge if found, null otherwise
     */
    @Nullable
    private static Edge findEdge(@NotNull Graph graph, Node u, Node v) {
        for (Edge e : graph.getNeighbors(u)) if (e.to().equals(v)) return e;
        return null;
    }

    /**
     * Counts the number of transfers in a path.
     * Transfers are calculated as rides - 1, excluding intra-city connectors.
     * @param graph the graph
     * @param path the path as a list of nodes
     * @return number of transfers, or Integer.MAX_VALUE if a path invalid
     */
    private static int countTransfers(Graph graph, List<Node> path) {
        if (path == null || path.size() < 2) return 0;
        int rides = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Edge e = findEdge(graph, path.get(i), path.get(i + 1));
            if (e == null) return Integer.MAX_VALUE;
            boolean isConnector = (e.price() == 0 && e.departureTime() != null && e.departureTime().equals(LocalTime.MIDNIGHT));
            if (!isConnector) rides++;
        }
        return Math.max(0, rides - 1);
    }

    /**
     * Calculates the total price of a path
     * @param graph the graph
     * @param path the path as a list of nodes
     * @return the total cost, or Integer.MAX_VALUE if a path invalid
     */
    private static int sumPrice(Graph graph, List<Node> path) {
        if (path == null || path.size() < 2) return 0;
        int cost = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Edge e = findEdge(graph, path.get(i), path.get(i + 1));
            if (e == null) return Integer.MAX_VALUE;
            cost += Math.max(0, e.price());
        }
        return cost;
    }

    /**
     * Calculates the total travel time of a path.
     * @param graph the graph
     * @param path the path as a list of nodes
     * @return the total duration in minutes, or Integer.MAX_VALUE if a path invalid
     */
    private static int sumTime(Graph graph, List<Node> path) {
        if (path == null || path.size() < 2) return 0;
        int t = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Edge e = findEdge(graph, path.get(i), path.get(i + 1));
            if (e == null) return Integer.MAX_VALUE;
            t += Math.max(0, e.durationMin());
        }
        return t;
    }
}
