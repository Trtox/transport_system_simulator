package net.etfbl.pj2.utility;

import net.etfbl.pj2.model.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Singleton class for parsing data from a JSON file and populating the relevant structures and relationships.
 */
public class DataParser {

    private static final DataParser INSTANCE = new DataParser();

    private final Map<String, City> cities;
    private final Map<String, BusStation> busStations;
    private final Map<String, TrainStation> trainStations;
    private final Map<Station, Vector<Departure>> departures;

    private final Graph graph;

    private DataParser() {
        cities = new HashMap<>();
        busStations = new HashMap<>();
        trainStations = new HashMap<>();
        departures = new HashMap<>();
        graph = new Graph();
    }

    /**
     * Singleton instance of the DataParser class.
     * @return The singleton instance of the DataParser class.
     */
    public static DataParser getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a map of cities indexed by their names.
     * @return A map of cities indexed by their names.
     */
    public Map<String, City> getCities() {
        return cities;
    }

    /**
     * Returns a map of bus stations indexed by their names.
     * @return A map of bus stations indexed by their names.
     */
    public Map<Station, Vector<Departure>> getDepartures() {
        return departures;
    }

    /**
     * Returns the graph object.
     * @return The graph object.
     */
    public Graph getGraph()  {  return graph;  }

    /**
     * Parses data from a JSON file to populate cities, stations, departures, and the graph structure.
     * Reads from a predefined JSON file ("transport_data.json") to extract information about stations,
     * cities, and departures, then processes this data to initialize relevant structures and relationships.
     *
     * @param n Number of graph rows.
     * @param m Number of graph columns.
     */
    public void parseDataFromJSON(int n, int m) {
        DataGenerator generator = new DataGenerator(n, m);
        generator.load_data();

        try {
            String jsonString = new String(Files.readAllBytes(Paths.get("transport_data.json")));
            JSONObject jsonObject = new JSONObject(jsonString);

            JSONArray stationArray = jsonObject.getJSONArray("stations");
            for (int i = 0; i < stationArray.length(); i++) {
                JSONObject obj = stationArray.getJSONObject(i);
                String cityName = obj.getString("city");
                String busStationName = obj.getString("busStation");
                String trainStationName = obj.getString("trainStation");

                BusStation busStation = new BusStation(busStationName);
                busStations.put(busStationName, busStation);

                TrainStation trainStation = new TrainStation(trainStationName);
                trainStations.put(trainStationName, trainStation);

                City city = new City(cityName, trainStation, busStation);
                cities.put(cityName, city);
            }

            for (City city : cities.values()) {
                Node busNode = graph.getOrCreateNode(city.busStation().getStationName(), StationType.BUS, city);
                Node trainNode = graph.getOrCreateNode(city.trainStation().getStationName(), StationType.TRAIN, city);

                int transferTime = 5;
                graph.addDeparture(busNode, trainNode, LocalTime.MIDNIGHT, transferTime, 0, 0);
                graph.addDeparture(trainNode, busNode, LocalTime.MIDNIGHT, transferTime, 0, 0);
            }

            JSONArray departuresArray = jsonObject.getJSONArray("departures");
            for (int i = 0; i < departuresArray.length(); i++) {
                JSONObject object = departuresArray.getJSONObject(i);
                String stationType = object.getString("type");
                String stationName = object.getString("from");
                String destinationCityName = object.getString("to");
                String depTime = object.getString("departureTime");
                LocalTime departureTime = LocalTime.parse(depTime, DateTimeFormatter.ofPattern("HH:mm"));
                int duration = object.getInt("duration");
                int price = object.getInt("price");
                int minTransferTime = object.getInt("minTransferTime");

                Departure departure = new Departure(
                        stationType,
                        stationType.equals("voz") ? trainStations.get(stationName) : busStations.get(stationName),
                        cities.get(destinationCityName),
                        departureTime,
                        duration,
                        price,
                        minTransferTime
                );

                Station station;
                if (stationType.equals("voz")) {
                    station = trainStations.get(stationName);
                } else {
                    station = busStations.get(stationName);
                }

                StringBuilder startCityName = new StringBuilder(stationName);
                startCityName.setCharAt(0, 'G');
                City fromCity = cities.get(startCityName.toString());
                StationType fromType = stationType.equals("voz") ? StationType.TRAIN : StationType.BUS;
                Node fromNode = graph.getOrCreateNode(stationName, fromType, fromCity);

                City toCity = cities.get(destinationCityName);
                String toStationName;
                StationType toType = stationType.equals("voz") ? StationType.TRAIN : StationType.BUS;
                if (toType == StationType.TRAIN) {
                    toStationName = cities.get(destinationCityName).trainStation().getStationName();
                } else {
                    toStationName = cities.get(destinationCityName).busStation().getStationName();
                }
                Node toNode = graph.getOrCreateNode(
                        toStationName,
                        toType,
                        toCity
                );

                graph.addDeparture(fromNode, toNode, departureTime, duration, price, minTransferTime);

                departures.putIfAbsent(station, new Vector<>());
                departures.get(station).add(departure);
            }

        } catch (IOException e) {
            throw new RuntimeException("Greška pri čitanju podataka: " + e.getMessage(), e);
        }

    }

}
