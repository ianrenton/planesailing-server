package com.ianrenton.planesailing.data;

import java.util.Map.Entry;

import com.ianrenton.planesailing.app.Application;
import com.ianrenton.planesailing.utils.DataMaps;

public class Aircraft extends Track {
	private static final long serialVersionUID = 1L;
	private static final String DEFAULT_AIRCRAFT_SYMBOL = "SUAPCF----";
	private static final Long DROP_AIR_TRACK_AT_ZERO_ALT_TIME = Application.CONFIG.getLong("timing.drop-air-track-after");
	private static final Long DROP_AIR_TRACK_TIME = Application.CONFIG.getLong("timing.drop-air-track-at-zero-alt-after");
	
	private Double altitude; // feet
	private boolean onGround;
	private Integer squawk;
	private Double verticalRate; // feet per second
	private String category; // e.g. "A1" = light
	private String categoryDescription; // e.g. "Light"
	private String registration; // aka Tail Number
	private String aircraftTypeShort; // e.g. "A320"
	private String aircraftTypeLong; // e.g. "Airbus A320"
	private String operator; // e.g. "Ryanair"

	public Aircraft(String id) {
		super(id);
		setTrackType(TrackType.AIRCRAFT);
		setSymbolCode(DEFAULT_AIRCRAFT_SYMBOL);
		positionHistory.setHistoryLength(60 * 60 * 1000); // 1 hour
		registration = DataMaps.AIRCRAFT_ICAO_HEX_TO_REGISTRATION.getOrDefault(id, null);
		aircraftTypeShort = DataMaps.AIRCRAFT_ICAO_HEX_TO_TYPE.getOrDefault(id, null);
		if (aircraftTypeShort != null) {
			aircraftTypeLong = DataMaps.AIRCRAFT_TYPE_SHORT_TO_LONG.getOrDefault(aircraftTypeShort, null);
		}
	}

	public Double getAltitude() {
		return altitude;
	}

	public Integer getSquawk() {
		return squawk;
	}

	public Double getVerticalRate() {
		return verticalRate;
	}

	public boolean isOnGround() {
		return onGround || (altitude != null && altitude <= 100);
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

	/**
	 * Get the aircraft category (if known), otherwise null.
	 */
	public String getCategory() {
		return category;
	}

	/**
	 * Get the aircraft category description (if known), otherwise null.
	 */
	public String getCategoryDescription() {
		return categoryDescription;
	}

	public void setCategory(String category) {
		this.category = category;

		updateMetadataTime();
	}

	/**
	 * Get the aircraft type (if known), otherwise null.
	 */
	public String getAircraftTypeShort() {
		return aircraftTypeShort;
	}

	/**
	 * Get the aircraft type description (if known), otherwise null.
	 */
	public String getAircraftTypeLong() {
		return aircraftTypeLong;
	}

	/**
	 * Get the aircraft registration, aka "tail number" (if known), otherwise null.
	 */
	public String getRegistration() {
		return registration;
	}

	/**
	 * Get the aircraft operator (if known), otherwise null.
	 */
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
	 * Get the altitude, formatted for display.
	 */
	public String getDisplayAltitude() {
		String ret = "";
		if (altitude != null) {
			ret += "FL" + Math.round(altitude / 100.0);
			if (verticalRate != null) {
				if (verticalRate > 2) {
					ret += "\u25b2";
				} else if (verticalRate < -2) {
					ret += "\u25bc";
				}
			}
		}
		return ret;
	}

	@Override
	public String getDisplayDescription1() {
		if (aircraftTypeLong != null) {
			return aircraftTypeLong.toUpperCase();
		} else if (aircraftTypeShort != null) {
			return aircraftTypeShort.toUpperCase();
		} else if (categoryDescription != null) {
			return "AIRCRAFT (" + categoryDescription.toUpperCase() + ")";
		} else {
			return "AIRCRAFT (UNKNOWN TYPE)";
		}
	}

	@Override
	public String getDisplayDescription2() {
		return (operator != null) ? operator.toUpperCase() : "";
	}
}
