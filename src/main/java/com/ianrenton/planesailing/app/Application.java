package com.ianrenton.planesailing.app;

import com.ianrenton.planesailing.comms.AISUDPReceiver;
import com.ianrenton.planesailing.comms.SBSTCPClient;
import com.ianrenton.planesailing.utils.DataMaps;

/**
 * Server for Plane/Sailing.
 * 
 * TODO:
 * * Move more functionality across from front end
 * * Xstream based serialisation for persistence
 * * Make ports and IP addresses configurable
 * * Finish SBS Receive with aircraft category and type
 * * Add loading of base station, airport and seaport data from file on disk at runtime
 * * Implement APRS Receive
 * * Implement web server
 */
public class Application {
	private static final int WEB_SERVER_PORT = 80;
	private static final int AIS_RECEIVER_LOCAL_PORT = 9242;
	private static final String SBS_RECEIVER_HOST = "192.168.1.241";
	private static final int SBS_RECEIVER_PORT = 30003;
	
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
		// Run threads
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
	}

}
