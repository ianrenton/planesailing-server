package com.ianrenton.planesailing.app;

import com.ianrenton.planesailing.comms.*;
import com.ianrenton.planesailing.utils.DataMaps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
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
    private Client aisReceiver;
    private Client adsbReceiver;
    private Client mlatReceiver;
    private Client aprsReceiver;

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

        // Load data
        DataMaps.initialise();

        // Set up track table
        trackTable.initialise();

        // Load custom tracks from config
        trackTable.loadCustomTracksFromConfig();

        // Set up connections
        if (CONFIG.getBoolean("comms.web-server.enabled")) {
            webServer = new WebServer(CONFIG.getInt("comms.web-server.port"));
        }

        if (CONFIG.getBoolean("comms.ais-receiver.enabled")) {
            aisReceiver = new AISUDPReceiver(CONFIG.getInt("comms.ais-receiver.port"), trackTable);
        }

        if (CONFIG.getBoolean("comms.adsb-receiver.enabled")) {
            switch (CONFIG.getString("comms.adsb-receiver.protocol")) {
                case "dump1090json" ->
                        adsbReceiver = new Dump1090JSONReader(CONFIG.getString("comms.adsb-receiver.file"), trackTable);
                case "beastbinary" ->
                        adsbReceiver = new BEASTBinaryTCPClient(CONFIG.getString("comms.adsb-receiver.host"), CONFIG.getInt("comms.adsb-receiver.port"), trackTable, false);
                case "beastavr" ->
                        adsbReceiver = new BEASTAVRTCPClient(CONFIG.getString("comms.adsb-receiver.host"), CONFIG.getInt("comms.adsb-receiver.port"), trackTable);
                case "sbs" ->
                        adsbReceiver = new SBSTCPClient(CONFIG.getString("comms.adsb-receiver.host"), CONFIG.getInt("comms.adsb-receiver.port"), trackTable, false);
                default ->
                        LOGGER.error("Unknown air data protocol '{}'. Options are 'beastbinary', 'beastavr' and 'sbs'.", CONFIG.getString("comms.adsb-receiver.protocol"));
            }
        }

        if (CONFIG.getBoolean("comms.mlat-receiver.enabled")) {
            switch (CONFIG.getString("comms.mlat-receiver.protocol")) {
                case "beastbinary" ->
                        mlatReceiver = new BEASTBinaryTCPClient(CONFIG.getString("comms.mlat-receiver.host"), CONFIG.getInt("comms.mlat-receiver.port"), trackTable, true);
                case "sbs" ->
                        mlatReceiver = new SBSTCPClient(CONFIG.getString("comms.mlat-receiver.host"), CONFIG.getInt("comms.mlat-receiver.port"), trackTable, true);
                default ->
                        LOGGER.error("Unknown air data protocol '{}'. Options are 'beastbinary' and 'sbs'.", CONFIG.getString("comms.mlat-receiver.protocol"));
            }
        }

        if (CONFIG.getBoolean("comms.aprs-receiver.enabled")) {
            aprsReceiver = new APRSTCPClient(CONFIG.getString("comms.aprs-receiver.host"), CONFIG.getInt("comms.aprs-receiver.port"), trackTable);
        }
    }

    private void run() {
        // Run web server thread
        if (webServer != null) {
            webServer.run();
        }

        // Run data receiver threads
        if (aisReceiver != null) {
            aisReceiver.run();
        }
        if (adsbReceiver != null) {
            adsbReceiver.run();
        }
        if (mlatReceiver != null) {
            mlatReceiver.run();
        }
        if (aprsReceiver != null) {
            aprsReceiver.run();
        }

        // Add a JVM shutdown hook to stop threads nicely
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (webServer != null) {
                webServer.stop();
            }
            if (aisReceiver != null) {
                aisReceiver.stop();
            }
            if (adsbReceiver != null) {
                adsbReceiver.stop();
            }
            if (mlatReceiver != null) {
                mlatReceiver.stop();
            }
            if (aprsReceiver != null) {
                aprsReceiver.stop();
            }
            trackTable.shutdown();
        }));

        LOGGER.info("Plane/Sailing Server is up and running!");
    }

    public static String getSoftwareVersion() {
        return softwareVersion;
    }

    public TrackTable getTrackTable() {
        return trackTable;
    }

    public ConnectionStatus getWebServerStatus() {
        if (webServer != null) {
            return webServer.getStatus();
        } else {
            return ConnectionStatus.DISABLED;
        }
    }

    public ConnectionStatus getADSBReceiverStatus() {
        if (adsbReceiver != null) {
            return adsbReceiver.getStatus();
        } else {
            return ConnectionStatus.DISABLED;
        }
    }

    public ConnectionStatus getMLATReceiverStatus() {
        if (mlatReceiver != null) {
            return mlatReceiver.getStatus();
        } else if (adsbReceiver instanceof Dump1090JSONReader) {
            // If using the JSON reader we don't need separate MLAT status
            return adsbReceiver.getStatus();
        } else {
            return ConnectionStatus.DISABLED;
        }
    }

    public ConnectionStatus getAISReceiverStatus() {
        if (aisReceiver != null) {
            return aisReceiver.getStatus();
        } else {
            return ConnectionStatus.DISABLED;
        }
    }

    public ConnectionStatus getAPRSReceiverStatus() {
        if (aprsReceiver != null) {
            return aprsReceiver.getStatus();
        } else {
            return ConnectionStatus.DISABLED;
        }
    }

}
