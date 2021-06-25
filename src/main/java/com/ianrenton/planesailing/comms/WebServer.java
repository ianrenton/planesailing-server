package com.ianrenton.planesailing.comms;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.app.TrackTable;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * The HTTP server that will provide data to the Plane/Sailing client over the web.
 */
public class WebServer {
	private static final Logger LOGGER = LogManager.getLogger(WebServer.class);
	
	private HttpServer server;
	private final int localPort;
	private final TrackTable trackTable;

	/**
	 * Create the web server
	 * @param localPort Port to listen on.
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
            
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    
    private enum Call {FIRST, UPDATE};
}
