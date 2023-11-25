package com.ianrenton.planesailing.comms;

import com.ianrenton.planesailing.app.Application;
import com.ianrenton.planesailing.app.TrackTable;
import com.ianrenton.planesailing.data.Track;
import com.ianrenton.planesailing.data.TrackType;
import com.ianrenton.planesailing.utils.PrometheusMetricGenerator;
import com.sun.management.OperatingSystemMXBean;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The HTTP server that will provide data to the Plane/Sailing client over the
 * web.
 */
public class WebServer {
    private static final Application APP = Application.getInstance();
    private static final Logger LOGGER = LogManager.getLogger(WebServer.class);
    private static final OperatingSystemMXBean OS_BEAN = ManagementFactory
            .getPlatformMXBean(OperatingSystemMXBean.class);
    // Work-around to specify total memory size of the PC manually (here, 2GB to
    // match my
    // Raspberry Pi) for JDKs where querying it doesn't work properly
    private static final long TOTAL_MEM_BYTES = (OS_BEAN.getTotalMemorySize() != 0)
            ? OS_BEAN.getTotalMemorySize() : 2000000000;
    // Expected milliseconds between receiving requests when a client is online
    private static final long CLIENT_REQUEST_RATE_MILLIS = 10000;

    private final HttpServer server;
    private final int localPort;
    private final boolean readableJSON = Application.CONFIG.getBoolean("comms.web-server.readable-json");
    private boolean online;
    private long lastReceivedTime;
    private final String homeCallResponseHTML;
    private int requestsServed = 0;

    /**
     * Create the web server
     *
     * @param localPort Port to listen on.
     * @exception IOException if the server could not be set up.
     */
    public WebServer(int localPort) throws IOException {
        this.localPort = localPort;

        homeCallResponseHTML = "<html><head><title>Plane/Sailing Server</title></head><body>"
                + "<h1>Plane/Sailing Server</h1>" + "<p>Plane/Sailing Server version "
                + Application.getSoftwareVersion() + " is up and running!</p>"
                + "<p>Available API endpoints:</p>"
                + "<ul><li><a href='/first'>/first</a> - First call (includes position history)</li>"
                + "<li><a href='/update'>/update</a> - Update call (no position history)</li>"
                + "<li><a href='/telemetry'>/telemetry</a> - Server telemetry information</li>"
                + "<li><a href='/metrics'>/metrics</a> - Performance data formatted for use with Prometheus (e.g. for Grafana)</li>"
                + "</ul></body></html>";

        server = HttpServer.create(new InetSocketAddress(localPort), 10);
        server.createContext("/first", new CallHandler(Call.FIRST));
        server.createContext("/update", new CallHandler(Call.UPDATE));
        server.createContext("/telemetry", new CallHandler(Call.TELEMETRY));
        server.createContext("/metrics", new CallHandler(Call.METRICS));
        server.createContext("/", new CallHandler(Call.HOME));
        server.setExecutor(null);
    }

    public void run() {
        server.start();
        online = true;
        LOGGER.info("Started web server on port {}.", localPort);
    }

    public void stop() {
        server.stop(0);
        online = false;
    }

    private class CallHandler implements HttpHandler {
        private final Call call;

        public CallHandler(Call call) {
            this.call = call;
        }

        @Override
        public void handle(HttpExchange t) {
            lastReceivedTime = System.currentTimeMillis();
            String response = "";
            String contentType = "application/json";

            try (t) {
                switch (call) {
                    case FIRST -> response = getFirstCallJSON();
                    case UPDATE -> response = getUpdateCallJSON();
                    case TELEMETRY -> response = getTelemetryCallJSON();
                    case METRICS -> {
                        response = getMetricsForPrometheus();
                        contentType = "text/plain";
                    }
                    case HOME -> {
                        response = homeCallResponseHTML;
                        contentType = "text/html";
                    }
                }

                final Headers headers = t.getResponseHeaders();
                final String requestMethod = t.getRequestMethod().toUpperCase();
                headers.add("Access-Control-Allow-Origin", "*");
                switch (requestMethod) {
                    case "GET" -> {
                        headers.set("Content-Type", String.format(contentType + "; charset=%s", "UTF8"));
                        final byte[] rawResponseBody = response.getBytes(StandardCharsets.UTF_8);
                        t.sendResponseHeaders(200, rawResponseBody.length);
                        t.getResponseBody().write(rawResponseBody);
                    }
                    case "OPTIONS" -> {
                        headers.set("Allow", "GET, OPTIONS");
                        headers.add("Access-Control-Allow-Methods", "GET, OPTIONS");
                        headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                        t.sendResponseHeaders(200, -1);
                    }
                    default -> {
                        headers.set("Allow", "GET, OPTIONS");
                        t.sendResponseHeaders(405, -1);
                    }
                }
                requestsServed++;
            } catch (Exception ex) {
                LOGGER.error("Exception responding to web request", ex);
            }
        }
    }

