package com.ianrenton.planesailing.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ianrenton.planesailing.app.Application;

public abstract class Track implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final Long DEFAULT_SHOW_ANTICIPATED_TIME = Application.CONFIG.getLong("timing.show-anticipated-after");

	protected String id; // unique ID. ICAO Hex is used for aircraft, MMSI for ships, callsign for APRS
							// tracks. These are all sufficiently different that each track should be
							// able to use this without risk of collision. The same ID is used as the
							// key for the TrackTable.
	protected String callsign;
	protected TrackType trackType;
	protected String symbolCode;
	protected final PositionHistory positionHistory = new PositionHistory();
	protected Double altitude; // feet
	protected Double course; // degrees
	protected Double heading; // degrees
	protected Double speed; // knots
	protected Long metaDataTime = System.currentTimeMillis(); // UTC millis since epoch. Set to current time on track creation.
	protected boolean fixed = false;
	protected boolean createdByConfig = false;

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
	 * Gets the dead reckoned position. If course and speed are unknown, this will
	 * be the same as the result of getPosition(). May be null if position is
	 * unknown.
	 * 
	 * @deprecated Intention is that the front end only polls the server every 10
	 *             seconds, but updates its map & tote every second with a dead
	 *             reckoned position calculated at the front end based on the age of
	 *             the position data.
	 */
	public TimestampedPosition getDRPosition() {
		if (!positionHistory.isEmpty()) {
			TimestampedPosition p = positionHistory.getLatest();
			if (course != null && speed != null) {
				return deadReckonFrom(p, course, speed);
			} else {
				return p;
			}
		} else {
			return null;
		}
	}

	/**
	 * Great Circle calculation for dead reckoning.
	 * 
	 * @param p      Last known position, including timestamp
	 * @param course in degrees
	 * @param speed  in knots
	 * @return
	 */
	private TimestampedPosition deadReckonFrom(TimestampedPosition p, double course, double speed) {
		double startLatitudeRadians = Math.toRadians(p.getLatitude());
		double startLongitudeRadians = Math.toRadians(p.getLongitude());
		double courseRadians = Math.toRadians(course);
		double distMovedMetres = (p.getAge() / 1000.0) * speed * 0.514;
		double distMovedRadians = distMovedMetres / 6371000.0;

		double cosphi1 = Math.cos(startLatitudeRadians);
		double sinphi1 = Math.sin(startLatitudeRadians);
		double cosAz = Math.cos(courseRadians);
		double sinAz = Math.sin(courseRadians);
		double sinc = Math.sin(distMovedRadians);
		double cosc = Math.cos(distMovedRadians);

		double endLatitudeRadians = Math.asin(sinphi1 * cosc + cosphi1 * sinc * cosAz);
		double endLongitudeRadians = Math.atan2(sinc * sinAz, cosphi1 * cosc - sinphi1 * sinc * cosAz) + startLongitudeRadians;

		double endLatitudeDegrees = Math.toDegrees(endLatitudeRadians);
		double endLongitudeDegrees = Math.toDegrees(endLongitudeRadians);

		return new TimestampedPosition(endLatitudeDegrees, endLongitudeDegrees, System.currentTimeMillis());
	}

	public Double getAltitude() {
		return altitude;
	}

	public void setAltitude(double altitude) {
		this.altitude = altitude;
		updateMetadataTime();
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
	 * Gets the age, in milliseconds, of the last position update for this track. If
	 * no position history exists, return null.
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
	 * Is this a fixed track (i.e. it is known to be immobile)? If so it will never
	 * be dropped from the track table and its position history will never be sent.
	 * Note this is slightly different to "createdByConfig" - all createdByConfig
	 * tracks are fixed, but not all fixed tracks are createdByConfig, e.g. AIS
	 * and APRS base stations.
	 */
	public boolean isFixed() {
		return fixed;
	}

	public void setFixed(boolean fixed) {
		this.fixed = fixed;
	}

	/**
	 * Is this a track that was created by config (i.e. base station, airport or
	 * seaport)? If so we only send info about it in the "first" call not in
	 * "update" because it can never be created, destroyed or modified.
	 * @return
	 */
	public boolean isCreatedByConfig() {
		return createdByConfig;
	}

	public void setCreatedByConfig(boolean createdByConfig) {
		this.createdByConfig = createdByConfig;
		if (createdByConfig) {
			this.fixed = true;
		}
	}

	/**
	 * Return true if this track is old and should be dropped from the track table.
	 * By default, this is true if the track is not "fixed", and has no data newer
	 * than the amount of position history it's configured to store. However
	 * subclasses can override this, e.g. to provide different logic for planes at
	 * altitude compared to on the ground.
	 */
	public boolean shouldDrop() {
		return !isFixed() && getPositionHistory().isEmpty() && getMetaDataAge() > getPositionHistory().getHistoryLength();
	}

	/**
	 * Show the "anticipated" version of the symbol?
	 */
	public boolean shouldShowAnticipatedSymbol() {
		return getPositionAge() != null && getPositionAge() > DEFAULT_SHOW_ANTICIPATED_TIME;
	}

	/**
	 * Get a name to be used for display. This will default to ID, or Callsign if
	 * set, but track types should override this with their own concept of "name".
	 */
	public String getDisplayName() {
		if (callsign != null) {
			return callsign;
		}
		return id;
	}

	/**
	 * Get the position, formatted for display.
	 * 
	 * @deprecated Not needed as the client will be dead reckoning its position
	 *             internally so will need to do its own formatting
	 */
	public String getDisplayPosition() {
		if (!positionHistory.isEmpty()) {
			double lat = positionHistory.getLatest().getLatitude();
			double lon = positionHistory.getLatest().getLongitude();
			return (String.format("%07.4f", Math.abs(lat)) + ((lat >= 0) ? 'N' : 'S') + " " + String.format("%08.4f", Math.abs(lon)) + ((lon >= 0) ? 'E' : 'W'));
		} else {
			return "";
		}
	}

	/**
	 * Get the altitude, formatted for display.
	 */
	public String getDisplayAltitude() {
		return (altitude != null) ? String.format("%.0f ft", altitude) : "";
	}

	/**
	 * Get the heading, formatted for display.
	 */
	public String getDisplayHeading() {
		return (heading != null) ? String.format("%03d", heading.intValue()) : "";
	}

	/**
	 * Get the course, formatted for display.
	 */
	public String getDisplayCourse() {
		return (course != null) ? String.format("%03d", course.intValue()) : "";
	}

	/**
	 * Get the speed, formatted for display.
	 */
	public String getDisplaySpeed() {
		return (speed != null) ? String.format("%d", speed.intValue()) + "KTS" : "";
	}

	/**
	 * Get the first line of description for display.
	 */
	public abstract String getDisplayDescription1();

	/**
	 * Get the second line of description for display.
	 */
	public abstract String getDisplayDescription2();

	/**
	 * Get a map of data for this track that will be provided to the client,
	 * including all metadata, the current position, and the position history, used
	 * for the "first" API call.
	 * 
	 * Note that position history is only provided for tracks that are not "fixed",
	 * i.e. if a track is known to be incapable of movement, this structure can
	 * be omitted to save bandwidth.
	 */
	public Map<String, Object> getFirstCallData() {
		Map<String, Object> map = getAllCallData();
		
		if (!fixed) {
			List<Map<String, Object>> posHistory = new ArrayList<>();
			for (TimestampedPosition p : positionHistory) {
				Map<String, Object> m = new HashMap<>();
				m.put("lat", p.getLatitude());
				m.put("lon", p.getLongitude());
				posHistory.add(m);
			}
			map.put("poshistory", posHistory);
		}
		return map;
	}

	/**
	 * Get a map of data for this track that will be provided to the client,
	 * including all metadata and the current position, used for the "update" API
	 * call.
	 */
	public Map<String, Object> getUpdateCallData() {
		return getAllCallData();
	}

	/**
	 * Get a map of metadata for this track that will be provided to the client in
	 * all API calls. This should be enough to generate all the information the
	 * client needs, and shouldn't need overriding in subclasses.
	 */
	private Map<String, Object> getAllCallData() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("name", getDisplayName().toUpperCase());
		map.put("tracktype", getTrackType().toString());
		map.put("symbolcode", getSymbolCode());
		map.put("fixed", isFixed());
		map.put("createdByConfig", isCreatedByConfig());

		TimestampedPosition p = getPosition();
		if (p != null) {
			map.put("lat", p.getLatitude());
			map.put("lon", p.getLongitude());
			map.put("postime", p.getTime());
		}

		map.put("course", getCourse());
		map.put("heading", getHeading());
		map.put("speed", getSpeed());
		map.put("courseText", getDisplayCourse());
		map.put("headingText", getDisplayHeading());
		map.put("speedText", getDisplaySpeed());
		map.put("altitudeText", getDisplayAltitude());
		map.put("desc1", getDisplayDescription1().toUpperCase());
		map.put("desc2", getDisplayDescription2().toUpperCase());
		map.put("datatime", getMetaDataTime());
		return map;
	}
}
