package com.ianrenton.planesailing.comms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.app.TrackTable;
import com.ianrenton.planesailing.data.Aircraft;

/**
 * Receiver for SBS messages from a TCP server. See:
 * http://woodair.net/sbs/article/barebones42_socket_data.htm
 */
public class SBSTCPClient {
	private static final Logger LOGGER = LogManager.getLogger(SBSTCPClient.class);
	private final String remoteHost;
	private final int remotePort;
	private final TrackTable trackTable;
	private final Receiver receiver = new Receiver();
	private boolean run = true;

	/**
	 * Create the client
	 * 
	 * @param remoteHost Host to connect to.
	 * @param remotePort Port to connect to.
	 * @param trackTable The track table to use.
	 */
	public SBSTCPClient(String remoteHost, int remotePort, TrackTable trackTable) {
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
		this.trackTable = trackTable;
	}

	/**
	 * Run the client.
	 */
	public void run() {
		run = true;
		new Thread(receiver).start();
	}

	/**
	 * Stop the client.
	 */
	public void stop() {
		run = false;
	}

	/**
	 * Handle an incoming message.
	 * 
	 * @param m
	 */
	private void handle(String m) {
		String[] fields = m.split(",");
		String icaoHex = fields[4];

		// If this is a new track, add it to the track table
		if (!trackTable.containsKey(icaoHex)) {
			trackTable.put(icaoHex, new Aircraft(icaoHex));
		}

		// Extract the data and update the track
		Aircraft a = (Aircraft) trackTable.get(icaoHex);

		if (fields[0].equals("MSG")) {
			if (fields.length > 10) {
				String callsign = fields[10].trim();
				if (!callsign.isEmpty()) {
					a.setCallsign(callsign);
				}
			}

			if (fields.length > 11) {
				String altitude = fields[11].trim();
				if (!altitude.isEmpty()) {
					a.setAltitude(Double.valueOf(altitude));
				}
			}

			if (fields.length > 12) {
				String speed = fields[12].trim();
				if (!speed.isEmpty()) {
					a.setSpeed(Double.valueOf(speed));
				}
			}

			if (fields.length > 13) {
				String course = fields[13].trim();
				if (!course.isEmpty()) {
					a.setCourse(Double.valueOf(course));
				}
			}

			if (fields.length > 15) {
				String latitude = fields[14].trim();
				String longitude = fields[15].trim();
				if (!latitude.isEmpty() && !longitude.isEmpty()) {
					a.addPosition(Double.valueOf(latitude), Double.valueOf(longitude));
				}
			}

			if (fields.length > 16) {
				String verticalRate = fields[16].trim();
				if (!verticalRate.isEmpty()) {
					a.setVerticalRate(Double.valueOf(verticalRate));
				}
			}

			if (fields.length > 17) {
				String squawk = fields[17].trim();
				if (!squawk.isEmpty()) {
					a.setSquawk(Integer.valueOf(squawk));
				}
			}

			if (fields.length > 21) {
				String isOnGround = fields[21].trim();
				if (!isOnGround.isEmpty()) {
					a.setOnGround(isOnGround != "0");
				}
			}
		}
	}

	/**
	 * Inner receiver thread. Reads datagrams from the UDP socket, pipes them over
	 * to the third-party AISInputStreamReader.
	 */
	private class Receiver implements Runnable {

		private Socket clientSocket;
		private BufferedReader in;

		public void run() {
			while (run) {
				while (run) {
					// Try to connect
					try {
						LOGGER.info("Trying to make TCP connection to {}:{} to receive SBS data...", remoteHost,
								remotePort);
						clientSocket = new Socket(remoteHost, remotePort);
						clientSocket.setSoTimeout(5000);
						clientSocket.setSoLinger(false, 0);
						in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
						LOGGER.info("TCP socket for SBS data connected.");
						break;
					} catch (IOException e) {
						try {
							LOGGER.warn("TCP Socket could not connect, trying again in one minute...");
							TimeUnit.MINUTES.sleep(1);
						} catch (InterruptedException ie) {
						}
					}
				}

				while (run) {
					try {
						String line = in.readLine();
						handle(line);
					} catch (IOException ex) {
						LOGGER.warn("TCP Socket exception, reconnecting...");
						break;
					}
				}
			}
		}
	}
}
