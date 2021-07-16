package com.ianrenton.planesailing.comms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensky.libadsb.ModeSDecoder;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;

import com.ianrenton.planesailing.app.TrackTable;

/**
 * Receiver for ADS-B & other Mode S messages, in BEAST AVR format: hexadecimal
 * encoding, asterisk and semicolon delimiters, and line breaks. (This is output
 * by Dump1090 on port 30002.)
 */
public class BEASTAVRTCPClient extends TCPClient {

	private static final String DATA_TYPE = "BEAST AVR Mode-S (ADS-B) data";
	private static final Logger LOGGER = LogManager.getLogger(BEASTAVRTCPClient.class);
	private ModeSDecoder decoder = new ModeSDecoder();

	/**
	 * Create the client
	 * 
	 * @param remoteHost Host to connect to.
	 * @param remotePort Port to connect to.
	 * @param trackTable The track table to use.
	 */
	public BEASTAVRTCPClient(String remoteHost, int remotePort, TrackTable trackTable) {
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
	 * Handle a new line of ADS-B Mode S data.
	 * 
	 * @param m The Mode S packet, in hexadecimal form.
	 */
	private void handle(String hex) {
		try {
			BEASTBinaryTCPClient.handle(decoder.decode(hex), trackTable, DATA_TYPE);
		} catch (BadFormatException e) {
			LOGGER.debug("Malformed message skipped. Message: {}", e.getMessage());
		} catch (UnspecifiedFormatError e) {
			LOGGER.debug("Unspecified message skipped.");
		}
	}

	@Override
	protected int getSocketTimeoutMillis() {
		return 60000;
	}

	@Override
	protected String getDataType() {
		return DATA_TYPE;
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
}
