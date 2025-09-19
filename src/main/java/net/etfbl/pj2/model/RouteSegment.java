package net.etfbl.pj2.model;

/**
 * Record that represents a route segment used for displaying the route on the GUI.
 * @param start
 * @param end
 * @param type
 * @param price
 * @param departure
 * @param arrival
 */
public record RouteSegment(String start, String end, String type, int price, String departure, String arrival) {
}