    /**
     * Returns JSON corresponding to the "first" API call of the server, which
     * includes all tracks (including base station, airports and seaports), and the
     * complete position history for all tracks that have it, so that the client can
     * populate both the full current picture and the snail trail for tracks. It
     * also includes the server's current time, so that clients can determine the
     * age of tracks correctly, and the server version number.
     */
    public String getFirstCallJSON() {
        Map<String, Object> map = new HashMap<>();
        map.put("time", System.currentTimeMillis());
        map.put("version", Application.getSoftwareVersion());

        Map<String, Map<String, Object>> tracks = new HashMap<>();
        for (Track t : APP.getTrackTable().values()) {
            tracks.put(t.getID(), t.getFirstCallData());
        }
        map.put("tracks", tracks);

        JSONObject o = new JSONObject(map);
        return o.toString(readableJSON ? 2 : 0);
    }

    /**
     * Returns JSON corresponding to the "update" API call of the server, which is
     * designed to update a picture previously populated by the "first" call. To
     * save bandwidth, no position history is sent - the client is expected to
     * append the reported position to its own position history store. This call
     * also omits the base station, airports and seaports that can't change. It also
     * includes the server's current time, so that clients can determine the age of
     * tracks correctly.
     */
    public String getUpdateCallJSON() {
        Map<String, Object> map = new HashMap<>();
        map.put("time", System.currentTimeMillis());

        Map<String, Map<String, Object>> tracks = new HashMap<>();
        for (Track t : APP.getTrackTable().values()) {
            tracks.put(t.getID(), t.getUpdateCallData());
        }
        map.put("tracks", tracks);

        JSONObject o = new JSONObject(map);
        return o.toString(readableJSON ? 2 : 0);
    }

    /**
     * Get a map of some useful server telemetry
     */
    private String getTelemetryCallJSON() {
        Map<String, String> map = new HashMap<>();
        map.put("cpuLoad", String.format("%.0f", OS_BEAN.getCpuLoad() * 100.0));
        map.put("memUsed",
                String.format("%.0f", ((OS_BEAN.getCommittedVirtualMemorySize() / (double) TOTAL_MEM_BYTES)) * 100.0));
        map.put("diskUsed", String.format("%.0f",
                (1.0 - (new File(".").getFreeSpace() / (double) new File(".").getTotalSpace())) * 100.0));
        map.put("uptime", String.format("%d", System.currentTimeMillis() - Application.START_TIME));
        Double temp = getTemp();
        if (temp != null) {
            map.put("temp", String.format("%.1f", temp));
        }
        map.put("webServerStatus", APP.getWebServerStatus().toString());
        map.put("adsbReceiverStatus", APP.getADSBReceiverStatus().toString());
        map.put("mlatReceiverStatus", APP.getMLATReceiverStatus().toString());
        map.put("aisReceiverStatus", APP.getAISReceiverStatus().toString());
        map.put("aprsReceiverStatus", APP.getAPRSReceiverStatus().toString());

        JSONObject o = new JSONObject(map);
        return o.toString(readableJSON ? 2 : 0);
    }

