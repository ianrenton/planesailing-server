package com.ianrenton.planesailing.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.comms.AISUDPReceiver;
import com.ianrenton.planesailing.comms.APRSTCPClient;
import com.ianrenton.planesailing.comms.BEASTAVRTCPClient;
import com.ianrenton.planesailing.comms.BEASTBinaryTCPClient;
import com.ianrenton.planesailing.comms.SBSTCPClient;
import com.ianrenton.planesailing.comms.TCPClient;
import com.ianrenton.planesailing.comms.WebServer;
import com.ianrenton.planesailing.utils.DataMaps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Server for Plane/Sailing.
 */
public class Application {	
	public static final Config CONFIG = ConfigFactory.load().getConfig("plane-sailing-server");
	public static final long START_TIME = System.currentTimeMillis();
	
	private static final Logger LOGGER = LogManager.getLogger(Application.class);
	private static String softwareVersion = "Unknown";
	
	private final TrackTable trackTable = new TrackTable();

	private WebServer webServer;
	private AISUDPReceiver aisReceiver;
	private TCPClient adsbReceiver;
	private TCPClient mlatReceiver;
	private TCPClient aprsReceiver;

	/**
	 * Start the application
	 * @param args None used
	 */
	public static void main(String[] args) {
		Application app = new Application();
		app.run();
	}
	
	public Application() {
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
		
		// Load custom tracks and AIS names from config
		trackTable.loadCustomTracksFromConfig();
		trackTable.loadCustomAISNamesFromConfig();
		
		// Set up connections
		if (CONFIG.getBoolean("comms.web-server.enabled")) {
			webServer = new WebServer(CONFIG.getInt("comms.web-server.port"), trackTable);
		}
		
		if (CONFIG.getBoolean("comms.ais-receiver.enabled")) {
			aisReceiver = new AISUDPReceiver(CONFIG.getInt("comms.ais-receiver.port"), trackTable);
		}
		
		if (CONFIG.getBoolean("comms.adsb-receiver.enabled")) {
			switch (CONFIG.getString("comms.adsb-receiver.protocol")) {
			case "beastbinary":
				adsbReceiver = new BEASTBinaryTCPClient(CONFIG.getString("comms.adsb-receiver.host"), CONFIG.getInt("comms.adsb-receiver.port"), trackTable, false);
				break;
			case "beastavr":
				adsbReceiver = new BEASTAVRTCPClient(CONFIG.getString("comms.adsb-receiver.host"), CONFIG.getInt("comms.adsb-receiver.port"), trackTable);
				break;
			case "sbs":
				adsbReceiver = new SBSTCPClient(CONFIG.getString("comms.adsb-receiver.host"), CONFIG.getInt("comms.adsb-receiver.port"), trackTable, false);
				break;
			default:
				LOGGER.error("Unknown air data protocol '{}'. Options are 'beastbinary', 'beastavr' and 'sbs'.", CONFIG.getString("comms.adsb-receiver.protocol"));
			}
		}
		
		if (CONFIG.getBoolean("comms.mlat-receiver.enabled")) {
			switch (CONFIG.getString("comms.mlat-receiver.protocol")) {
			case "beastbinary":
				mlatReceiver = new BEASTBinaryTCPClient(CONFIG.getString("comms.mlat-receiver.host"), CONFIG.getInt("comms.mlat-receiver.port"), trackTable, true);
				break;
			case "sbs":
				mlatReceiver = new SBSTCPClient(CONFIG.getString("comms.mlat-receiver.host"), CONFIG.getInt("comms.mlat-receiver.port"), trackTable, true);
				break;
			default:
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
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
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
			}
		});
		
		LOGGER.info("Plane/Sailing Server is up and running!");
	}

	protected static String getSoftwareVersion() {
		return softwareVersion;
	}
}
