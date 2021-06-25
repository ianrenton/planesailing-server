package com.ianrenton.planesailing.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.comms.AISUDPReceiver;
import com.ianrenton.planesailing.comms.SBSTCPClient;
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

	private final AISUDPReceiver aisReceiver = new AISUDPReceiver(CONFIG.getInt("comms.ais-receiver.port"), trackTable);
	private final SBSTCPClient sbsReceiver = new SBSTCPClient(CONFIG.getString("comms.sbs-receiver.host"), CONFIG.getInt("comms.sbs-receiver.port"), trackTable);

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
	}
	
	private void run() {		
		// Run data receiver threads
		aisReceiver.run();
		sbsReceiver.run();
		
		// Add a JVM shutdown hook to stop threads nicely
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				aisReceiver.stop();
				sbsReceiver.stop();
				trackTable.shutdown();
			}
		});
		
		LOGGER.info("Plane Sailing Server is up and running!");
	}

}
