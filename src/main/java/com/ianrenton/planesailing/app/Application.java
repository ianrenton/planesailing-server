package com.ianrenton.planesailing.app;

import com.ianrenton.planesailing.comms.*;
import com.ianrenton.planesailing.utils.DataMaps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Server for Plane/Sailing.
 */
public class Application {
    public static final Config CONFIG = ConfigFactory.load().getConfig("plane-sailing-server");
    public static final long START_TIME = System.currentTimeMillis();

    private static Application instance;
    private static final Logger LOGGER = LogManager.getLogger(Application.class);
    private static String softwareVersion = "Unknown";

    private final TrackTable trackTable = new TrackTable();

    private WebServer webServer;
    private final List<Client> aisReceivers = new ArrayList<>();
    private final List<Client> adsbReceivers = new ArrayList<>();
    private final List<Client> mlatReceivers = new ArrayList<>();
    private final List<Client> aprsReceivers = new ArrayList<>();

    /**
     * Start the application
     *
     * @param args None used
     */
    public static void main(String[] args) {
        instance = new Application();
        instance.setup();
        instance.run();
    }

    /**
     * Get the application instance singleton
     */
    public static Application getInstance() {
        return instance;
    }

    public void setup() {
        // Fetch software version
        Properties p = new Properties();
        InputStream is = DataMaps.class.getClassLoader().getResourceAsStream("version.properties");
        try {
            p.load(is);
            if (p.containsKey("version")) {
                softwareVersion = p.getProperty("version");
            }
        } catch (IOException e) {
            // Failed to load, just use the default
        }
        LOGGER.info("This is Plane/Sailing Server v{}", softwareVersion);

        try {
            // Load data
            DataMaps.initialise();

            // Set up track table
            trackTable.initialise();

            // Load custom tracks from config
            trackTable.loadCustomTracksFromConfig();

            // Set up connections
            webServer = new WebServer(CONFIG.getInt("comms.web-server.port"));

            List<? extends Config> aisReceiversConfig = CONFIG.getConfigList("comms.ais-receivers");
            for (Config c : aisReceiversConfig) {
                aisReceivers.add(new AISUDPReceiver(c.getInt("port"), trackTable));
            }

            List<? extends Config> adsbReceiversConfig = CONFIG.getConfigList("comms.adsb-receivers");
            for (Config c : adsbReceiversConfig) {
                switch (c.getString("protocol")) {
                    case "dump1090json" ->
                            adsbReceivers.add(new Dump1090JSONReader(c.getString("file"), trackTable));
                    case "beastbinary" ->
                            adsbReceivers.add(new BEASTBinaryTCPClient(c.getString("host"), c.getInt("port"), trackTable, false));
                    case "beastavr" ->
                            adsbReceivers.add(new BEASTAVRTCPClient(c.getString("host"), c.getInt("port"), trackTable));
                    case "sbs" ->
                            adsbReceivers.add(new SBSTCPClient(c.getString("host"), c.getInt("port"), trackTable, false));
                    default ->
                            LOGGER.error("Unknown air data protocol '{}'. Options are 'beastbinary', 'beastavr' and 'sbs'.", c.getString("protocol"));
                }
            }

            List<? extends Config> mlatReceiversConfig = CONFIG.getConfigList("comms.mlat-receivers");
            for (Config c : mlatReceiversConfig) {
                switch (c.getString("protocol")) {
                    case "beastbinary" ->
                            mlatReceivers.add(new BEASTBinaryTCPClient(c.getString("host"), c.getInt("port"), trackTable, true));
                    case "sbs" ->
                            mlatReceivers.add(new SBSTCPClient(c.getString("host"), c.getInt("port"), trackTable, true));
                    default ->
                            LOGGER.error("Unknown air data protocol '{}'. Options are 'beastbinary' and 'sbs'.", c.getString("comms.mlat-receiver.protocol"));
                }
            }

            List<? extends Config> aprsReceiversConfig = CONFIG.getConfigList("comms.aprs-receivers");
            for (Config c : aprsReceiversConfig) {
                aprsReceivers.add(new APRSTCPClient(c.getString("host"), c.getInt("port"), trackTable));
            }

        } catch (Exception ex) {
            LOGGER.error("Exception when setting up Plane/Sailing Server", ex);
            System.exit(1);
        }
    }

