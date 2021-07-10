package com.ianrenton.planesailing.comms;

import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.app.TrackTable;
import com.ianrenton.planesailing.data.APRSTrack;

import net.ab0oo.aprs.parser.APRSPacket;
import net.ab0oo.aprs.parser.CourseAndSpeedExtension;
import net.ab0oo.aprs.parser.InformationField;
import net.ab0oo.aprs.parser.ObjectPacket;
import net.ab0oo.aprs.parser.Parser;
import net.ab0oo.aprs.parser.Position;
import net.ab0oo.aprs.parser.PositionPacket;

/**
 * Receiver for messages from an APRS KISS server, e.g. Direwolf.
 * 
 * This is a multi-layered protocol where KISS frames encapsulate AX.25 frames,
 * which are binary data that may contain APRS information that is useful to us.
 * 
 * Many fully-featured implementations exist for sending and receiving
 * APRS-in-AX.25-in-KISS, and I am particularly indebted to the following
 * projects: https://github.com/xba1k/ax25irc
 * https://github.com/ab0oo/javAPRSlib https://github.com/sivantoledo/javAX25
 * https://github.com/amishHammer/libkisstnc-java
 * 
 * This is a much cut-down implementation because for this project we only
 * really care about receiving position information.
 */
public class APRSTCPClient extends TCPClient {

	private static final Logger LOGGER = LogManager.getLogger(APRSTCPClient.class);
	private static final byte FEND = (byte) 0xC0;
	private static final byte FESC = (byte) 0xDB;
	private static final byte TFEND = (byte) 0xDC;
	private static final byte TFESC = (byte) 0xDD;

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
	protected boolean read(InputStream in) {
		try {
			byte[] bytes = new byte[1000];
			int bytesReceived = 0;
			int i;
			while ((i = in.read()) != -1) {
				byte b = (byte) i;
				if (b == FEND) {
					// Delimiter found, process contents of byte buffer.
					if (bytesReceived > 0) {
						byte[] aprsMessage = new byte[bytesReceived];
						System.arraycopy(bytes, 0, aprsMessage, 0, bytesReceived);
						try {
							aprsMessage = processEscapedBytes(aprsMessage);
							extractAPRSData(aprsMessage);
						} catch (Exception ex) {
							LOGGER.error("Encountered an exception when handling an APRS packet.", ex);
						}
					}

					// Reset the buffer
					bytes = new byte[1000];
					bytesReceived = 0;
				} else {
					// New non-delimiter byte found, add to the buffer
					bytes[bytesReceived++] = b;
				}
			}
			return true;
		} catch (IOException ex) {
			getLogger().warn("Exception encountered in TCP Socket for {}.", getDataType(), ex);
			return false;
		}
	}

	/**
	 * Process the escaped bytes in a KISS frame. Code from
	 * https://github.com/amishHammer/libkisstnc-java
	 */
	private static byte[] processEscapedBytes(byte[] data) {
		byte[] tmp = new byte[data.length - 1];
		boolean handlingEscape = false;
		int wPos = 0;
		for (int rPos = 1; rPos < data.length; rPos++) {
			if (handlingEscape) {
				if (data[rPos] == TFEND) {
					tmp[wPos++] = FEND;
				} else if (data[rPos] == TFESC) {
					tmp[wPos++] = FESC;
				} else {
					LOGGER.warn("Bad Escaped byte: " + data[rPos]);
				}
				handlingEscape = false;
			} else if (data[rPos] == FESC) {
				handlingEscape = true;
			} else {
				tmp[wPos++] = data[rPos];
			}
		}
		byte[] procData = new byte[wPos];
		System.arraycopy(tmp, 0, procData, 0, wPos);
		return procData;
	}

	/**
	 * Extract APRS data from an AX.25 frame. Based on code from
	 * https://github.com/ab0oo/javAPRSlib
	 */
	public void extractAPRSData(byte[] bytes) throws Exception {
		APRSPacket packet = Parser.parseAX25(bytes);
		InformationField data = packet.getAprsInformation();
		if (packet.isAprs() && !packet.hasFault() && data != null) {
			addDataToTrack(packet);
		}
	}

	private void addDataToTrack(APRSPacket packet) {
		// Extract core data
		String callsign = packet.getSourceCall();
		String destCall = packet.getDestinationCall();
		String route = packet.getDigiString();

		// If this is a new track, add it to the track table
		if (!trackTable.containsKey(callsign)) {
			trackTable.put(callsign, new APRSTrack(callsign));
		}
		APRSTrack a = (APRSTrack) trackTable.get(callsign);

		// Extract APRS data
		InformationField data = packet.getAprsInformation();
		String comment = data.getComment();

		// Tweak formatting of route, and only keep the comment
		// if it contains human-entered data, approximated here
		// by being more than five characters long
		if (route.startsWith(",")) {
			route = route.replaceFirst(",", "");
		}
		if (comment.length() < 5) {
			comment = null;
		}

		// Extract position data if available
		Position p = null;
		if (data instanceof PositionPacket) {
			p = ((PositionPacket) data).getPosition();
		} else if (data instanceof ObjectPacket) {
			p = ((ObjectPacket) data).getPosition();
		}

		// Extract course/speed data if available
		Double course = null;
		Double speed = null;
		if (data.getExtension() instanceof CourseAndSpeedExtension) {
			course = Double.valueOf(((CourseAndSpeedExtension) data.getExtension()).getCourse());
			speed = Double.valueOf(((CourseAndSpeedExtension) data.getExtension()).getSpeed());
		}

		// Update the track.
		if (destCall != null) {
			a.setPacketDestCall(destCall);
		}
		if (route != null) {
			a.setPacketRoute(route);
		}
		if (comment != null) {
			a.setComment(comment);
		}
		if (p != null) {
			a.addPosition(p.getLatitude(), p.getLongitude());
			if (p.getAltitude() > 0) {
				a.setAltitude(p.getAltitude());
			}
		}
		if (course != null) {
			a.setCourse(course);
			a.setHeading(course);
		}
		if (speed != null) {
			a.setSpeed(speed);
		}
	}

	@Override
	protected int getSocketTimeoutMillis() {
		return 3600000;
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
