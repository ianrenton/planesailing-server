package com.ianrenton.planesailing.data;

public class Seaport extends Track {
	private static final long serialVersionUID = 1L;
	private static final String SEAPORT_SYMBOL = "SFGPIBN---H-";
	private static int seaportCount;
	
	private final String name;
	
	public Seaport(String name, double lat, double lon) {
		super("SEAPORT-" + seaportCount++);
		this.name = name;
		addPosition(lat, lon);
		setCreatedByConfig(true);
		setTrackType(TrackType.SEAPORT);
		setSymbolCode(SEAPORT_SYMBOL);
	}

	public String getName() {
		return name;
	}
	
	@Override
	public String getDisplayName() {
		return name;
	}

	@Override
	public String getDisplayDescription1() {
		return "";
	}

	@Override
	public String getDisplayDescription2() {
		return "";
	}
}
