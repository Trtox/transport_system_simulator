package net.etfbl.pj2.model;

/**
 * Represents a train station in the transport system.
 */
public class TrainStation implements Station {

    /**
     * The name of this train station.
     */
    private final String name;

    /**
     * Constructs a new TrainStation with the given name.
     * @param name the name of the station
     */
    public TrainStation(String name)
    {
        this.name = name;
    }

    /**
     * Returns the type of this station.
     * @return always returns "train"
     */
    @Override
    public String getType()
    {
        return "train";
    }

    /**
     * Returns the name of this station.
     * @return the station name
     */
    @Override
    public String getStationName()
    {
        return name;
    }

    /**
     * Indicates whether some other object is equal to this one.
     * Two TrainStation objects are considered equal if their names are equal.
     * @param o the reference object with which to compare
     * @return true if this object is the same as the o argument; false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrainStation that)) return false;
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
