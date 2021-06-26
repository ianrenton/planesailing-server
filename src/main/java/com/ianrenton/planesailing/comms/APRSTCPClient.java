package com.ianrenton.planesailing.comms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.app.TrackTable;
import com.ianrenton.planesailing.data.APRSTrack;

import net.ab0oo.aprs.parser.APRSPacket;
import net.ab0oo.aprs.parser.Parser;
import net.ab0oo.aprs.parser.PositionPacket;

/**
 * Receiver for messages from an APRS server.
 */
public class APRSTCPClient extends TCPClient {

	private static final Logger LOGGER = LogManager.getLogger(APRSTCPClient.class);

	/**
	 * Create the client
	 * 
	 * @param remoteHost Host to connect to.
	 * @param remotePort Port to connect to.
	 * @param trackTable The track table to use.
	 */
	public APRSTCPClient(String remoteHost, int remotePort, TrackTable trackTable) {
		super(remoteHost, remotePort, trackTable);
	}

	@Override
	protected void handle(String m) {
		try {
			APRSPacket p = Parser.parse(m);
			if (!p.hasFault()) {
				String callsign = p.getSourceCall();
				
				// If this is a new track, add it to the track table
				if (!trackTable.containsKey(callsign)) {
					trackTable.put(callsign, new APRSTrack(callsign));
				}

				// Extract the data and update the track
				APRSTrack a = (APRSTrack) trackTable.get(callsign);

				switch (p.getType()) {
				case T_POSITION:
					PositionPacket pp = (PositionPacket) p.getAprsInformation();
					a.addPosition(pp.getPosition().getLatitude(), pp.getPosition().getLongitude());
					a.setAltitude(pp.getPosition().getAltitude());
					break;
				default:
					LOGGER.info("Received APRS packet of unhandled type {}", p.getType());
					break;
				}
			}
		} catch (Exception ex) {
			LOGGER.error("Exception parsing APRS packet: {}", m, ex);
		}
	}

	@Override
	protected String getDataType() {
		return "APRS data";
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}

}
