package net.etfbl.pj2.algorithms;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import net.etfbl.pj2.model.Edge;
import net.etfbl.pj2.model.Graph;
import net.etfbl.pj2.model.Node;
import net.etfbl.pj2.model.PathResult;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dijkstra variant that minimizes the total price while respecting timetables and transfers.
 * The algorithm exposes both single-route and k-best variants (using Yen's style spur paths).
 * Reported totalTime and totalCost in PathResult are computed with the same
 * schedule-based logic used by the UI, so UI sorting and algorithmic results are consistent.
 */
public class LowestPriceDijkstra {

    /** Priority-queue entry ordered by (price, arrivalMinutesSinceStart). */
    private record PQEntry(Node node, int price, int arrivalMinSinceStart) {}

    /** Directed edge descriptor used to exclude edges in Yen's algorithm. */
    private record EdgeRef(Node from, Node to) {}

    /**
     * Comparator that orders routes by price, then by schedule time, then by number of transfers.
     * Used to rank k-best candidates at the same monetary cost.
     * @param graph the graph used to count transfers
     * @return comparator applying price → time → transfers ordering
     */
    @NotNull
    private static Comparator<PathResult> byPriceThenTimeThenTransfers(Graph graph) {
        return Comparator.comparingInt(PathResult::totalCost)
                .thenComparingInt(PathResult::totalTime)
                .thenComparingInt(pr -> countTransfersOnPath(graph, pr.path()));
    }

    /**
     * Computes up to k cheapest routes using a Yen-style k-best approach.
     * Candidates are ranked by price, then schedule time, then transfers.
     *
     * @param graph the transport graph
     * @param start the start node
     * @param goals destination set (any is acceptable)
     * @param startTime departure readiness time (local time of day); may be null
     * @param wrapToNextDay if true, allows wrapping to the next day when a departure is missed
     * @param k maximum number of routes to return
     * @return list of up to k PathResult's ordered by efficiency
     */
    @NotNull
    public static List<PathResult> lowestKPrices(Graph graph, Node start, Set<Node> goals,
                                                 LocalTime startTime, boolean wrapToNextDay, int k) {
        if (k <= 0) return Collections.emptyList();
        PathResult first = getRoute(graph, start, goals, startTime, wrapToNextDay);
        if (first.path().isEmpty() || first.totalCost() == Integer.MAX_VALUE) return Collections.emptyList();

        List<PathResult> A = new ArrayList<>();
        A.add(first);
        PriorityQueue<PathResult> B = new PriorityQueue<>(byPriceThenTimeThenTransfers(graph));

        for (int kth = 1; kth < k; kth++) {
            PathResult prevBest = A.get(kth - 1);
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

                int minutesToSpur = simulateMinutesToIndex(graph, prevPath, i, startTime, wrapToNextDay);
                LocalTime spurStartTime = (startTime == null) ? null : startTime.plusMinutes(minutesToSpur);

                PathResult spur = getRoute(graph, spurNode, goals, spurStartTime, wrapToNextDay, excludedEdges, excludedNodes);
                if (spur.path().isEmpty() || spur.totalCost() == Integer.MAX_VALUE) continue;

                List<Node> combined = new ArrayList<>(rootPath);
                List<Node> spurPath = spur.path();
                combined.addAll(spurPath.subList(1, spurPath.size()));

                int totalPrice = sumPrice(graph, combined);
                int totalTime = scheduleEndToEndMinutes(graph, combined);
                if (totalTime == Integer.MAX_VALUE) continue;

                PathResult cand = new PathResult(combined, totalPrice, totalTime);

                boolean duplicate = A.stream().anyMatch(r -> r.path().equals(cand.path())) ||
                                    B.stream().anyMatch(r -> r.path().equals(cand.path()));
                if (!duplicate) B.add(cand);
            }

            if (B.isEmpty()) break;
            A.add(B.poll());
        }

        return A;
    }

    /**
     * Wrapper for {@link #lowestKPrices(Graph, Node, Set, LocalTime, boolean, int)} starting now.
     * @param graph the transport graph
     * @param start the start node
     * @param goals destination set
     * @param k maximum number of routes to return
     * @return list of routes ordered by price, then time, then transfers
     */
    @NotNull
    public static List<PathResult> lowestKPricesNow(Graph graph, Node start, Set<Node> goals, int k) {
        LocalTime nowSarajevo = ZonedDateTime.now(ZoneId.of("Europe/Sarajevo")).toLocalTime();
        return lowestKPrices(graph, start, goals, nowSarajevo, true, k);
    }

    /**
     * Computes the single cheapest oute from start to any goals starting at startTime.
     * @param graph the transport graph
     * @param start the start node
     * @param goals destination set
     * @param startTime readiness time-of-day; may be {@code null}
     * @param wrapToNextDay whether the search may wrap departures to the next day
     * @return the cheapest route found, or an empty result if none exists
     */
    @NotNull
    @Contract("_, _, _, _, _ -> new")
    public static PathResult getRoute(Graph graph, Node start, Set<Node> goals, LocalTime startTime, boolean wrapToNextDay) {
        return getRoute(graph, start, goals, startTime, wrapToNextDay,
                Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Core price-minimizing Dijkstra with optional excluded edges/nodes (used by Yen's algorithm for k-best).
     * The returned PathResult carries total cost and schedule duration consistent with the UI.
     * @param graph the graph
     * @param start start node
     * @param goals acceptable destination set
     * @param startTime readiness time-of-day; may be {@code null}
     * @param wrapToNextDay if true, allow wrapping to the next day to catch departures
     * @param excludedEdges edges disallowed in the search
     * @param excludedNodes nodes disallowed (except the spur itself when applicable)
     * @return a new PathResult describing the chosen route
     */
    @NotNull
    @Contract("_, _, _, _, _, _, _ -> new")
    private static PathResult getRoute(Graph graph, Node start, Set<Node> goals,
                                       LocalTime startTime, boolean wrapToNextDay,
                                       Set<EdgeRef> excludedEdges, Set<Node> excludedNodes) {
        final int DAY_MIN = 24 * 60;
        final int startOffset = (startTime == null ? 0 : startTime.getHour() * 60 + startTime.getMinute());

        Map<Node, Integer> bestPrice = new HashMap<>();
        Map<Node, Integer> bestArrival = new HashMap<>();
        Map<Node, Node> parent = new HashMap<>();

        PriorityQueue<PQEntry> pq = new PriorityQueue<>(Comparator
                .comparingInt(PQEntry::price)
                .thenComparingInt(PQEntry::arrivalMinSinceStart));

        bestPrice.put(start, 0);
        bestArrival.put(start, 0);
        pq.add(new PQEntry(start, 0, 0));

        Node reachedGoal = null;
        int reachedPrice = Integer.MAX_VALUE;

        while (!pq.isEmpty()) {
            PQEntry cur = pq.poll();
            Node u = cur.node();
            int priceSoFar = cur.price();
            int arriveSoFar = cur.arrivalMinSinceStart();

            Integer bp = bestPrice.get(u);
            Integer ba = bestArrival.get(u);
            if (bp == null || ba == null) continue;
            if (priceSoFar != bp || arriveSoFar != ba) continue; // stale

            if (goals.contains(u)) { reachedGoal = u; reachedPrice = priceSoFar;
                break; }

            if (excludedNodes.contains(u) && !u.equals(start)) continue;

            for (Edge e : graph.getNeighbors(u)) {
                if (e == null || e.to() == null) continue;
                if (excludedEdges.contains(new EdgeRef(u, e.to()))) continue;
                if (excludedNodes.contains(e.to())) continue;

                boolean isTransferConnector = (e.price() == 0
                        && e.departureTime() != null
                        && e.departureTime().equals(LocalTime.MIDNIGHT));

                int waitMinutes;
                int travelMinutes;

                if (isTransferConnector) {
                    waitMinutes = 0;
                    travelMinutes = Math.max(1, e.minTransferTime());
                } else {
                    LocalTime dep = e.departureTime();
                    if (dep == null) continue;
                    int depMin = dep.getHour() * 60 + dep.getMinute();

                    int readyAbs = startOffset + arriveSoFar + Math.max(0, e.minTransferTime());
                    int readyOfDay = ((readyAbs % DAY_MIN) + DAY_MIN) % DAY_MIN;

                    int delta = depMin - readyOfDay;
                    if (delta < 0) {
                        if (!wrapToNextDay) continue;
                        delta += DAY_MIN;
                    }
                    waitMinutes = delta;
                    travelMinutes = Math.max(0, e.durationMin());
                }

                int newArrival = arriveSoFar + waitMinutes + travelMinutes;
                int newPrice = priceSoFar + Math.max(0, e.price());

                boolean improve;
                Integer curBestPrice = bestPrice.get(e.to());
                Integer curBestArrival = bestArrival.get(e.to());
                if (curBestPrice == null) {
                    improve = true;
                } else if (newPrice < curBestPrice) {
                    improve = true;
                } else if (newPrice == curBestPrice && curBestArrival != null && newArrival < curBestArrival) {
                    improve = true;
                } else {
                    improve = false;
                }

                if (improve) {
                    bestPrice.put(e.to(), newPrice);
                    bestArrival.put(e.to(), newArrival);
                    parent.put(e.to(), u);
                    pq.add(new PQEntry(e.to(), newPrice, newArrival));
                }
            }
        }

        if (reachedGoal == null) return new PathResult(Collections.emptyList(), Integer.MAX_VALUE, Integer.MAX_VALUE);
        List<Node> path = reconstructPath(parent, start, reachedGoal);
        int scheduleMinutes = scheduleEndToEndMinutes(graph, path);
        return new PathResult(path, reachedPrice, scheduleMinutes);
    }

    /**
     * Reconstructs a path from start to goal using the parent map.
     * @param parent mapping of node → predecessor
     * @param start start node
     * @param goal reached goal
     * @return the path from start to goal (inclusive)
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
     * Returns the directed edge from u to v, or null if none exists.
     * @param graph the graph holding the edges
     * @param u source node
     * @param v destination node
     * @return the edge if present; otherwise null
     */
    @Nullable
    private static Edge findEdge(@NotNull Graph graph, Node u, Node v) {
        for (Edge e : graph.getNeighbors(u)) if (e.to().equals(v)) return e;
        return null;
    }

    /**
     * Determines whether the edge is an intra-city transfer connector (zero price, midnight departure).
     * @param e the edge to check
     * @return true if the edge represents a transfer connector; false otherwise
     */
    private static boolean isTransferConnector(Edge e) {
        return e != null && e.price() == 0 && e.departureTime() != null && e.departureTime().equals(LocalTime.MIDNIGHT);
    }

    /**
     * Counts the number of transfers along a concrete node path.
     * Transfers are edges recognized by isTransferConnector(Edge).
     * @param graph the graph
     * @param path the path of nodes
     * @return number of transfer edges encountered (0 if a path is empty)
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
     * Sums monetary price along a concrete node-path.
     * @param graph the graph
     * @param path the path of nodes
     * @return total price, or Integer.MAX_VALUE if any edge is missing
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
     * Computes schedule duration from the first real departure (or initial transfer) to the final arrival.
     * @param graph the graph
     * @param path node sequence
     * @return total minutes, or Integer.MAX_VALUE if the path is invalid
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
     * Simulates travel minutes along a path using readiness at startTime and wrap rules.
     * Used internally to compute spur readiness times during k-best enumeration.
     * @param graph the graph
     * @param path node sequence
     * @param startTime readiness time-of-day; may be null
     * @param wrapToNextDay if true, allow wrapping to next day to catch departures
     * @return simulated minutes, or Integer.MAX_VALUE if invalid
     */
    private static int simulateTravelMinutes(Graph graph, List<Node> path, LocalTime startTime, boolean wrapToNextDay) {
        if (path == null || path.size() < 2) return 0;
        final int DAY_MIN = 24 * 60;
        int now = (startTime == null ? 0 : startTime.getHour() * 60 + startTime.getMinute());
        int t = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Node u = path.get(i), v = path.get(i + 1);
            Edge e = findEdge(graph, u, v);
            if (e == null) return Integer.MAX_VALUE;
            boolean isTransferConnector = (e.price() == 0
                    && e.departureTime() != null
                    && e.departureTime().equals(LocalTime.MIDNIGHT));
            if (isTransferConnector) {
                t += Math.max(1, e.minTransferTime());
            } else {
                LocalTime dep = e.departureTime();
                if (dep == null) return Integer.MAX_VALUE;
                int depMin = dep.getHour() * 60 + dep.getMinute();
                int readyAbs = now + t + Math.max(0, e.minTransferTime());
                int readyOfDay = ((readyAbs % DAY_MIN) + DAY_MIN) % DAY_MIN;
                int wait = depMin - readyOfDay;
                if (wait < 0) {
                    if (!wrapToNextDay) return Integer.MAX_VALUE;
                    wait += DAY_MIN;
                }
                t += wait + Math.max(0, e.durationMin());
            }
        }
        return t;
    }

    /**
     * Helper that simulates minutes up to and including inclusiveIndex in the path.
     * @param graph the graph
     * @param path node sequence
     * @param inclusiveIndex last index to simulate (inclusive)
     * @param startTime readiness time-of-day; may be null
     * @param wrapToNextDay if true, allow wrapping to the next day to catch departures
     * @return simulated minutes to the given index
     */
    private static int simulateMinutesToIndex(Graph graph, @NotNull List<Node> path, int inclusiveIndex,
                                              LocalTime startTime, boolean wrapToNextDay) {
        return simulateTravelMinutes(graph, path.subList(0, Math.max(1, inclusiveIndex + 1)), startTime, wrapToNextDay);
    }
}
