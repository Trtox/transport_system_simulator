package net.etfbl.pj2.model;

/**
 * Record that represents a city.
 * @param name
 * @param trainStation
 * @param busStation
 */
public record City(String name, TrainStation trainStation, BusStation busStation) {
}
