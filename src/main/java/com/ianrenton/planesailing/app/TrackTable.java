package com.ianrenton.planesailing.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SerializationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import com.ianrenton.planesailing.data.Airport;
import com.ianrenton.planesailing.data.BaseStation;
import com.ianrenton.planesailing.data.Seaport;
import com.ianrenton.planesailing.data.Track;
import com.ianrenton.planesailing.data.TrackType;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigValue;

/**
 * Track table
 */
public class TrackTable extends HashMap<String, Track> {
	
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = LogManager.getLogger(TrackTable.class);
	
	private transient final File serializationFile = new File("track_data_store.dat");
	
	private transient final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
	@SuppressWarnings("rawtypes")
	private transient ScheduledFuture maintenanceTask;
	@SuppressWarnings("rawtypes")
	private transient ScheduledFuture backupTask;

	private transient boolean printTrackTableToStdOut = Application.CONFIG.getBoolean("print-track-table-to-stdout");
	private transient boolean readableJSON = Application.CONFIG.getBoolean("comms.web-server.readable-json");

	/**
	 * Set up the track table, using data found on disk if present. Spawns internal
	 * threads to run scheduled tasks such as culling old positions, printing status
	 * data, and backing up the track table to disk.
	 * 
	 * This must be called before using the track table, unless creating one for
	 * unit tests.
	 */
	public void initialise() {
		// Load data from serialised track data store, and immediately delete
		// anything too old to survive
		loadFromFile();
		cullOldPositionData();
		dropExpiredTracks();
	
		// Set up tasks to run in the background
		maintenanceTask = scheduledExecutorService.scheduleWithFixedDelay(new MaintenanceTask(), 10, 10, TimeUnit.SECONDS);
		backupTask = scheduledExecutorService.scheduleWithFixedDelay(new BackupTask(), 10, 600, TimeUnit.SECONDS);
	}
	
	/**
	 * Returns JSON corresponding to the "first" API call of the server, which
	 * includes all position history.
	 */
	public String getFirstCallJSON() {
		Map<String, Object> map = new HashMap<>();
		map.put("time", System.currentTimeMillis());
		
		Map<String, Map<String, Object>> tracks = new HashMap<>();
		for (Track t : values()) {
			tracks.put(t.getID(), t.getFirstCallData());
		}
		map.put("tracks", tracks);
		
		JSONObject o = new JSONObject(map);
		return o.toString(readableJSON ? 2 : 0);
	}
	
	/**
	 * Returns JSON corresponding to the "update" API call of the server, which
	 * includes all metadata but only the latest position and timestamp.
	 */
	public String getUpdateCallJSON() {
		Map<String, Object> map = new HashMap<>();
		map.put("time", System.currentTimeMillis());
		
		Map<String, Map<String, Object>> tracks = new HashMap<>();
		for (Track t : values()) {
			tracks.put(t.getID(), t.getUpdateCallData());
		}
		map.put("tracks", tracks);
		
		JSONObject o = new JSONObject(map);
		return o.toString(readableJSON ? 2 : 0);
	}
	
	private long countTracksOfType(TrackType t) {
		return values().stream().filter(track -> track.getTrackType() == t).count();
	}

	/**
	 * Delete position data older than the threshold for all tracks.
	 */
	private void cullOldPositionData() {
		for (Track t : values()) {
			if (!t.isFixed()) {
				t.getPositionHistory().cull();
			}
		}
	}

	/**
	 * Drop any tracks that have no current data
	 */
	private void dropExpiredTracks() {
		values().removeIf(t -> t.shouldDrop());
	}

