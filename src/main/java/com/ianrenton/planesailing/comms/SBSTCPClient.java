package com.ianrenton.planesailing.comms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.app.TrackTable;
import com.ianrenton.planesailing.data.Aircraft;

/**
 * Receiver for SBS messages from a TCP server. See:
 * http://woodair.net/sbs/article/barebones42_socket_data.htm
 */
public class SBSTCPClient extends TCPClient {

	private static final Logger LOGGER = LogManager.getLogger(SBSTCPClient.class);

	/**
	 * Create the client
	 * 
	 * @param remoteHost Host to connect to.
	 * @param remotePort Port to connect to.
	 * @param trackTable The track table to use.
	 */
	public SBSTCPClient(String remoteHost, int remotePort, TrackTable trackTable) {
		super(remoteHost, remotePort, trackTable);
	}

	@Override
	protected void handle(String m) {
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

	@Override
	protected String getDataType() {
		return "ADS-B (SBS) data";
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
}
