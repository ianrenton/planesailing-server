package com.ianrenton.planesailing.data;

public class APRSTrack extends Track {
	private static final String DEFAULT_APRS_SYMBOL = "SFGPU-------";
	
	public APRSTrack(String id) {
		super(id);
		setSymbolCode(DEFAULT_APRS_SYMBOL);
		positionHistory.setHistoryLength(60 * 60 * 1000); // 1 hour
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
