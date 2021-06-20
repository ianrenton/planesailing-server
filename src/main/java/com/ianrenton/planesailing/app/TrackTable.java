package com.ianrenton.planesailing.app;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ianrenton.planesailing.data.Track;

/**
 * Track table
 */
public class TrackTable extends HashMap<String, Track> {

	private static final Logger LOGGER = LogManager.getLogger(TrackTable.class);
	private static ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
	private ScheduledFuture scheduledTask;
	
	/**
	 * Create a track table. Spawns an internal thread to run scheduled tasks
	 * such as culling old positions, and printing status data.
	 */
	public TrackTable() {
		scheduledTask = scheduledExecutorService.scheduleWithFixedDelay(new ScheduledTask(), 10, 10, TimeUnit.SECONDS);
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
	
	public void shutdown() {
		scheduledTask.cancel(true);
	}

	/**
	 * Scheduled task that runs while the track table is running.
	 */
	private class ScheduledTask implements Runnable {

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
				LOGGER.info("ID                  Age (ms)");
				LOGGER.info("------------------------------------");
				for (Track e : values()) {
					LOGGER.info("{}{}", String.format("%-20s", e.getDisplayName()), e.getTimeSinceLastUpdate() != null ? e.getTimeSinceLastUpdate() : "---");
				}
			}
			
			LOGGER.info("");
		}

	}
}
