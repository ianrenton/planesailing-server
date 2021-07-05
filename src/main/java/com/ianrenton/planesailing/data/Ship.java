package com.ianrenton.planesailing.data;

import java.util.Map.Entry;

import com.ianrenton.planesailing.app.Application;
import com.ianrenton.planesailing.utils.DataMaps;

import dk.tbsalling.aismessages.ais.messages.types.NavigationStatus;
import dk.tbsalling.aismessages.ais.messages.types.ShipType;

public class Ship extends Track {
	private static final long serialVersionUID = 1L;
	private static final String DEFAULT_SHIP_SYMBOL = "SUSP------";
	private static final String SHORE_STATION_SYMBOL = "SUGPUUS-----";
	private static final Long DROP_STATIC_SHIP_TRACK_TIME = Application.CONFIG.getLong("timing.drop-ship-track-static-after");
	private static final Long DROP_MOVING_SHIP_TRACK_TIME = Application.CONFIG.getLong("timing.drop-ship-track-moving-after");
	
	private final int mmsi;
	private String name;
	private ShipType shipType = ShipType.NotAvailable;
	private String shipTypeDescription = null;
	private boolean shoreStation = false;
	private NavigationStatus navStatus = NavigationStatus.Undefined;
	private String navStatusDescription = null;
	private String destination = null;

	public Ship(int mmsi) {
		super(String.valueOf(mmsi));
		this.mmsi = mmsi;
		setSymbolCode(DEFAULT_SHIP_SYMBOL);
		positionHistory.setHistoryLength(24 * 60 * 60 * 1000); // 24 hours
	}

	public int getMmsi() {
		return mmsi;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
		updateMetadataTime();
	}

	public ShipType getShipType() {
		return shipType;
	}

	public String getShipTypeDescription() {
		return shipTypeDescription;
	}

	public void setShipType(ShipType shipType) {
		this.shipType = shipType;
		
		// Set the right symbol for the ship type if known
		for (Entry<String, String> e : DataMaps.SHIP_TYPE_TO_SYMBOL.entrySet()) {
			if (shipType.getCode().equals(Integer.valueOf(e.getKey()))) {
				setSymbolCode(e.getValue());
				break;
			}
		}
		
		// Set the right description for the ship type if known
		for (Entry<String, String> e : DataMaps.SHIP_TYPE_TO_DESCRIPTION.entrySet()) {
			if (shipType.getCode().equals(Integer.valueOf(e.getKey()))) {
				shipTypeDescription = e.getValue();
				break;
			}
		}
		
		updateMetadataTime();
	}

	public boolean isShoreStation() {
		return shoreStation;
	}

	public void setShoreStation(boolean shoreStation) {
		this.shoreStation = shoreStation;
		if (shoreStation) {
			setSymbolCode(SHORE_STATION_SYMBOL);
		}
		updateMetadataTime();
	}

	public NavigationStatus getNavStatus() {
		return navStatus;
	}

	public void setNavStatus(NavigationStatus navStatus) {
		this.navStatus = navStatus;
		
		// Set the right description for the nav status if known
		for (Entry<String, String> e : DataMaps.SHIP_NAV_STATUS_TO_DESCRIPTION.entrySet()) {
			if (navStatus.getCode().equals(Integer.valueOf(e.getKey()))) {
				navStatusDescription = e.getValue();
				break;
			}
		}
				
		updateMetadataTime();
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
		updateMetadataTime();
	}
	
	public boolean shouldDrop() {
		if (getSpeed() == null || getSpeed() < 1.0) {
			return getTimeSinceLastUpdate() > DROP_STATIC_SHIP_TRACK_TIME;
		} else {
			return getTimeSinceLastUpdate() > DROP_MOVING_SHIP_TRACK_TIME;
		}
	}
	
	@Override
	public String getDisplayName() {
		if (name != null) {
			return name;
		}
		return "MMSI " + mmsi;
	}

	@Override
	public String getDisplayDescription1() {
		if (shoreStation) {
			return "AIS SHORE STATION";
		} else if (shipTypeDescription != null) {
			return shipTypeDescription.toUpperCase();
		} else {
			return "SHIP (UNKNOWN TYPE)";
		}
	}

	@Override
	public String getDisplayDescription2() {
		String ret = "";
		if (navStatusDescription != null && !navStatusDescription.equals("Undefined")) {
			ret += navStatusDescription.toUpperCase();
		}
		if (destination != null) {
			if (!ret.isEmpty()) {
				ret += " - ";
			}
			ret += destination.toUpperCase();
		}
		return ret;
	}
}
