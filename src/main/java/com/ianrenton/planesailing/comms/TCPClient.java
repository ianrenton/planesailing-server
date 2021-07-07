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
						clientSocket.setKeepAlive(true);
						in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
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
					try {
						String line = in.readLine();
						if (line != null) {
							try {
								handle(line);
							} catch (Exception ex) {
								getLogger().warn("TCP Socket for {} encountered an exception handling line {}", getDataType(), line, ex);
							}
						} else {
							getLogger().warn("TCP Socket for {} read no data, reconnecting...", getDataType());
							try {
								Thread.sleep(1000);
								clientSocket.close();
							} catch (IOException | InterruptedException e) {
								// Probably closed anyway
							}
						}
					} catch (IOException ex) {
						getLogger().warn("TCP Socket for {} threw an exception, reconnecting...", getDataType());
						try {
							Thread.sleep(1000);
							clientSocket.close();
						} catch (IOException | InterruptedException e) {
							// Probably closed anyway
						}
						break;
					}
				}
			}
		}
	}

}