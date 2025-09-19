package net.etfbl.pj2.utility;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class DataGenerator {
    private final int rows, cols;
    private static final int DEPARTURES_PER_STATION = 5;
    private static final Random random = new Random();

    public DataGenerator(int rows, int cols)
    {
        this.rows = rows;
        this.cols = cols;
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int x,y;
        x = in.nextInt();
        y = in.nextInt();
        DataGenerator generator = new DataGenerator(x, y);
        TransportData data = generator.generateData();
        generator.saveToJson(data, "transport_data.json");
        System.out.println("Podaci su generisani i sacuvani kao transport_data.json");
    }

    public void load_data()
    {
        DataGenerator generator = new DataGenerator(rows, cols);
        TransportData data = generator.generateData();
        generator.saveToJson(data, "transport_data.json");
    }

    // struktura podataka koja sadrzi sve trazene ulazne podatke
    public static class TransportData {
        public String[][] countryMap;
        public List<Station> stations;
        public List<Departure> departures;

    }

    public static class Station {
        public String city;
        public String busStation;
        public String trainStation;
    }

    public static class Departure {
        public String type; // "autobus" ili "voz"
        public String from;
        public String to;
        public String departureTime;
        public int duration; // u minutama
        public int price;
        public int minTransferTime; // vrijeme potrebno za transfer (u minutama)
    }

    public TransportData generateData() {
        TransportData data = new TransportData();
        data.countryMap = generateCountryMap();
        data.stations = generateStations();
        data.departures = generateDepartures(data.stations);
        return data;
    }

    // generisanje gradova (G_X_Y)
    private String[][] generateCountryMap() {
        String[][] countryMap = new String[rows][cols];
        for (int x = 0; x < rows; x++) {
            for (int y = 0; y < cols; y++) {
                countryMap[x][y] = "G_" + x + "_" + y;
            }
        }
        return countryMap;
    }

    // generisanje autobuskih i zeljeznickih stanica
    private List<Station> generateStations() {
        List<Station> stations = new ArrayList<>();
        for (int x = 0; x < rows; x++) {
            for (int y = 0; y < cols; y++) {
                Station station = new Station();
                station.city = "G_" + x + "_" + y;
                station.busStation = "A_" + x + "_" + y;
                station.trainStation = "Z_" + x + "_" + y;
                stations.add(station);
            }
        }
        return stations;
    }

    // generisanje vremena polazaka
    private List<Departure> generateDepartures(List<Station> stations) {
        List<Departure> departures = new ArrayList<>();

        for (Station station : stations) {
            int x = Integer.parseInt(station.city.split("_")[1]);
            int y = Integer.parseInt(station.city.split("_")[2]);

            // generisanje polazaka autobusa
            for (int i = 0; i < DEPARTURES_PER_STATION; i++) {
                departures.add(generateDeparture("autobus", station.busStation, x, y));
            }

            // generisanje polazaka vozova
            for (int i = 0; i < DEPARTURES_PER_STATION; i++) {
                departures.add(generateDeparture("voz", station.trainStation, x, y));
            }
        }
        return departures;
    }

    private Departure generateDeparture(String type, String from, int x, int y) {
        Departure departure = new Departure();
        departure.type = type;
        departure.from = from;

        // generisanje susjeda
        List<String> neighbors = getNeighbors(x, y);
        departure.to = neighbors.isEmpty() ? from : neighbors.get(random.nextInt(neighbors.size()));

        // generisanje vremena
        int hour = random.nextInt(24);
        int minute = random.nextInt(4) * 15; // 0, 15, 30, 45
        departure.departureTime = String.format("%02d:%02d", hour, minute);

        // geneirsanje cijene
        departure.duration = 30 + random.nextInt(151);
        departure.price = 100 + random.nextInt(901);

        // generisanje vremena transfera
        departure.minTransferTime = 5 + random.nextInt(26);

        return departure;
    }

    // pronalazak susjednih gradova
    private List<String> getNeighbors(int x, int y) {
        List<String> neighbors = new ArrayList<>();
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; 

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (nx >= 0 && nx < rows && ny >= 0 && ny < cols) {
                neighbors.add("G_" + nx + "_" + ny);
            }
        }
        return neighbors;
    }

    // cuvanje podataka u JSON mapu
    private void saveToJson(TransportData data, String filename) {
        try (FileWriter file = new FileWriter(filename)) {
            StringBuilder json = new StringBuilder();
            json.append("{\n");

            // mapa drzave
            json.append("  \"countryMap\": [\n");
            for (int i = 0; i < rows; i++) {
                json.append("    [");
                for (int j = 0; j < cols; j++) {
                    json.append("\"").append(data.countryMap[i][j]).append("\"");
                    if (j < cols - 1) json.append(", ");
                }
                json.append("]");
                if (i < rows - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");

            // stanice
            json.append("  \"stations\": [\n");
            for (int i = 0; i < data.stations.size(); i++) {
                Station s = data.stations.get(i);
                json.append("    {\"city\": \"").append(s.city)
                    .append("\", \"busStation\": \"").append(s.busStation)
                    .append("\", \"trainStation\": \"").append(s.trainStation)
                    .append("\"}");
                if (i < data.stations.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");

            // vremena polazaka
            json.append("  \"departures\": [\n");
            for (int i = 0; i < data.departures.size(); i++) {
                Departure d = data.departures.get(i);
                json.append("    {\"type\": \"").append(d.type)
                    .append("\", \"from\": \"").append(d.from)
                    .append("\", \"to\": \"").append(d.to)
                    .append("\", \"departureTime\": \"").append(d.departureTime)
                    .append("\", \"duration\": ").append(d.duration)
                    .append(", \"price\": ").append(d.price)
                    .append(", \"minTransferTime\": ").append(d.minTransferTime)
                    .append("}");
                if (i < data.departures.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ]\n");

            json.append("}");
            file.write(json.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}