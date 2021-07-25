package com.ianrenton.planesailing.data;

public class BaseStation extends Track {
	private static final long serialVersionUID = 1L;
	private static final String BASE_STATION_SYMBOL = "SFGPUUSR----";
	private static int baseStationCount;
	
	private final String name;
	
	public BaseStation(String name, double lat, double lon) {
		super("BASESTATION-" + baseStationCount++);
		this.name = name;
		addPosition(lat, lon);
		setCreatedByConfig(true);
		setTrackType(TrackType.BASE_STATION);
		setSymbolCode(BASE_STATION_SYMBOL);
	}

	public String getName() {
		return name;
	}
	
	@Override
	public String getDisplayName() {
		return name;
	}

	@Override
	public String getTypeDescription() {
		return "BASE STATION";
	}

	@Override
	public String getDisplayInfo1() {
		return "";
	}

	@Override
	public String getDisplayInfo2() {
		return "";
	}
}
