package com.ianrenton.planesailing.data;

import java.util.Map.Entry;

import com.ianrenton.planesailing.app.Application;
import com.ianrenton.planesailing.utils.DataMaps;

public class APRSTrack extends Track {
	private static final long serialVersionUID = 1L;
	private static final String DEFAULT_APRS_SYMBOL = "SUGPEVC-----";
	private static final Long DROP_STATIC_APRS_TRACK_TIME = Application.CONFIG.getLong("timing.drop-aprs-track-static-after");
	private static final Long DROP_MOVING_APRS_TRACK_TIME = Application.CONFIG.getLong("timing.drop-aprs-track-moving-after");

	private String packetDestCall = null;
	private String packetRoute = null; 
	private String comment = null;
	private String ssid = null;
	
	public APRSTrack(String id) {
		super(id);
		setTrackType(TrackType.APRS_MOBILE);
		setSymbolCode(DEFAULT_APRS_SYMBOL);
		positionHistory.setHistoryLength(60 * 60 * 1000); // 1 hour
	}

	public String getPacketDestCall() {
		return packetDestCall;
	}

	public void setPacketDestCall(String packetDestCall) {
		this.packetDestCall = packetDestCall;
	}

	public String getPacketRoute() {
		return packetRoute;
	}

	public void setPacketRoute(String packetRoute) {
		this.packetRoute = packetRoute;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public String getSSID() {
		return ssid;
	}

	public void setSSID(String ssid) {
		this.ssid = ssid;
		
		// Set the right symbol for the SSID if known
		for (Entry<String, String> e : DataMaps.APRS_SSID_TO_SYMBOL.entrySet()) {
			if (ssid.equals(e.getKey())) {
				setSymbolCode(e.getValue());
				break;
			}
		}
		
		// SSIDs 0, 10 & 13 represent fixed stations
		boolean tmpFixed = ssid.equals("0") || ssid.equals("10") || ssid.equals("13");
		setFixed(tmpFixed);
		setTrackType(tmpFixed ? TrackType.APRS_BASE_STATION : TrackType.APRS_MOBILE);
	}

	@Override
	public boolean shouldDrop() {
		if (fixed) {
			return false;
		} else if (getSpeed() == null || getSpeed() < 1.0) {
			return getTimeSinceLastUpdate() > DROP_STATIC_APRS_TRACK_TIME;
		} else {
			return getTimeSinceLastUpdate() > DROP_MOVING_APRS_TRACK_TIME;
		}
	}

	@Override
	public String getDisplayDescription1() {
		return (comment != null && !comment.isEmpty()) ? comment : "";
	}

	@Override
	public String getDisplayDescription2() {
		return ((packetDestCall != null && !packetDestCall.isEmpty()) ? (">" + packetDestCall) : "") + ((packetRoute != null && !packetRoute.isEmpty()) ? ("," + packetRoute) : "");
	}
}
