package com.ianrenton.planesailing.comms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensky.libadsb.ModeSDecoder;
import org.opensky.libadsb.Position;
import org.opensky.libadsb.tools;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;
import org.opensky.libadsb.msgs.AirbornePositionV0Msg;
import org.opensky.libadsb.msgs.AirspeedHeadingMsg;
import org.opensky.libadsb.msgs.AltitudeReply;
import org.opensky.libadsb.msgs.CommBAltitudeReply;
import org.opensky.libadsb.msgs.CommBIdentifyReply;
import org.opensky.libadsb.msgs.IdentificationMsg;
import org.opensky.libadsb.msgs.IdentifyReply;
import org.opensky.libadsb.msgs.LongACAS;
import org.opensky.libadsb.msgs.ModeSReply;
import org.opensky.libadsb.msgs.ShortACAS;
import org.opensky.libadsb.msgs.SurfacePositionV0Msg;
import org.opensky.libadsb.msgs.VelocityOverGroundMsg;

import com.ianrenton.planesailing.app.TrackTable;
import com.ianrenton.planesailing.data.Aircraft;

/**
 * Receiver for ADS-B Mode S messages, with hexadecimal encoding, asterisk and
 * semicolon delimiters, and line breaks. (This is output by Dump1090 on port
 * 30002, and I think is the same as BEAST AVR format. We use this in preference
 * to "raw" ADS-B Mode S on port 30005, even though it requires an extra
 * encoding-then-decoding hex step, because having line break delimiters helps
 * us stay synced.)
 */
public class ADSBModeSHexTCPClient extends TCPClient {

	private static final Logger LOGGER = LogManager.getLogger(ADSBModeSHexTCPClient.class);
	private ModeSDecoder decoder = new ModeSDecoder();

	/**
	 * Create the client
	 * 
	 * @param remoteHost Host to connect to.
	 * @param remotePort Port to connect to.
	 * @param trackTable The track table to use.
	 */
	public ADSBModeSHexTCPClient(String remoteHost, int remotePort, TrackTable trackTable) {
		super(remoteHost, remotePort, trackTable);
	}

	@Override
	protected boolean read(InputStream in) {
		try {
			String line = new BufferedReader(new InputStreamReader(in)).readLine();
			if (line != null && line.length() > 2) {
				// Remove asterisk and semicolon
				String hex = line.substring(1, line.length() - 1);
				handle(hex);
			}
			return true;
		} catch (IOException ex) {
			getLogger().warn("Exception encountered in TCP Socket for {}.", getDataType(), ex);
			return false;
		}
	}

