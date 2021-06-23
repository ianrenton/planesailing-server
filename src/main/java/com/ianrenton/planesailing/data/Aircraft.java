package com.ianrenton.planesailing.data;

import java.util.Map.Entry;

import com.ianrenton.planesailing.utils.DataMaps;

public class Aircraft extends Track {
	private static final long DROP_AIR_TRACK_TIME = 300000; // 5 min
	private static final long DROP_AIR_TRACK_AT_ZERO_ALT_TIME = 10000; // Drop tracks at zero altitude sooner because they've likely landed, dead reckoning far past the airport runway looks weird
	private static final String DEFAULT_AIRCRAFT_SYMBOL = "SUAPCF----";
	private double altitude; // feet
	private boolean onGround;
	private int squawk;
	private double verticalRate; // feet per second
	private String category; // e.g. "A1" = light
	private String aircraftType; // e.g. "A320"
	private String operator; // e.g. "Ryanair"

	public Aircraft(String id) {
		super(id);
		setTrackType(TrackType.AIRCRAFT);
		setSymbolCode(DEFAULT_AIRCRAFT_SYMBOL);
		positionHistory.setHistoryLength(60 * 60 * 1000); // 1 hour
	}

	public double getAltitude() {
		return altitude;
	}

	public int getSquawk() {
		return squawk;
	}

	public double getVerticalRate() {
		return verticalRate;
	}

	public boolean isOnGround() {
		return onGround || altitude <= 100;
	}
	
	@Override
	public void setCallsign(String callsign) {
		super.setCallsign(callsign);
		
		// Set the right symbol code for the callsign if known
		for (Entry<String, String> e : DataMaps.AIRCRAFT_AIRLINE_CODE_TO_SYMBOL.entrySet()) {
			if (callsign.startsWith(e.getKey())) {
				setSymbolCode(e.getValue());
				break;
			}
		}
		
		// Set the right operator for the callsign if known
		for (Entry<String, String> e : DataMaps.AIRCRAFT_AIRLINE_CODE_TO_OPERATOR.entrySet()) {
			if (callsign.startsWith(e.getKey())) {
				setOperator(e.getValue());
				break;
			}
		}
	}

	public void setAltitude(double altitude) {
		this.altitude = altitude;
		updateMetadataTime();
	}

	public void setOnGround(boolean onGround) {
		this.onGround = onGround;
		updateMetadataTime();
	}

	public void setSquawk(int squawk) {
		this.squawk = squawk;
		updateMetadataTime();
	}

	public void setVerticalRate(double verticalRate) {
		this.verticalRate = verticalRate;
		updateMetadataTime();
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
		
		updateMetadataTime();
	}

	public String getAircraftType() {
		return aircraftType;
	}

	public void setAircraftType(String aircraftType) {
		this.aircraftType = aircraftType;
		
		updateMetadataTime();
	}

	public String getOperator() {
		return operator;
	}

	public void setOperator(String operator) {
		this.operator = operator;
		
		updateMetadataTime();
	}
	
	public boolean shouldDrop() {
		if (isOnGround()) {
			return getTimeSinceLastUpdate() > DROP_AIR_TRACK_AT_ZERO_ALT_TIME;
		} else {
			return getTimeSinceLastUpdate() > DROP_AIR_TRACK_TIME;
		}
	}
	
	@Override
	public String getDisplayName() {
		if (callsign != null) {
			return callsign;
		}
		return "ICAO " + id;
	}
	
	/**
	 * Get registration (tail number) if known, otherwise null.
	 */
	public String getRegistration() {
		return DataMaps.AIRCRAFT_ICAO_HEX_TO_REGISTRATION.getOrDefault(id, null);
	}
	
	/**
	 * Get airframe type (short version) if known, otherwise null.
	 */
	public String getTypeShort() {
		return DataMaps.AIRCRAFT_ICAO_HEX_TO_TYPE.getOrDefault(id, null);
	}
	
	/**
	 * Get airframe type (long version) if known, otherwise null.
	 */
	public String getTypeLong() {
		if (getTypeShort() != null) {
			return DataMaps.AIRCRAFT_TYPE_SHORT_TO_LONG.getOrDefault(getTypeShort(), null);
		} else {
			return null;
		}
	}
}
