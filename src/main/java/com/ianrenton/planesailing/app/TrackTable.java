package com.ianrenton.planesailing.app;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.data.Track;
import com.thoughtworks.xstream.XStream;

/**
 * Track table
 */
public class TrackTable extends HashMap<String, Track> {

	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = LogManager.getLogger(TrackTable.class);
	private transient final XStream xstream = new XStream();
	private transient final File serializationFile = new File("tracktable.xml");
	private transient final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
	@SuppressWarnings("rawtypes")
	private transient final ScheduledFuture maintenanceTask;
	@SuppressWarnings("rawtypes")
	private transient final ScheduledFuture backupTask;
	
	/**
	 * Create a track table, using data found on disk if present.
	 * Spawns internal threads to run scheduled tasks such as culling old positions,
	 * printing status data, and backing up the track table to disk.
	 */
	public TrackTable() {
		loadFromFile();
		maintenanceTask = scheduledExecutorService.scheduleWithFixedDelay(new MaintenanceTask(), 10, 10, TimeUnit.SECONDS);
		backupTask = scheduledExecutorService.scheduleWithFixedDelay(new BackupTask(), 1, 10, TimeUnit.MINUTES);
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
	 * Load data from XStream serialisation file on disk
	 */
	public void loadFromFile() {
		if (serializationFile.exists()) {
			clear();
			putAll((TrackTable) xstream.fromXML(serializationFile));
			LOGGER.info("Loaded track table from {}", serializationFile.getAbsolutePath());
		} else {
			LOGGER.info("Track table file did not exist in {}, probably first startup.", serializationFile.getAbsolutePath());
		}
	}

	/**
	 * Save data to XStream serialisation file on disk
	 */
	public void saveToFile() {
		try {
			xstream.toXML(this, new FileWriter(serializationFile));
			LOGGER.info("Backed up track table to {}", serializationFile.getAbsolutePath());
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
			LOGGER.info("Seen {} entities.", size());

			if (!isEmpty()) {
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
