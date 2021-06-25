package com.ianrenton.planesailing.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.comms.AISUDPReceiver;
import com.ianrenton.planesailing.comms.SBSTCPClient;
import com.ianrenton.planesailing.utils.DataMaps;

/**
 * Server for Plane/Sailing.
 */
public class Application {
	private static final int WEB_SERVER_PORT = 80;
	private static final int AIS_RECEIVER_LOCAL_PORT = 9242;
	private static final String SBS_RECEIVER_HOST = "192.168.1.241";
	private static final int SBS_RECEIVER_PORT = 30003;
	public static final boolean PRINT_TRACK_TABLE_TO_STDOUT = false;

	public static final long DROP_AIR_TRACK_TIME = 300000; // 5 min
	public static final long DROP_AIR_TRACK_AT_ZERO_ALT_TIME = 10000; // Drop tracks at zero altitude sooner because
																		// they've likely landed, dead reckoning far
																		// past the airport runway looks weird
	public static final long DEFAULT_SHOW_ANTICIPATED_TIME = 60000; // 60 seconds
	public static final long SHIP_SHOW_ANTICIPATED_TIME = 300000; // 5 minutes
	public static final long DROP_MOVING_SHIP_TRACK_TIME = 1200000; // 20 minutes
	public static final long DROP_STATIC_SHIP_TRACK_TIME = 172800000; // 2 days

	private static final Logger LOGGER = LogManager.getLogger(Application.class);
	
	private final TrackTable trackTable = new TrackTable();

	private final AISUDPReceiver aisReceiver = new AISUDPReceiver(AIS_RECEIVER_LOCAL_PORT, trackTable);
	private final SBSTCPClient sbsReceiver = new SBSTCPClient(SBS_RECEIVER_HOST, SBS_RECEIVER_PORT, trackTable);

	/**
	 * Start the application
	 * @param args None used
	 */
	public static void main(String[] args) {
		Application app = new Application();
		app.run();
	}
	
	private void run() {
		// Load data
		DataMaps.initialise();
		
		// Set up track table
		trackTable.initialise();
		
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
