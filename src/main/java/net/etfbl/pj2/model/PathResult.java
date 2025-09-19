package net.etfbl.pj2.model;

import java.util.List;

/**
 * Record that represents the result of a pathfinding algorithm.
 * @param path
 * @param totalCost
 * @param totalTime
 */
public record PathResult(List<Node> path, int totalCost, int totalTime) {
}
