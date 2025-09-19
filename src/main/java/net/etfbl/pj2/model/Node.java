package net.etfbl.pj2.model;

import org.jetbrains.annotations.NotNull;

/**
 * Record that represents a node in the graph.
 * Used separately from a Node type in GraphStream.
 * @param id
 * @param type
 * @param city
 */
public record Node(
        String id,
        StationType type,
        City city
) {
    @NotNull
    @Override
    public String toString() {
        return id;
    }
}