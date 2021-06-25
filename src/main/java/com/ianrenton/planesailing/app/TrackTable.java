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

import com.ianrenton.planesailing.data.Track;
import com.ianrenton.planesailing.data.TrackType;

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
	 * Load data from serialisation file on disk
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
	 * Save data to serialisation file on disk
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
