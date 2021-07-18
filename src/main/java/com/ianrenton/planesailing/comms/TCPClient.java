package com.ianrenton.planesailing.comms;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.app.TrackTable;

/**
 * Generic line-reading TCP client implementation, to abstract out commonality
 * between SBS and APRS clients.
 */
public abstract class TCPClient {

	protected final String remoteHost;
	protected final int remotePort;
	protected final TrackTable trackTable;
	private final Receiver receiver = new Receiver();
	protected boolean run = true;
	private boolean online;
	private long lastReceivedTime;

	/**
	 * Create the client
	 * 
	 * @param remoteHost Host to connect to.
	 * @param remotePort Port to connect to.
	 * @param trackTable The track table to use.
	 */
	public TCPClient(String remoteHost, int remotePort, TrackTable trackTable) {
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
	 * Read data from the stream, and process it if appropriate.
	 * This method can block for as long as it likes trying to
	 * read, but the TCP server implements a socket timeout so
	 * after a certain amount of time any read operation on the
	 * stream will fail.
	 * 
	 * @param in The input stream to read data from.
	 * @return true if data was successfully read this time,
	 * regardless of whether we chose to process it or not. False
	 * if the read operation failed, and we need to reconnect
	 * the socket.
	 */
	protected abstract boolean read(InputStream in);
	
	/**
	 * Get the data type this connection handles, used only for logging.
	 */
	protected abstract String getDataType();
	
	/**
	 * Get the subclass logger implementation
	 */
	protected abstract Logger getLogger();

	/**
	 * Means for implementations to provide their preferred socket timeout.
	 * We typically get many SBS messages a second, but APRS only rarely,
	 * so they have different timeouts.
	 */
	protected abstract int getSocketTimeoutMillis();
	
	/**
	 * Means for implementations to update the "last received time" so
	 * we know packets are arriving.
	 */
	protected void updatePacketReceivedTime() {
		lastReceivedTime = System.currentTimeMillis();
	}

	/**
	 * Inner receiver thread. Reads lines from the TCP socket, and provides them
	 * to the handle() method.
	 */
	private class Receiver implements Runnable {

		private Socket clientSocket;
		private InputStream in;

		public void run() {
			while (run) {
				while (run) {
					// Try to connect
					try {
						getLogger().info("Trying to make TCP connection to {}:{} to receive {}...", remoteHost, remotePort, getDataType());
						clientSocket = new Socket(remoteHost, remotePort);
						clientSocket.setSoTimeout(getSocketTimeoutMillis());
						clientSocket.setSoLinger(false, 0);
						clientSocket.setKeepAlive(true);
						in = clientSocket.getInputStream();
						online = true;
						getLogger().info("TCP socket for {} connected.", getDataType());
						break;
					} catch (IOException e) {
						try {
							getLogger().warn("TCP Socket could for {} not connect, trying again in one minute...", getDataType());
							TimeUnit.MINUTES.sleep(1);
						} catch (InterruptedException ie) {
						}
					}
				}

				while (run) {
					boolean ok = read(in);
					if (!ok) {
						getLogger().warn("TCP Socket for {} read failed, reconnecting...", getDataType());
						try {
							online = false;
							Thread.sleep(1000);
							clientSocket.close();
							break;
						} catch (IOException | InterruptedException e) {
							// Probably closed anyway
						}
					}
				}
			}
		}
	}

	public ConnectionStatus getStatus() {
		if (online) {
			if (System.currentTimeMillis() - lastReceivedTime <= getSocketTimeoutMillis()) {
				return ConnectionStatus.ACTIVE;
			} else {
				return ConnectionStatus.ONLINE;
			}
		} else {
			return ConnectionStatus.OFFLINE;
		}
	}
}