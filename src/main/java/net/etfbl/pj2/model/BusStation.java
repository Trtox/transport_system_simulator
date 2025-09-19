/**
 * Represents a bus station in the transport system.
 */
package net.etfbl.pj2.model;

public class BusStation implements Station {

    /**
     * The name of this bus station.
     */
    private final String name;

    /**
     * Constructs a new BusStation with the given name.
     * @param name the name of the station
     */
    public BusStation(String name)
    {
        this.name = name;
    }

    /**
     * Returns the type of this station.
     * @return always returns "bus"
     */
    @Override
    public String getType()
    {
        return "bus";
    }

    /**
     * Returns the name of this stations
     * @return the station name
     */
    @Override
    public String getStationName()
    {
        return name;
    }

    /**
     * Indicates whether some other object is equal to this one.
     * Two BusStation objects are considered equal if their names are equal.
     * @param o the reference object with which to compare
     * @return true if this object is the same as the o argument; false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BusStation that)) return false;
        return name != null && name.equals(that.name);
    }

    /**
     * Returns a hash code value for the station based on its name.
     * @return the hash code value for this station
     */
    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

}
