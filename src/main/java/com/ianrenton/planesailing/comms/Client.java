package com.ianrenton.planesailing.comms;

import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.app.TrackTable;

public abstract class Client {

	protected final TrackTable trackTable;
	protected boolean online;
	protected long lastReceivedTime;

	public Client(TrackTable trackTable) {
		this.trackTable = trackTable;
	}

	/**
	 * Run the client.
	 */
	public abstract void run();

	/**
	 * Stop the client.
	 */
	public abstract void stop();

	public ConnectionStatus getStatus() {
		if (online) {
			if (System.currentTimeMillis() - lastReceivedTime <= getTimeoutMillis()) {
				return ConnectionStatus.ACTIVE;
			} else {
				return ConnectionStatus.ONLINE;
			}
		} else {
			return ConnectionStatus.OFFLINE;
		}
	}

	/**
	 * Get the data type this connection handles, used only for logging.
	 */
	protected abstract String getDataType();

	/**
	 * Get the subclass logger implementation
	 */
	protected abstract Logger getLogger();

	/**
	 * Means for implementations to update the "last received time" so
	 * we know packets are arriving.
	 */
	protected void updatePacketReceivedTime() {
		lastReceivedTime = System.currentTimeMillis();
	}

	/**
	 * Means for implementations to provide their preferred socket timeout.
	 * We typically get many SBS messages a second, but APRS only rarely,
	 * so they have different timeouts.
	 */
	protected abstract int getTimeoutMillis();

}