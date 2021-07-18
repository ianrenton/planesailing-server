package com.ianrenton.planesailing.comms;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import com.ianrenton.planesailing.app.Application;
import com.ianrenton.planesailing.data.Track;
import com.sun.management.OperatingSystemMXBean;
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
	private static final OperatingSystemMXBean OS_BEAN = ManagementFactory
			.getPlatformMXBean(OperatingSystemMXBean.class);
	// Work-around to specify total memory size of the PC manually (here, 2GB to
	// match my
	// Raspberry Pi) for JDKs where querying it doesn't work properly
	private static final long TOTAL_MEM_BYTES = (OS_BEAN.getTotalPhysicalMemorySize() != 0)
			? OS_BEAN.getTotalPhysicalMemorySize()
			: 2000000000;
	// Expected milliseconds between receiving requests when a client is online
	private static final long CLIENT_REQUEST_RATE_MILLIS = 10000;

	private HttpServer server;
	private final int localPort;
	private final Application app;
	private boolean readableJSON = Application.CONFIG.getBoolean("comms.web-server.readable-json");
	private boolean online;
	private long lastReceivedTime;
	private String homeCallResponseHTML;

	/**
	 * Create the web server
	 * 
	 * @param localPort Port to listen on.
	 * @param app       Reference to the application to look up data.
	 */
	public WebServer(int localPort, Application app) {
		this.localPort = localPort;
		this.app = app;

		homeCallResponseHTML = "<html><head><title>Plane/Sailing Server</title></head><body>"
				+ "<h1>Plane/Sailing Server</h1>" + "<p>Plane/Sailing Server version "
				+ Application.getSoftwareVersion() + " is up and running!</p>"
				+ "<p>Available API endpoints:</p>"
				+ "<ul><li><a href='/first'>/first</a> - First call (includes position history)</li>"
				+ "<li><a href='/update'>/update</a> - Update call (no position history)</li>"
				+ "<li><a href='/telemetry'>/telemetry</a> - Server telemetry information</li></ul>"
				+ "</body></html>";

		try {
			server = HttpServer.create(new InetSocketAddress(localPort), 0);
			server.createContext("/first", new CallHandler(Call.FIRST));
			server.createContext("/update", new CallHandler(Call.UPDATE));
			server.createContext("/telemetry", new CallHandler(Call.TELEMETRY));
			server.createContext("/", new CallHandler(Call.HOME));
			server.setExecutor(null);
		} catch (IOException ex) {
			LOGGER.error("Could not set up web server", ex);
		}
	}

	public void run() {
		server.start();
		online = true;
		LOGGER.info("Started web server on port {}.", localPort);
	}

	public void stop() {
		server.stop(0);
		online = false;
	}

	private class CallHandler implements HttpHandler {
		private final Call call;

		public CallHandler(Call call) {
			this.call = call;
		}

		@Override
		public void handle(HttpExchange t) throws IOException {
			lastReceivedTime = System.currentTimeMillis();
			String response = "";
			String contentType = "application/json";

			try {
				switch (call) {
				case FIRST:
					response = getFirstCallJSON();
					break;
				case UPDATE:
					response = getUpdateCallJSON();
					break;
				case TELEMETRY:
					response = getTelemetryCallJSON();
					break;
				case HOME:
					response = homeCallResponseHTML;
					contentType = "text/html";
					break;
				}

				final Headers headers = t.getResponseHeaders();
				final String requestMethod = t.getRequestMethod().toUpperCase();
				headers.add("Access-Control-Allow-Origin", "*");
				switch (requestMethod) {
				case "GET":
					headers.set("Content-Type", String.format(contentType + "; charset=%s", "UTF8"));
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
			} catch (Exception ex) {
				LOGGER.error("Exception responding to web request", ex);
			} finally {
				t.close();
			}
		}
	}

	/**
	 * Returns JSON corresponding to the "first" API call of the server, which
	 * includes all tracks (including base station, airports and seaports), and the
	 * complete position history for all tracks that have it, so that the client can
	 * populate both the full current picture and the snail trail for tracks. It
	 * also includes the server's current time, so that clients can determine the
	 * age of tracks correctly, and the server version number.
	 */
	public String getFirstCallJSON() {
		Map<String, Object> map = new HashMap<>();
		map.put("time", System.currentTimeMillis());
		map.put("version", Application.getSoftwareVersion());

		Map<String, Map<String, Object>> tracks = new HashMap<>();
		for (Track t : app.getTrackTable().values()) {
			tracks.put(t.getID(), t.getFirstCallData());
		}
		map.put("tracks", tracks);

		JSONObject o = new JSONObject(map);
		return o.toString(readableJSON ? 2 : 0);
	}

	/**
	 * Returns JSON corresponding to the "update" API call of the server, which is
	 * designed to update a picture previously populated by the "first" call. To
	 * save bandwidth, no position history is sent - the client is expected to
	 * append the reported position to its own position history store. This call
	 * also omits the base station, airports and seaports that can't change. It also
	 * includes the server's current time, so that clients can determine the age of
	 * tracks correctly.
	 */
	public String getUpdateCallJSON() {
		Map<String, Object> map = new HashMap<>();
		map.put("time", System.currentTimeMillis());

		Map<String, Map<String, Object>> tracks = new HashMap<>();
		for (Track t : app.getTrackTable().values()) {
			tracks.put(t.getID(), t.getUpdateCallData());
		}
		map.put("tracks", tracks);

		JSONObject o = new JSONObject(map);
		return o.toString(readableJSON ? 2 : 0);
	}

	/**
	 * Get a map of some useful server telemetry
	 */
	private String getTelemetryCallJSON() {
		Map<String, String> map = new HashMap<>();
		map.put("cpuLoad", String.format("%.0f", OS_BEAN.getSystemCpuLoad() * 100.0));
		map.put("memUsed",
				String.format("%.0f", ((OS_BEAN.getCommittedVirtualMemorySize() / (double) TOTAL_MEM_BYTES)) * 100.0));
		map.put("diskUsed", String.format("%.0f",
				(1.0 - (new File(".").getFreeSpace() / (double) new File(".").getTotalSpace())) * 100.0));
		map.put("uptime", String.format("%d", System.currentTimeMillis() - Application.START_TIME));
		map.put("webServerStatus", app.getWebServerStatus().toString());
		map.put("adsbReceiverStatus", app.getADSBReceiverStatus().toString());
		map.put("mlatReceiverStatus", app.getMLATReceiverStatus().toString());
		map.put("aisReceiverStatus", app.getAISReceiverStatus().toString());
		map.put("aprsReceiverStatus", app.getAPRSReceiverStatus().toString());

		JSONObject o = new JSONObject(map);
		return o.toString(readableJSON ? 2 : 0);
	}

	private enum Call {
		FIRST, UPDATE, TELEMETRY, HOME
	}

	public ConnectionStatus getStatus() {
		if (online) {
			if (System.currentTimeMillis() - lastReceivedTime <= CLIENT_REQUEST_RATE_MILLIS * 2) {
				return ConnectionStatus.ACTIVE;
			} else {
				return ConnectionStatus.ONLINE;
			}
		} else {
			return ConnectionStatus.OFFLINE;
		}
	};
}
