package com.ianrenton.planesailing.comms;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.app.TrackTable;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * The HTTP server that will provide data to the Plane/Sailing client over the
 * web.
 */
public class WebServer {
	private static final Logger LOGGER = LogManager.getLogger(WebServer.class);

	private HttpServer server;
	private final int localPort;
	private final TrackTable trackTable;

	/**
	 * Create the web server
	 * 
	 * @param localPort  Port to listen on.
	 * @param trackTable The track table to use.
	 */
	public WebServer(int localPort, TrackTable trackTable) {
		this.localPort = localPort;
		this.trackTable = trackTable;

		try {
			server = HttpServer.create(new InetSocketAddress(localPort), 0);
			server.createContext("/first", new CallHandler(Call.FIRST));
			server.createContext("/update", new CallHandler(Call.UPDATE));
			server.setExecutor(null);
		} catch (IOException ex) {
			LOGGER.error("Could not set up web server", ex);
		}
	}

	public void run() {
		server.start();
		LOGGER.info("Started web server on port {}.", localPort);
	}

	public void stop() {
		server.stop(0);
	}

	private class CallHandler implements HttpHandler {

		private final Call call;

		public CallHandler(Call call) {
			this.call = call;
		}

		@Override
		public void handle(HttpExchange t) throws IOException {
			String response = "";
			switch (call) {
			case FIRST:
				response = trackTable.getFirstCallJSON();
				break;
			case UPDATE:
				response = trackTable.getUpdateCallJSON();
				break;
			}
			
			try {
                final Headers headers = t.getResponseHeaders();
                final String requestMethod = t.getRequestMethod().toUpperCase();
                headers.add("Access-Control-Allow-Origin", "*");
                switch (requestMethod) {
                    case "GET":
                        headers.set("Content-Type", String.format("application/json; charset=%s", "UTF8"));
                        final byte[] rawResponseBody = response.getBytes("UTF8");
                        t.sendResponseHeaders(200, rawResponseBody.length);
                        t.getResponseBody().write(rawResponseBody);
                        break;
                    case "OPTIONS":
                        headers.set("Allow", "GET, OPTIONS");
                        headers.add("Access-Control-Allow-Methods", "GET, OPTIONS");
                        headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                        t.sendResponseHeaders(200, -1);
                        break;
                    default:
                        headers.set("Allow", "GET, OPTIONS");
                        t.sendResponseHeaders(405, -1);
                        break;
                }
            } finally {
                t.close();
            }
		}
	}

	private enum Call {
		FIRST, UPDATE
	};
}
