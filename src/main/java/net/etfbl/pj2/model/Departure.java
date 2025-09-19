package net.etfbl.pj2.model;

import java.time.LocalTime;

/**
 * Record that represents a departure.
 * @param type
 * @param start
 * @param destination
 * @param departureTime
 * @param duration
 * @param price
 * @param minTransferTime
 */
public record Departure(
        String type,
        Station start,
        City destination,
        LocalTime departureTime,
        int duration,
        int price,
        int minTransferTime
) {}
