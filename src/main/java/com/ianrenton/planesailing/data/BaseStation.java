package com.ianrenton.planesailing.data;

public class BaseStation extends Track {
	private static final String BASE_STATION_SYMBOL = "SFGPUUS-----";
	private static int baseStationCount;
	private final String name;
	private final String softwareVersion;
	
	public BaseStation(String name, double lat, double lon, String softwareVersion) {
		super("BASESTATION-" + baseStationCount++);
		this.name = name;
		this.softwareVersion = softwareVersion;
		addPosition(lat, lon);
		setFixed(true);
		setTrackType(TrackType.BASE_STATION);
		setSymbolCode(BASE_STATION_SYMBOL);
	}

	public String getName() {
		return name;
	}

	public String getSoftwareVersion() {
		return softwareVersion;
	}
	
	@Override
	public String getDisplayName() {
		return name;
	}

	@Override
	public String getDisplayDescription1() {
		return softwareVersion;
	}

	@Override
	public String getDisplayDescription2() {
		return "";
	}
}
