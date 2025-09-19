package net.etfbl.pj2.model;

import java.time.LocalTime;

/**
 * Record that represents an edge in the graph.
 * Used separately from an Edge type in GraphStream.
 * @param to
 * @param departureTime
 * @param durationMin
 * @param price
 * @param minTransferTime
 */
public record Edge(
        Node to,
        LocalTime departureTime,
        int durationMin,
        int price,
        int minTransferTime
) {}