	/**
	 * Load data from serialisation file on disk.
	 */
	public void loadFromFile() {
		if (serializationFile.exists()) {
			try {
				clear();
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(serializationFile));
				@SuppressWarnings("unchecked")
				Map<String, Track> newTT = (HashMap<String, Track>) ois.readObject();
				ois.close();
				putAll(newTT);
				LOGGER.info("Loaded {} tracks from track data store at {}", size(), serializationFile.getAbsolutePath());
			} catch (SerializationException | IOException | ClassNotFoundException ex) {
				LOGGER.error("Exception loading track data store", ex);
			}
		} else {
			LOGGER.info("Track table file did not exist in {}, probably first startup.", serializationFile.getAbsolutePath());
		}
	}

	/**
	 * Save data to serialisation file on disk.
	 */
	public void saveToFile() {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(serializationFile));
			oos.writeObject(this);
			oos.flush();
			oos.close();
			LOGGER.info("Saved {} tracks to track data store at {}", size(), serializationFile.getAbsolutePath());
		} catch (IOException e) {
			LOGGER.error("Could not save track table to {}", serializationFile.getAbsolutePath(), e);
		}
	}

	/**
	 * Read the "custom tracks" (base station, airports and seaports) from the config file
	 * and populate the track table.
	 */
	@SuppressWarnings("unchecked")
	public void loadCustomTracksFromConfig() {
		// First, remove any existing base stations, airports and seaports from the track table.
		// We are loading a new set from config so we don't want to duplicate any old ones.
		values().removeIf(t -> t.getTrackType() == TrackType.BASE_STATION
				|| t.getTrackType() == TrackType.AIRPORT
				|| t.getTrackType() == TrackType.SEAPORT);
		
		// Now load.
		ConfigList baseStationConfigs = Application.CONFIG.getList("custom-tracks.base-stations");
		for (ConfigValue c : baseStationConfigs) {
			Map<String, Object> data = (Map<String, Object>) c.unwrapped();
			BaseStation bs = new BaseStation((String) data.get("name"), (Double) data.get("lat"), (Double) data.get("lon"), (String) data.get("software-version"));
			put(bs.getID(), bs);
		}
		LOGGER.info("Loaded {} base stations from config file", baseStationConfigs.size());

		ConfigList airportConfigs = Application.CONFIG.getList("custom-tracks.airports");
		for (ConfigValue c : airportConfigs) {
			Map<String, Object> data = (Map<String, Object>) c.unwrapped();
			Airport ap = new Airport((String) data.get("name"), (Double) data.get("lat"), (Double) data.get("lon"), (String) data.get("icao-code"));
			put(ap.getID(), ap);
		}
		LOGGER.info("Loaded {} airports from config file", airportConfigs.size());

		ConfigList seaportConfigs = Application.CONFIG.getList("custom-tracks.seaports");
		for (ConfigValue c : seaportConfigs) {
			Map<String, Object> data = (Map<String, Object>) c.unwrapped();
			Seaport sp = new Seaport((String) data.get("name"), (Double) data.get("lat"), (Double) data.get("lon"));
			put(sp.getID(), sp);
		}
		LOGGER.info("Loaded {} seaports from config file", seaportConfigs.size());
	}

	/**
	 * Stop internal threads and prepare for shutdown.
	 */
	public void shutdown() {
		maintenanceTask.cancel(true);
		backupTask.cancel(true);
		saveToFile();
	}

	/**
	 * Scheduled maintenance task that runs while the track table is running.
	 */
	private class MaintenanceTask implements Runnable {

		@Override
		public void run() {
			printStatusData();
			cullOldPositionData();
			dropExpiredTracks();
		}

		/**
		 * Print some debug data
		 */
		private void printStatusData() {
			StringBuilder summary = new StringBuilder();
			for (TrackType t : TrackType.values()) {
				long count = countTracksOfType(t);
				if (count > 0) {
					summary.append(count).append(" ").append(t).append("   ");
				}
			}
			LOGGER.info("Track table contains: {}", summary);

			if (printTrackTableToStdOut && !isEmpty()) {
				LOGGER.info("----------------------------------------------------------------------------------");
				LOGGER.info("Name                 Type       Description                               Age (ms)");
				LOGGER.info("----------------------------------------------------------------------------------");
				for (Track e : values()) {
					LOGGER.info("{} {} {} {} {}",
							String.format("%-20.20s", e.getDisplayName()),
							String.format("%-10.10s", e.getTrackType()),
							String.format("%-20.20s", e.getDisplayDescription1()),
							String.format("%-20.20s", e.getDisplayDescription2()),
							e.getTimeSinceLastUpdate() != null ? String.format("%-6.6s", e.getTimeSinceLastUpdate()) : "------");
				}
				LOGGER.info("----------------------------------------------------------------------------------");
			}
		}
	}

	/**
	 * Scheduled backup task that runs while the track table is running.
	 */
	private class BackupTask implements Runnable {

		@Override
		public void run() {
			saveToFile();
		}
	}
}