    private void run() {
        try {
            // Run web server thread
            webServer.run();

            // Run data receiver threads
            for (Client c : aisReceivers) {
                c.run();
            }
            for (Client c : adsbReceivers) {
                c.run();
            }
            for (Client c : mlatReceivers) {
                c.run();
            }
            for (Client c : aprsReceivers) {
                c.run();
            }

            // Add a JVM shutdown hook to stop threads nicely
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                webServer.stop();
                for (Client c : aisReceivers) {
                    c.stop();
                }
                for (Client c : adsbReceivers) {
                    c.stop();
                }
                for (Client c : mlatReceivers) {
                    c.stop();
                }
                for (Client c : aprsReceivers) {
                    c.stop();
                }
                trackTable.shutdown();
            }));

            LOGGER.info("Plane/Sailing Server is up and running!");
        } catch (Exception ex) {
            LOGGER.error("Exception when starting Plane/Sailing Server", ex);
            System.exit(1);
        }
    }

    public static String getSoftwareVersion() {
        return softwareVersion;
    }

    public TrackTable getTrackTable() {
        return trackTable;
    }

    public ConnectionStatus getWebServerStatus() {
        return webServer.getStatus();
    }

    /**
     * Get the status of the ADS-B receiver, or if there's more than one, return the "best" status amongst them.
     * "Disabled" will be returned if there are no receivers of this type configured.
     */
    public ConnectionStatus getADSBReceiverStatus() {
        if (!adsbReceivers.isEmpty()) {
            return getBestStatus(adsbReceivers);
        } else {
            return ConnectionStatus.DISABLED;
        }
    }

    /**
     * Get the status of the MLAT receiver, or if there's more than one, return the "best" status amongst them.
     * "Disabled" will be returned if there are no receivers of this type configured.
     */
    public ConnectionStatus getMLATReceiverStatus() {
        if (!mlatReceivers.isEmpty()) {
            return getBestStatus(mlatReceivers);
        } else if (adsbReceivers.stream().anyMatch(r -> r instanceof Dump1090JSONReader)) {
            // If using a Dump1090 JSON reader we don't need separate MLAT status, because the JSON data includes MLAT
            Optional<Client> r = adsbReceivers.stream().filter(r2 -> r2 instanceof Dump1090JSONReader).findFirst();
            return r.isPresent() ? r.get().getStatus() : ConnectionStatus.DISABLED;
        } else {
            return ConnectionStatus.DISABLED;
        }
    }

    /**
     * Get the status of the AIS receiver, or if there's more than one, return the "best" status amongst them.
     * "Disabled" will be returned if there are no receivers of this type configured.
     */
    public ConnectionStatus getAISReceiverStatus() {
        if (!aisReceivers.isEmpty()) {
            return getBestStatus(aisReceivers);
        } else {
            return ConnectionStatus.DISABLED;
        }
    }

    /**
     * Get the status of the APRS receiver, or if there's more than one, return the "best" status amongst them.
     * "Disabled" will be returned if there are no receivers of this type configured.
     */
    public ConnectionStatus getAPRSReceiverStatus() {
        if (!aprsReceivers.isEmpty()) {
            return getBestStatus(aprsReceivers);
        } else {
            return ConnectionStatus.DISABLED;
        }
    }

    private ConnectionStatus getBestStatus(List<Client> clients) {
        List<ConnectionStatus> statuses = clients.stream().map(Client::getStatus).toList();
        if (statuses.contains(ConnectionStatus.ACTIVE)) {
            return ConnectionStatus.ACTIVE;
        } else if (statuses.contains(ConnectionStatus.ONLINE)) {
            return ConnectionStatus.ONLINE;
        } else {
            return ConnectionStatus.OFFLINE;
        }
    }

}