    /**
     * Get some server statistics formatted for use with Prometheus.
     */
    private String getMetricsForPrometheus() {
        TrackTable tt = APP.getTrackTable();
        return PrometheusMetricGenerator.generate("plane_sailing_uptime", "Uptime of the server in seconds",
                "counter", (System.currentTimeMillis() - Application.START_TIME) / 1000.0)
                + PrometheusMetricGenerator.generate("plane_sailing_requests_served", "Number of HTTP requests served by the Plane/Sailing server since start",
                "counter", requestsServed)
                + PrometheusMetricGenerator.generate("plane_sailing_adsb_available", "Is ADSB input configured and connected?",
                "gauge", APP.getADSBReceiverStatus() == ConnectionStatus.ONLINE || APP.getADSBReceiverStatus() == ConnectionStatus.ACTIVE ? 1 : 0)
                + PrometheusMetricGenerator.generate("plane_sailing_mlat_available", "Is MLAT input configured and connected?",
                "gauge", APP.getMLATReceiverStatus() == ConnectionStatus.ONLINE || APP.getMLATReceiverStatus() == ConnectionStatus.ACTIVE ? 1 : 0)
                + PrometheusMetricGenerator.generate("plane_sailing_ais_available", "Is AIS input configured and connected?",
                "gauge", APP.getAISReceiverStatus() == ConnectionStatus.ONLINE || APP.getAISReceiverStatus() == ConnectionStatus.ACTIVE ? 1 : 0)
                + PrometheusMetricGenerator.generate("plane_sailing_aprs_available", "Is APRS input configured and connected?",
                "gauge", APP.getAPRSReceiverStatus() == ConnectionStatus.ONLINE || APP.getAPRSReceiverStatus() == ConnectionStatus.ACTIVE ? 1 : 0)
                + PrometheusMetricGenerator.generate("plane_sailing_adsb_receiving", "Is ADSB input receiving data?",
                "gauge", APP.getADSBReceiverStatus() == ConnectionStatus.ACTIVE ? 1 : 0)
                + PrometheusMetricGenerator.generate("plane_sailing_mlat_receiving", "Is MLAT input receiving data?",
                "gauge", APP.getADSBReceiverStatus() == ConnectionStatus.ACTIVE ? 1 : 0)
                + PrometheusMetricGenerator.generate("plane_sailing_ais_receiving", "Is AIS input receiving data?",
                "gauge", APP.getADSBReceiverStatus() == ConnectionStatus.ACTIVE ? 1 : 0)
                + PrometheusMetricGenerator.generate("plane_sailing_aprs_receiving", "Is APRS input receiving data?",
                "gauge", APP.getADSBReceiverStatus() == ConnectionStatus.ACTIVE ? 1 : 0)
                + PrometheusMetricGenerator.generate("plane_sailing_track_count", "Number of tracks of all kinds in the system",
                "gauge", tt.size())
                + PrometheusMetricGenerator.generate("plane_sailing_aircraft_count", "Number of aircraft tracks in the system",
                "gauge", tt.values().stream().filter(t -> t.getTrackType() == TrackType.AIRCRAFT).count())
                + PrometheusMetricGenerator.generate("plane_sailing_ship_count", "Number of ship tracks in the system",
                "gauge", tt.values().stream().filter(t -> t.getTrackType() == TrackType.SHIP).count())
                + PrometheusMetricGenerator.generate("plane_sailing_ais_shore_station_count", "Number of AIS shore station tracks in the system",
                "gauge", tt.values().stream().filter(t -> t.getTrackType() == TrackType.AIS_SHORE_STATION).count())
                + PrometheusMetricGenerator.generate("plane_sailing_aton_count", "Number of AtoN tracks in the system",
                "gauge", tt.values().stream().filter(t -> t.getTrackType() == TrackType.AIS_ATON).count())
                + PrometheusMetricGenerator.generate("plane_sailing_aprs_mobile_count", "Number of mobile APRS tracks in the system",
                "gauge", tt.values().stream().filter(t -> t.getTrackType() == TrackType.APRS_MOBILE).count())
                + PrometheusMetricGenerator.generate("plane_sailing_aprs_base_count", "Number of APRS base station tracks in the system",
                "gauge", tt.values().stream().filter(t -> t.getTrackType() == TrackType.APRS_BASE_STATION).count())
                + PrometheusMetricGenerator.generate("plane_sailing_aircraft_furthest_distance", "Distance in nautical miles from the base station to the furthest tracked aircraft",
                "gauge", tt.values().stream().filter(t -> t.getTrackType() == TrackType.AIRCRAFT).mapToDouble(tt::getDistanceFromBaseStationOrZero).map(d -> d * 0.000539957).max().orElse(0.0))
                + PrometheusMetricGenerator.generate("plane_sailing_ship_furthest_distance", "Distance in nautical miles from the base station to the furthest tracked ship",
                "gauge", tt.values().stream().filter(t -> t.getTrackType() == TrackType.SHIP).mapToDouble(tt::getDistanceFromBaseStationOrZero).map(d -> d * TrackTable.METRES_TO_NMI).max().orElse(0.0))
                + PrometheusMetricGenerator.generate("plane_sailing_ais_furthest_distance", "Distance in nautical miles from the base station to the furthest tracked AIS contact",
                "gauge", tt.values().stream().filter(t -> t.getTrackType() == TrackType.SHIP || t.getTrackType() == TrackType.AIS_SHORE_STATION || t.getTrackType() == TrackType.AIS_ATON).mapToDouble(tt::getDistanceFromBaseStationOrZero).map(d -> d * 0.000539957).max().orElse(0.0))
                + PrometheusMetricGenerator.generate("plane_sailing_aprs_furthest_distance", "Distance in nautical miles from the base station to the furthest tracked APRS contact",
                "gauge", tt.values().stream().filter(t -> t.getTrackType() == TrackType.APRS_MOBILE || t.getTrackType() == TrackType.APRS_BASE_STATION).mapToDouble(tt::getDistanceFromBaseStationOrZero).map(d -> d * 0.000539957).max().orElse(0.0));
    }

    private enum Call {
        FIRST, UPDATE, TELEMETRY, METRICS, HOME
    }

    public ConnectionStatus getStatus() {
        if (online) {
            if (System.currentTimeMillis() - lastReceivedTime <= CLIENT_REQUEST_RATE_MILLIS * 2) {
                return ConnectionStatus.ACTIVE;
            } else {
                return ConnectionStatus.ONLINE;
            }
        } else {
            return ConnectionStatus.OFFLINE;
        }
    }

    private Double getTemp() {
        Process proc;
        try {
            proc = Runtime.getRuntime().exec("cat /sys/class/thermal/thermal_zone0/temp");
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String s = stdInput.readLine();
            if (s != null) {
                // Value returned is in "millidegrees", we want degrees
                return Double.parseDouble(s) / 1000.0;
            } else {
                // Could not read temperature, maybe this value isn't available?
                return null;
            }
        } catch (Exception e) {
            // Could not read temperature, maybe this isn't running on Linux?
            return null;
        }


    }
}
