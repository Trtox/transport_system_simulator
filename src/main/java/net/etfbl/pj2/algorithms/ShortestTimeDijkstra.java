package net.etfbl.pj2.algorithms;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import net.etfbl.pj2.model.*; // Graph, Node, Edge, PathResult
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dijkstra variant that minimizes the total time while respecting price and transfers.
 * The algorithm exposes both single-route and k-best variants (using Yen's style spur paths).
 * Reported totalTime and totalCost in PathResult are computed with the same
 * schedule-based logic used by the UI, so UI sorting and algorithmic results are consistent.
 */
public class ShortestTimeDijkstra {

    /**
     * Immutable entry for the priority queue, representing a node and the minutes-since-start cost to reach it.
     * @param node the node in the graph
     * @param cost minutes since start
     */
    private record PQEntry(Node node, int cost) {
    }

    /**
     * Lightweight directed edge reference used for exclusions in Yen's algorithm.
     * @param from source node
     * @param to destination node
     */
    private record EdgeRef(Node from, Node to) { }

    /**
     * Returns a comparator for PathResult that orders by total time, then price, then number of transfers.
     * @param graph the transport graph
     * @return comparator for path results
     */
    @NotNull
    private static Comparator<PathResult> byTimeThenPriceThenTransfers(Graph graph) {
        return Comparator.comparingInt(PathResult::totalTime)
                .thenComparingInt(PathResult::totalCost)
                .thenComparingInt(pr -> countTransfersOnPath(graph, pr.path()));
    }

    /**
     * Counts the number of transfers (intra-city connectors) along a given path.
     * @param graph the transport graph
     * @param path the node path
     * @return number of transfers in the path
     */
    private static int countTransfersOnPath(Graph graph, List<Node> path) {
        if (path == null || path.size() < 2) return 0;
        int transfers = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Edge e = findEdge(graph, path.get(i), path.get(i + 1));
            if (isTransferConnector(e)) transfers++;
        }
        return transfers;
    }

    /**
     * Computes up to k earliest-arrival routes starting from the current time.
     * @param graph the transport graph
     * @param start starting station node
     * @param goals set of goal station nodes
     * @param k number of routes to compute
     * @return list of up to k path results, ordered by the earliest arrival
     */
    @NotNull
    public static List<PathResult> shortestKTimesFromNow(Graph graph, Node start, Set<Node> goals, int k) {
        LocalTime nowSarajevo = ZonedDateTime.now(ZoneId.of("Europe/Sarajevo")).toLocalTime();
        return shortestKTimes(graph, start, goals, nowSarajevo, true, k);
    }

    /**
     * Computes the earliest-arrival path from a start node to any of a set of goal nodes using Dijkstra's algorithm.
     * @param graph the transport graph
     * @param start starting station node (A_x_y or Z_x_y)
     * @param goals set of station nodes in the destination city
     * @param startTime in local, time the traveler starts at the start node
     * @param wrapToNextDay if true, missed departures wrap to the next day's service; if false, they are skipped
     * @return the shortest (earliest arrival) path result
     */
    @NotNull
    @Contract("_, _, _, _, _ -> new")
    public static PathResult shortestTime(Graph graph, Node start, Set<Node> goals, LocalTime startTime, boolean wrapToNextDay) {
        return shortestTime(graph, start, goals, startTime, wrapToNextDay, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Computes the earliest-arrival path from a start node to any of a set of goal nodes using Dijkstra's algorithm,
     * with support for excluding specific edges and nodes (used by Yen's k-shortest paths).
     * @param graph the transport graph
     * @param start starting station node
     * @param goals set of goal station nodes
     * @param startTime local time the traveler starts at the start node
     * @param wrapToNextDay if true, missed departures wrap to the next day's service; if false, they are skipped
     * @param excludedEdges set of directed edges to exclude from consideration
     * @param excludedNodes set of nodes to exclude from consideration (except start)
     * @return the shortest (earliest arrival) path result
     */
    @NotNull
    @Contract("_, _, _, _, _, _, _ -> new")
    private static PathResult shortestTime(@NotNull Graph graph, Node start, Set<Node> goals,
                                           LocalTime startTime, boolean wrapToNextDay,
                                           Set<EdgeRef> excludedEdges, Set<Node> excludedNodes) {
        final int DAY_MIN = 24 * 60;
        final int startOffset = (startTime == null ? 0 : startTime.getHour() * 60 + startTime.getMinute());

        Map<Node, Integer> dist = new HashMap<>();
        Map<Node, Node> parent = new HashMap<>();
        PriorityQueue<PQEntry> pq = new PriorityQueue<>(Comparator.comparingInt(PQEntry::cost));

        for (Node n : graph.getNodes()) dist.put(n, Integer.MAX_VALUE);
        dist.put(start, 0);
        pq.add(new PQEntry(start, 0));

        Node reachedGoal = null;
        while (!pq.isEmpty()) {
            PQEntry cur = pq.poll();
            Node u = cur.node;
            int t = cur.cost();

            if (t != dist.get(u)) continue;
            if (goals.contains(u)) { reachedGoal = u; break; }

            if (excludedNodes.contains(u) && !u.equals(start)) continue;

            for (Edge e : graph.getNeighbors(u)) {
                if (excludedEdges.contains(new EdgeRef(u, e.to()))) continue;
                if (excludedNodes.contains(e.to())) continue;

                boolean isTransferConnector = (e.price() == 0
                        && e.departureTime() != null
                        && e.departureTime().equals(LocalTime.MIDNIGHT));

                int weightMinutes;
                if (isTransferConnector) {
                    weightMinutes = 5;
                } else {
                    LocalTime dep = e.departureTime();
                    if (dep == null) continue;
                    int readyAbs = startOffset + t + Math.max(0, e.minTransferTime());
                    int readyOfDay = readyAbs % DAY_MIN;
                    int depMin = dep.getHour() * 60 + dep.getMinute();
                    int wait = depMin - readyOfDay;
                    if (wait < 0) {
                        if (!wrapToNextDay) continue;
                        wait += DAY_MIN;
                    }
                    weightMinutes = wait + e.durationMin();
                }

                int newT = t + weightMinutes;
                if (newT < dist.getOrDefault(e.to(), Integer.MAX_VALUE)) {
                    dist.put(e.to(), newT);
                    parent.put(e.to(), u);
                    pq.add(new PQEntry(e.to(), newT));
                }
            }
        }

        if (reachedGoal == null) return new PathResult(Collections.emptyList(), Integer.MAX_VALUE, Integer.MAX_VALUE);
        List<Node> path = reconstructPath(parent, start, reachedGoal);
        int totalPrice = sumPrice(graph, path);
        int scheduleMinutes = scheduleEndToEndMinutes(graph, path);
        return new PathResult(path, totalPrice, scheduleMinutes);
    }

    /**
     * Reconstructs the path from the start node to the goal node using the parent pointers.
     * @param parent map of node to its parent in the shortest path tree
     * @param start the start node
     * @param goal the goal node
     * @return list of nodes representing the path from start to goal (inclusive)
     */
    @NotNull
    private static List<Node> reconstructPath(Map<Node, Node> parent, Node start, Node goal) {
        LinkedList<Node> path = new LinkedList<>();
        Node cur = goal;
        while (cur != null && !cur.equals(start)) {
            path.addFirst(cur);
            cur = parent.get(cur);
        }
        if (cur != null) path.addFirst(start);
        return path;
    }

    /**
     * Returns the edge from u to v if present, else null.
     * @param graph the transport graph
     * @param u source node
     * @param v destination node
     * @return the edge from u to v, or null if not present
     */
    @Nullable
    private static Edge findEdge(@NotNull Graph graph, Node u, Node v) {
        for (Edge e : graph.getNeighbors(u)) if (e.to().equals(v)) return e;
        return null;
    }

    /**
     * Returns true if the edge is an intra-city transfer connector.
     * @param e edge to check
     * @return true if the edge is a transfer connector, false otherwise
     */
    private static boolean isTransferConnector(Edge e) {
        return e != null && e.price() == 0 && e.departureTime() != null && e.departureTime().equals(LocalTime.MIDNIGHT);
    }

    /**
     * Sums the monetary price along a concrete node-path.
     * @param graph the transport graph
     * @param path the node path
     * @return total price of the path, or Integer.MAX_VALUE if the path is invalid
     */
    private static int sumPrice(Graph graph, List<Node> path) {
        if (path == null || path.size() < 2) return 0;
        int cost = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Node u = path.get(i);
            Node v = path.get(i + 1);
            Edge e = findEdge(graph, u, v);
            if (e == null) return Integer.MAX_VALUE;
            cost += Math.max(0, e.price());
        }
        return cost;
    }

    /**
     * Computes the total minutes from the first real departure (or initial transfer) to the last arrival,
     * rolling departures forward by 24h as needed to maintain a non-decreasing absolute timeline.
     * @param graph the transport graph
     * @param path the node path
     * @return total scheduled minutes for the trip, or Integer.MAX_VALUE if the path is invalid
     */
    private static int scheduleEndToEndMinutes(Graph graph, List<Node> path) {
        if (path == null || path.size() < 2) return 0;
        final int DAY = 24 * 60;
        Integer firstDepAbs = null;
        int clockAbs = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Node u = path.get(i);
            Node v = path.get(i + 1);
            Edge e = findEdge(graph, u, v);
            if (e == null) return Integer.MAX_VALUE;

            if (isTransferConnector(e)) {
                int transferMin = (e.minTransferTime() > 0 ? e.minTransferTime() : 5);
                int depAbs = clockAbs;
                int arrAbs = depAbs + transferMin;
                if (firstDepAbs == null) firstDepAbs = depAbs;
                clockAbs = arrAbs;
            } else {
                if (e.departureTime() == null) return Integer.MAX_VALUE;
                int depMin = e.departureTime().getHour() * 60 + e.departureTime().getMinute();
                int duration = e.durationMin();
                int depAbs = depMin;
                while (depAbs < clockAbs) depAbs += DAY;
                int arrAbs = depAbs + duration;
                if (firstDepAbs == null) firstDepAbs = depAbs;
                clockAbs = arrAbs;
            }
        }
        return Math.max(0, clockAbs - firstDepAbs);
    }

    /**
     * Simulates traveling along a concrete node-path using the same waiting/transfer rules to get the total minutes.
     * @param graph the transport graph
     * @param path the node path
     * @param startTime the starting local time
     * @param wrapToNextDay if true, missed departures wrap to the next day's service; if false, they are skipped
     * @return total minutes simulated, or Integer.MAX_VALUE if the path is invalid
     */
    private static int simulateTravelMinutes(Graph graph, List<Node> path, LocalTime startTime, boolean wrapToNextDay) {
        if (path == null || path.size() < 2) return 0;
        final int DAY_MIN = 24 * 60;
        int now = (startTime == null ? 0 : startTime.getHour() * 60 + startTime.getMinute());
        int t = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Node u = path.get(i);
            Node v = path.get(i + 1);
            Edge e = findEdge(graph, u, v);
            if (e == null) return Integer.MAX_VALUE;
            boolean isTransferConnector = (e.price() == 0
                    && e.departureTime() != null
                    && e.departureTime().equals(LocalTime.MIDNIGHT));
            if (isTransferConnector) {
                t += 5;
            } else {
                LocalTime dep = e.departureTime();
                if (dep == null) return Integer.MAX_VALUE;
                int readyAbs = now + t + Math.max(0, e.minTransferTime());
                int readyOfDay = readyAbs % DAY_MIN;
                int depMin = dep.getHour() * 60 + dep.getMinute();
                int wait = depMin - readyOfDay;
                if (wait < 0) {
                    if (!wrapToNextDay) return Integer.MAX_VALUE;
                    wait += DAY_MIN;
                }
                t += wait + e.durationMin();
            }
        }
        return t;
    }

    /**
     * Simulates the total travel minutes along the path up to the specified (inclusive) index.
     * @param graph the transport graph
     * @param path the node path
     * @param inclusiveIndex the index in the path up to which to simulate travel
     * @param startTime the starting local time
     * @param wrapToNextDay if true, missed departures wrap to the next day
     * @return total minutes simulated up to the specified index
     */
    private static int simulateMinutesToIndex(Graph graph, @NotNull List<Node> path, int inclusiveIndex, LocalTime startTime, boolean wrapToNextDay) {
        return simulateTravelMinutes(graph, path.subList(0, Math.max(1, inclusiveIndex + 1)), startTime, wrapToNextDay);
    }

    /**
     * Computes up to k earliest-arrival routes using Yen's algorithm.
     * Results are ordered by total minutes ascending; duplicates are removed.
     * @param graph the transport graph
     * @param start starting station node
     * @param goals set of goal station nodes
     * @param startTime in local time the traveler starts at the start node
     * @param wrapToNextDay if true, missed departures wrap to the next day's service; if false, they are skipped
     * @param k number of routes to compute
     * @return list of up to <code>k</code> path results, ordered by the earliest arrival
     */
    @NotNull
    public static List<PathResult> shortestKTimes(Graph graph, Node start, Set<Node> goals,
                                                  LocalTime startTime, boolean wrapToNextDay, int k) {
        if (k <= 0) return Collections.emptyList();
        PathResult first = shortestTime(graph, start, goals, startTime, wrapToNextDay);
        if (first.path().isEmpty() || first.totalTime() == Integer.MAX_VALUE) return Collections.emptyList();

        List<PathResult> A = new ArrayList<>();
        A.add(first);
        PriorityQueue<PathResult> B = new PriorityQueue<>(byTimeThenPriceThenTransfers(graph));

        for (int kth = 1; kth < k; kth++) {
            PathResult prevBest = A.get(kth - 1);
            List<Node> prevPath = prevBest.path();
            for (int i = 0; i < prevPath.size() - 1; i++) {
                Node spurNode = prevPath.get(i);
                List<Node> rootPath = new ArrayList<>(prevPath.subList(0, i + 1));
                Set<EdgeRef> excludedEdges = new HashSet<>();
                for (PathResult a : A) {
                    List<Node> p = a.path();
                    if (p.size() > i && p.subList(0, i + 1).equals(rootPath)) {
                        EdgeRef ban = new EdgeRef(p.get(i), p.get(i + 1));
                        excludedEdges.add(ban);
                    }
                }
                Set<Node> excludedNodes = new HashSet<>(rootPath);
                excludedNodes.remove(spurNode);
                int minutesToSpur = simulateMinutesToIndex(graph, prevPath, i, startTime, wrapToNextDay);
                LocalTime spurStartTime = (startTime == null)
                        ? null
                        : startTime.plusMinutes(minutesToSpur);
                PathResult spurResult = shortestTime(graph, spurNode, goals, spurStartTime, wrapToNextDay, excludedEdges, excludedNodes);
                if (spurResult.path().isEmpty() || spurResult.totalTime() == Integer.MAX_VALUE) continue;
                List<Node> combined = new ArrayList<>(rootPath);
                List<Node> spurPath = spurResult.path();
                combined.addAll(spurPath.subList(1, spurPath.size()));
                int totalPrice = sumPrice(graph, combined);
                int totalTime = scheduleEndToEndMinutes(graph, combined);
                if (totalTime == Integer.MAX_VALUE) continue;
                PathResult candidate = new PathResult(combined, totalPrice, totalTime);
                boolean duplicate = A.stream().anyMatch(r -> r.path().equals(candidate.path())) ||
                                    B.stream().anyMatch(r -> r.path().equals(candidate.path()));
                if (!duplicate) B.add(candidate);
            }
            if (B.isEmpty()) break;
            A.add(B.poll());
        }
        return A;
    }
}
