package com.ianrenton.planesailing.comms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
	 * Handle an incoming message.
	 * 
	 * @param m
	 */
	protected abstract void handle(String m);
	
	/**
	 * Get the data type this connection handles, used only for logging.
	 */
	protected abstract String getDataType();
	
	/**
	 * Get the subclass logger implementation
	 */
	protected abstract Logger getLogger();

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
	 * Inner receiver thread. Reads lines from the TCP socket, and provides them
	 * to the handle() method.
	 */
	class Receiver implements Runnable {

		private Socket clientSocket;
		private BufferedReader in;

		public void run() {
			while (run) {
				while (run) {
					// Try to connect
					try {
						getLogger().info("Trying to make TCP connection to {}:{} to receive {}...", remoteHost, remotePort, getDataType());
						clientSocket = new Socket(remoteHost, remotePort);
						clientSocket.setSoTimeout(60000);
						clientSocket.setSoLinger(false, 0);
						in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
						getLogger().info("TCP socket for {} connected.", getDataType());
						break;
					} catch (IOException e) {
						try {
							getLogger().warn("TCP Socket could not connect, trying again in one minute...");
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
						getLogger().warn("TCP Socket exception, reconnecting...");
						break;
					}
				}
			}
		}
	}

}