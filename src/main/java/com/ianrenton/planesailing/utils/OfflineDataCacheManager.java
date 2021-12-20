package com.ianrenton.planesailing.utils;

import com.ianrenton.planesailing.app.TrackTable;
import com.ianrenton.planesailing.data.Track;

/**
 * Utility to manage the cache file offline.
 */
public class OfflineDataCacheManager {

	public static void main(String[] args) {
		TrackTable tt = new TrackTable();
		tt.loadFromFile();
//		Track r = tt.remove("992356052");
//		System.out.println("Removed " + r.getDisplayName());
//		r = tt.remove("859531965");
//		System.out.println("Removed " + r.getDisplayName());
		tt.saveToFile();
	}
}