	/**
	 * Handle a new line of ADS-B Mode S data. Based on
	 * https://github.com/openskynetwork/java-adsb/blob/master/src/main/java/org/opensky/example/ExampleDecoder.java
	 * 
	 * @param m The Mode S packet, in hexadecimal form.
	 */
	private void handle(String hex) {
		try {
			// Decode the message
			ModeSReply msg;
			try {
				msg = decoder.decode(hex);
			} catch (BadFormatException e) {
				LOGGER.debug("Malformed message skipped. Message: {}", e.getMessage());
				return;
			} catch (UnspecifiedFormatError e) {
				LOGGER.debug("Unspecified message skipped.");
				return;
			}

			// Get the ICAO 24-bit hex code
			String icao24 = tools.toHexString(msg.getIcao24());
			
			// If this is a new track, add it to the track table
			if (!trackTable.containsKey(icao24)) {
				trackTable.put(icao24, new Aircraft(icao24));
			}

			// Extract the data and update the track
			Aircraft a = (Aircraft) trackTable.get(icao24);

			// check for erroneous messages; some receivers set
			// parity field to the result of the CRC polynomial division
			if (tools.isZero(msg.getParity()) || msg.checkParity()) { // CRC is ok

				// now check the message type, first looking for ADS-B types:
				switch (msg.getType()) {
				case ADSB_AIRBORN_POSITION_V0:
				case ADSB_AIRBORN_POSITION_V1:
				case ADSB_AIRBORN_POSITION_V2:
					AirbornePositionV0Msg ap0 = (AirbornePositionV0Msg) msg;

					// Figure out a position. If we have a real position decoded "properly" using two packets
					// (odd and even) then use it. Otherwise, we fall back on the "local position" provided
					// in the single packet we just received. This will be less accurate and will only work
					// for planes within 180 nmi of the base station, but should be good enough to get us
					// some kind of position rather than having it blank in the track table and no icon shown.
					Position airPos = decoder.decodePosition(System.currentTimeMillis(), ap0, trackTable.getBaseStationPositionForADSB());
					Position localPos = ap0.getLocalPosition(trackTable.getBaseStationPositionForADSB());
					if (airPos != null) {
						a.addPosition(airPos.getLatitude(), airPos.getLongitude());
					} else if (localPos != null) {
						a.addPosition(localPos.getLatitude(), localPos.getLongitude());
					}

					// Get an altitude, this could be barometric or geometric but Plane/Sailing doesn't really care
					if (ap0.hasAltitude()) {
						a.setAltitude(ap0.getAltitude());
					}
					
					// Got this message so we know this is airborne
					a.setOnGround(false);
					break;
					
				case ADSB_SURFACE_POSITION_V0:
				case ADSB_SURFACE_POSITION_V1:
				case ADSB_SURFACE_POSITION_V2:
					SurfacePositionV0Msg sp0 = (SurfacePositionV0Msg) msg;

					// Figure out a position. If we have a real position decoded "properly" using two packets
					// (odd and even) then use it. Otherwise, we fall back on the "local position" provided
					// in the single packet we just received. This will be less accurate and will only work
					// for planes within 180 nmi of the base station, but should be good enough to get us
					// some kind of position rather than having it blank in the track table and no icon shown.
					Position surPos	= decoder.decodePosition(System.currentTimeMillis(), sp0, trackTable.getBaseStationPositionForADSB());
					Position localPos2 = sp0.getLocalPosition(trackTable.getBaseStationPositionForADSB());
					if (surPos != null) {
						a.addPosition(surPos.getLatitude(), surPos.getLongitude());
					} else if (localPos2 != null) {
						a.addPosition(localPos2.getLatitude(), localPos2.getLongitude());
					}

					if (sp0.hasGroundSpeed()) {
						a.setSpeed(sp0.getGroundSpeed());
					}
					
					// We can approximate heading as course here, I suppose unless the aircraft
					// is being pushed?
					if (sp0.hasValidHeading()) {
						a.setHeading(sp0.getHeading());
						a.setCourse(sp0.getHeading());
					}
					
					// Got this message so we know this is on the ground
					a.setOnGround(true);
					a.setAltitude(0.0);
					break;
					
				case ADSB_AIRSPEED:
					AirspeedHeadingMsg airspeed = (AirspeedHeadingMsg) msg;
					
					if (airspeed.hasAirspeedInfo()) {
						a.setSpeed(airspeed.getAirspeed());
					}
					
					// Might as well approximate heading as course here,
					// in lieu of any other source
					if (airspeed.hasHeadingStatusFlag()) {
						a.setHeading(airspeed.getHeading());
						a.setCourse(airspeed.getHeading());
					}

					if (airspeed.hasVerticalRateInfo()) {
						a.setVerticalRate(Double.valueOf(airspeed.getVerticalRate()));
					}
					break;
					
				case ADSB_VELOCITY:
					VelocityOverGroundMsg veloc = (VelocityOverGroundMsg) msg;

					if (veloc.hasVelocityInfo()) {
						a.setSpeed(veloc.getVelocity());
					}
					
					// Might as well approximate heading as course here,
					// in lieu of any other source
					if (veloc.hasVelocityInfo()) {
						a.setHeading(veloc.getHeading());
						a.setCourse(veloc.getHeading());
					}

					if (veloc.hasVerticalRateInfo()) {
						a.setVerticalRate(Double.valueOf(veloc.getVerticalRate()));
					}
					break;
					
				case ADSB_IDENTIFICATION:
					IdentificationMsg ident = (IdentificationMsg) msg;
					
					a.setCallsign(new String(ident.getIdentity()));
					a.setCategory(getICAOCategoryFromIdentMsg(ident));
					break;
					
				case ADSB_EMERGENCY:
				case ADSB_STATUS_V0:
				case ADSB_AIRBORN_STATUS_V1:
				case ADSB_AIRBORN_STATUS_V2:
				case ADSB_SURFACE_STATUS_V1:
				case ADSB_SURFACE_STATUS_V2:
				case ADSB_TCAS:
				case ADSB_TARGET_STATE_AND_STATUS:
					// Plane/Sailing doesn't need to know about this yet
					// Useful things in future that can be extracted from some of these packets include:
					// * True vs Mag north correction
					// * Airspeed vs Ground speed correction
					// * Barometric vs ground based altitude correction
					// * Autopilot, alt hold, VNAV/LNAV and approach flags
					break;
					
				case EXTENDED_SQUITTER:
					// "Squitter", eww.
					break;
				default:
					// Type not applicable for this downlink format
					break;
				}
				
			} else if (msg.getDownlinkFormat() != 17) { // CRC failed, if it's not ADS-B then check some other Mode S types that we support
				switch (msg.getType()) {
					
				case SHORT_ACAS:
					ShortACAS acas = (ShortACAS) msg;
					if (acas.getAltitude() != null) {
						a.setAltitude(acas.getAltitude());
						a.setOnGround(!acas.isAirborne());
					}
					break;
					
				case ALTITUDE_REPLY:
					AltitudeReply alti = (AltitudeReply) msg;
					if (alti.getAltitude() != null) {
						a.setAltitude(alti.getAltitude());
						a.setOnGround(alti.isOnGround());
					}
					break;
					
				case IDENTIFY_REPLY:
					IdentifyReply identify = (IdentifyReply) msg;
					a.setSquawk(Integer.valueOf(identify.getIdentity()));
					break;
					
				case LONG_ACAS:
					LongACAS long_acas = (LongACAS) msg;
					if (long_acas.getAltitude() != null) {
						a.setAltitude(long_acas.getAltitude());
						a.setOnGround(!long_acas.isAirborne());
					}
					break;
					
				case COMM_B_ALTITUDE_REPLY:
					CommBAltitudeReply commBaltitude = (CommBAltitudeReply) msg;
					if (commBaltitude.getAltitude() != null) {
						a.setAltitude(commBaltitude.getAltitude());
						a.setOnGround(commBaltitude.isOnGround());
					}
					break;
					
				case COMM_B_IDENTIFY_REPLY:
					CommBIdentifyReply commBidentify = (CommBIdentifyReply) msg;
					a.setSquawk(Integer.valueOf(commBidentify.getIdentity()));
					break;
					
				case COMM_D_ELM:
				case MODES_REPLY:
				case ALL_CALL_REPLY:
				case MILITARY_EXTENDED_SQUITTER:
					// Plane/Sailing doesn't need to know about this yet
					break;
				default:
					// Type not applicable for this downlink format
				}
			} else {
				LOGGER.debug("Message contains bit errors.");
			}

		} catch (Exception ex) {
			getLogger().warn("TCP Socket for {} encountered an exception handling line {}", getDataType(), hex, ex);
		}
	}

	/**
	 * Return a category like "A2" from an ident message. The Java-ADSB
	 * library's code does provide a description field but we prefer to
	 * have this and use our own shorter descriptions and pick the right
	 * symbol codes based on our CSVs.
	 */
	private String getICAOCategoryFromIdentMsg(IdentificationMsg ident) {
		if (ident.getFormatTypeCode() > 0 && ident.getFormatTypeCode() <= 4) {
			String[] formatTypeCodeLetters = new String[] {"", "D", "C", "B", "A"};
			return formatTypeCodeLetters[ident.getFormatTypeCode()] + String.valueOf(ident.getEmitterCategory());
		} else {
			return null;
		}
	}

	@Override
	protected int getSocketTimeoutMillis() {
		return 60000;
	}

	@Override
	protected String getDataType() {
		return "ADS-B (Mode S) data";
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
}
