package com.ianrenton.planesailing.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensky.libadsb.Position;

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
public class TrackTable extends ConcurrentHashMap<String, Track> {
	
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = LogManager.getLogger(TrackTable.class);
	
	private transient final File serializationFile = new File("track_data_store.dat");

	public final Map<Integer, String> aisNameCache = new ConcurrentHashMap<>();

	private Position baseStationPositionForADSB = null;
	
	private transient final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2, new BasicThreadFactory.Builder().namingPattern("Track Table Processing Thread %d").build());
	@SuppressWarnings("rawtypes")
	private transient ScheduledFuture maintenanceTask;
	@SuppressWarnings("rawtypes")
	private transient ScheduledFuture backupTask;

	private transient boolean printTrackTableToStdOut = Application.CONFIG.getBoolean("print-track-table-to-stdout");

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
	
	private long countTracksOfType(TrackType t) {
		return values().stream().filter(track -> track.getTrackType() == t).count();
	}

	/**
	 * Delete position data older than the threshold for all non-fixed tracks.
	 * For fixed tracks, just leave the single most recent position (regardless
	 * of age) since it won't have moved anyway.
	 */
	private void cullOldPositionData() {
		for (Track t : values()) {
			try {
				if (!t.isFixed()) {
					t.getPositionHistory().cull();
				} else {
					t.getPositionHistory().keepOnlyLatest();
				}
			} catch (Exception ex) {
				LOGGER.error("Caught exception when culling old position data for {}, continuing...", t.getDisplayName(), ex);
			}
		}
	}

	/**
	 * Drop any tracks that have no current data
	 */
	private void dropExpiredTracks() {
		for (Iterator<Entry<String, Track>> it = entrySet().iterator(); it.hasNext();) {
            Track t = it.next().getValue();
			try {
	            if (t.shouldDrop()) {
					it.remove();
				}
			} catch (Exception ex) {
				LOGGER.error("Caught exception when checking if {} should be dropped, continuing...", t.getDisplayName(), ex);
			}
        }
	}

	/**
	 * Load data from serialisation file on disk.
	 */
	public void loadFromFile() {
		loadFromFile(serializationFile);
	}

	/**
	 * Load data from serialisation file on disk.
	 */
	public void loadFromFile(File file) {
		if (file.exists()) {
			try {
				clear();
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
				TrackTable newTT = (TrackTable) ois.readObject();
				ois.close();
				copy(newTT);
				LOGGER.info("Loaded {} tracks from track data store at {}", size(), file.getAbsolutePath());
				LOGGER.info("Loaded {} AIS names from track data store", aisNameCache.size());
				
				// Perform post-load tasks on each loaded track
				for (Track t : values()) {
					t.performPostLoadTasks();
				}
			} catch (SerializationException | IOException | ClassNotFoundException | ClassCastException ex) {
				LOGGER.error("Exception loading track data store. Deleting the file so this doesn't reoccur.", ex);
				file.delete();
			}
		} else {
			LOGGER.info("Track table file did not exist in {}, probably first startup.", file.getAbsolutePath());
		}
	}

	/**
	 * Save data to serialisation file on disk.
	 */
	public void saveToFile() {
		saveToFile(serializationFile);
	}

	/**
	 * Save data to serialisation file on disk.
	 */
	public void saveToFile(File file) {
		try {
			LOGGER.info("Saving to track data store...");
			file.delete();
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
			// Deep copy to avoid concurrent modification problems when the track table
			// contents are modified during save. While the top level object uses
			// ConcurrentHashMap to avoid this problem, we should not be forcing that
			// implementation detail on the whole tree of objects inside the table.
			TrackTable copy = SerializationUtils.clone(this);
			oos.writeObject(copy);
			oos.flush();
			oos.close();
			LOGGER.info("Saved {} tracks to track data store at {}", size(), file.getAbsolutePath());
			LOGGER.info("Saved {} AIS names to track data store", aisNameCache.size());
		} catch (IOException e) {
			LOGGER.error("Could not save track table to {}", file.getAbsolutePath(), e);
		}
	}
	
	/**
	 * Copy another track table into this one
	 */
	public void copy(TrackTable tt) {
		this.putAll(tt);
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
			BaseStation bs = new BaseStation((String) data.get("name"), (Double) data.get("lat"), (Double) data.get("lon"));
			put(bs.getID(), bs);
			// Special case - store the first base station's position as the ADS-B decoder
			// will want that. Note Position takes longitude first!
			baseStationPositionForADSB = new Position((Double) data.get("lon"), (Double) data.get("lat"), ((Number) data.get("alt")).doubleValue());
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
	
	public Map<Integer, String> getAISNameCache() {
		return aisNameCache;
	}

	public Position getBaseStationPositionForADSB() {
		return baseStationPositionForADSB;
	}

	/**
	 * Print some debug data
	 */
	public void printStatusData() {
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
						String.format("%-20.20s", e.getDisplayInfo1()),
						String.format("%-20.20s", e.getDisplayInfo2()),
						e.getTimeSinceLastUpdate() != null ? String.format("%-6.6s", e.getTimeSinceLastUpdate()) : "------");
			}
			LOGGER.info("----------------------------------------------------------------------------------");
		}
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
			try {
				printStatusData();
				cullOldPositionData();
				dropExpiredTracks();
			} catch (Throwable t) {
				LOGGER.error("Caught exception in maintenance task, continuing...", t);
			}
		}
	}

	/**
	 * Scheduled backup task that runs while the track table is running.
	 */
	private class BackupTask implements Runnable {

		@Override
		public void run() {
			try {
				saveToFile();
			} catch (Throwable t) {
				LOGGER.error("Caught exception in backup task, continuing...", t);
			}
		}
	}
}
