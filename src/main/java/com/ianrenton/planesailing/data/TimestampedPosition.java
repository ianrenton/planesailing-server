package com.ianrenton.planesailing.data;

import java.io.Serializable;

public class TimestampedPosition implements Comparable<TimestampedPosition>, Serializable {

	private static final long serialVersionUID = 1L;
	private final double latitude; // degrees
	private final double longitude; // degrees
	private final long time; // UTC millis since epoch

	public TimestampedPosition(double latitude, double longitude, long time) {
		super();
		this.latitude = latitude;
		this.longitude = longitude;
		this.time = time;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	/**
	 * Get the time, in UTC milliseconds since UNIX epoch, of this position update.
	 */
	public long getTime() {
		return time;
	}

	/**
	 * Get the age in milliseconds of this position update.
	 */
	public long getAge() {
		return System.currentTimeMillis() - time;
	}

	/**
	 * Sort by time.
	 */
	public int compareTo(TimestampedPosition o) {
		return Long.compare(this.time, o.time);
	}
}
