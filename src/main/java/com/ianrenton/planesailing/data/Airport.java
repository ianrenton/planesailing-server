package com.ianrenton.planesailing.data;

public class Airport extends Track {
	private static final long serialVersionUID = 1L;
	private static final String AIRPORT_SYMBOL = "SFGPIBA---H";	
	private static int airportCount;
	
	private final String name;
	private final String icaoCode;
	
	public Airport(String name, double lat, double lon, String icaoCode) {
		super("AIRPORT-" + airportCount++);
		this.name = name;
		this.icaoCode = icaoCode;
		addPosition(lat, lon);
		setCreatedByConfig(true);
		setTrackType(TrackType.AIRPORT);
		setSymbolCode(AIRPORT_SYMBOL);
	}

	public String getName() {
		return name;
	}

	public String getIcaoCode() {
		return icaoCode;
	}

	@Override
	public String getTypeDescription() {
		return "AIRPORT";
	}
	
	@Override
	public String getDisplayName() {
		return name;
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
