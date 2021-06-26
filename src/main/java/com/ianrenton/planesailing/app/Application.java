package com.ianrenton.planesailing.app;

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
		// Load data
		DataMaps.initialise();
		
		// Set up track table
		trackTable.initialise();
		
		// Load custom tracks from config
		trackTable.loadCustomTracksFromConfig();
		
		// Set up connections
		if (CONFIG.hasPath("comms.web-server")) {
			webServer = new WebServer(CONFIG.getInt("comms.web-server.port"), trackTable);
		} else {
			LOGGER.warn("Web server config was missing from application.conf!");
		}
		if (CONFIG.hasPath("comms.ais-receiver")) {
			aisReceiver = new AISUDPReceiver(CONFIG.getInt("comms.ais-receiver.port"), trackTable);
		} else {
			LOGGER.info("No AIS receiver config found in application.conf, AIS receiver will not be created.");
		}
		if (CONFIG.hasPath("comms.sbs-receiver")) {
			sbsReceiver = new SBSTCPClient(CONFIG.getString("comms.sbs-receiver.host"), CONFIG.getInt("comms.sbs-receiver.port"), trackTable);
		} else {
			LOGGER.info("No SBS receiver config found in application.conf, SBS receiver will not be created.");
		}
		if (CONFIG.hasPath("comms.aprs-receiver")) {
			aprsReceiver = new APRSTCPClient(CONFIG.getString("comms.aprs-receiver.host"), CONFIG.getInt("comms.aprs-receiver.port"), trackTable);
		} else {
			LOGGER.info("No APRS receiver config found in application.conf, APRS receiver will not be created.");
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
		
		LOGGER.info("Plane Sailing Server is up and running!");
	}

}
