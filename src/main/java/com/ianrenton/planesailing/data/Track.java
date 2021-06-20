package com.ianrenton.planesailing.data;

public abstract class Track {
	private static final long SHOW_ANTICIPATED_TIME = 60000; // 60 seconds
	
	protected String id; // unique ID. ICAO Hex is used for aircraft, MMSI for ships, callsign for APRS
							// tracks. These are all sufficiently different that each track should be
							// able to use this without risk of collision. The same ID is used as the
							// key for the TrackTable.
	protected String callsign;
	protected TrackType trackType;
	protected String symbolCode;
	protected final PositionHistory positionHistory = new PositionHistory();
	protected Double course; // degrees
	protected Double heading; // degrees
	protected Double speed; // knots
	protected Long metaDataTime = System.currentTimeMillis(); // UTC millis since epoch. Set to current time on track creation.
	protected boolean fixed = false;

	public Track(String id) {
		this.id = id;
	}

	public String getID() {
		return id;
	}

	public String getCallsign() {
		return callsign;
	}

	public void setCallsign(String callsign) {
		this.callsign = callsign;
		updateMetadataTime();
	}

	public TrackType getTrackType() {
		return trackType;
	}

	public void setTrackType(TrackType trackType) {
		this.trackType = trackType;
	}

	public String getSymbolCode() {
		return symbolCode;
	}

	public void setSymbolCode(String symbolCode) {
		this.symbolCode = symbolCode;
	}
	
	/**
	 * Gets the latest known position. May be null if position is unknown.
	 */
	public TimestampedPosition getPosition() {
		if (!positionHistory.isEmpty()) {
			return positionHistory.getLatest();
		} else {
			return null;
		}
	}
	
	/**
	 * Gets the dead reckoned position. If course and speed are unknown, this
	 * will be the same as the result of getPosition(). May be null if position is unknown.
	 */
	public TimestampedPosition getDRLatitude() {
		if (!positionHistory.isEmpty()) {
			TimestampedPosition p = positionHistory.getLatest();
			if (course != null && speed != null) {
				// todo
			} else {
				return p;
			}
		} else {
			return null;
		}
	}
	
	/**
	 * Gets the dead reckoned longitude. May be null if position is unknown.
	 */
	public Double getDRLongitude() {
		if (!positionHistory.isEmpty()) {
			return positionHistory.getLatest().getLongitude();
		} else {
			return null;
		}
	}

	/**
	 * Get the course in degrees. May be null if speed is unknown.
	 */
	public Double getCourse() {
		return course;
	}

	public void setCourse(double course) {
		this.course = course;
		updateMetadataTime();
	}

	/**
	 * Get the heading in degrees. May be null if speed is unknown.
	 */
	public Double getHeading() {
		return heading;
	}

	public void setHeading(double heading) {
		this.heading = heading;
		updateMetadataTime();
	}

	/**
	 * Get the speed in knots. May be null if speed is unknown.
	 */
	public Double getSpeed() {
		return speed;
	}

	public void setSpeed(double speed) {
		this.speed = speed;
		updateMetadataTime();
	}

	public PositionHistory getPositionHistory() {
		return positionHistory;
	}

	public void addPosition(double latitude, double longitude) {
		positionHistory.add(new TimestampedPosition(latitude, longitude, System.currentTimeMillis()));
		updateMetadataTime();
	}

	/**
	 * Gets the age, in milliseconds, of the last position update for this track.
	 * If no position history exists, return null.
	 */
	public Long getPositionAge() {
		if (!positionHistory.isEmpty()) {
			return positionHistory.getLatest().getAge();
		} else {
			return null;
		}
	}

	protected void updateMetadataTime() {
		metaDataTime = System.currentTimeMillis();
	}

	public Long getMetaDataTime() {
		return metaDataTime;
	}

	/**
	 * Gets the age, in milliseconds, of the last metadata update for this track.
	 */
	public Long getMetaDataAge() {
		return System.currentTimeMillis() - metaDataTime;
	}

	/**
	 * Gets the age, in milliseconds, of the last position *or* metadata update for
	 * this track.
	 */
	public Long getTimeSinceLastUpdate() {
		if (getMetaDataAge() != null && getPositionAge() != null) {
			return Math.min(getMetaDataAge(), getPositionAge());
		} else if (getMetaDataAge() != null) {
			return getMetaDataAge();
		} else {
			return getPositionAge();
		}
	}

	/**
	 * Is this a fixed track (e.g. the base station, airport or sea port) that should
	 * never time out and be deleted?
	 */
	public boolean isFixed() {
		return fixed;
	}

	public void setFixed(boolean fixed) {
		this.fixed = fixed;
	}
	
	/**
	 * Return true if this track is old and should be dropped from the track table.
	 * By default, this is true if the track is not "fixed", and has no data newer
	 * than the amount of position history it's configured to store. However subclasses
	 * can override this, e.g. to provide different logic for planes at altitude
	 * compared to on the ground.
	 */
	public boolean shouldDrop() {
		return !isFixed() && getPositionHistory().isEmpty() && getMetaDataAge() > getPositionHistory().getHistoryLength();
	}
	
	/**
	 * Show the "anticipated" version of the symbol?
	 */
	public boolean shouldShowAnticipatedSymbol() {
		return getPositionAge() != null && getPositionAge() > SHOW_ANTICIPATED_TIME;
	}
	
	/**
	 * Get a name to be used for display. This will default to ID, or Callsign if set,
	 * but track types should override this with their own concept of "name".
	 */
	public String getDisplayName() {
		if (callsign != null) {
			return callsign;
		}
		return id;
	}
}
