package com.ianrenton.planesailing.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.comms.AISUDPReceiver;
import com.ianrenton.planesailing.comms.APRSTCPClient;
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
	
	private static final Logger LOGGER = LogManager.getLogger(Application.class);
	private static String softwareVersion = "Unknown";
	
	private final TrackTable trackTable = new TrackTable();

	private WebServer webServer;
	private AISUDPReceiver aisReceiver;
	private TCPClient sbsReceiver;
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
		
		// Load custom tracks from config
		trackTable.loadCustomTracksFromConfig();
		
		// Set up connections
		if (CONFIG.getBoolean("comms.web-server.enabled")) {
			webServer = new WebServer(CONFIG.getInt("comms.web-server.port"), trackTable);
		}
		if (CONFIG.getBoolean("comms.ais-receiver.enabled")) {
			aisReceiver = new AISUDPReceiver(CONFIG.getInt("comms.ais-receiver.port"), trackTable);
		}
		if (CONFIG.getBoolean("comms.sbs-receiver.enabled")) {
			sbsReceiver = new SBSTCPClient(CONFIG.getString("comms.sbs-receiver.host"), CONFIG.getInt("comms.sbs-receiver.port"), trackTable);
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
		if (sbsReceiver != null) {
			sbsReceiver.run();
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
				if (sbsReceiver != null) {
					sbsReceiver.stop();
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
