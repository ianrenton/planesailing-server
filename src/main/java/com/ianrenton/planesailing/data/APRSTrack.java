package com.ianrenton.planesailing.data;

import com.ianrenton.planesailing.app.Application;

public class APRSTrack extends Track {
	private static final long serialVersionUID = 1L;
	private static final String DEFAULT_APRS_SYMBOL = "SFGPU-------";
	private static final Long DROP_STATIC_APRS_TRACK_TIME = Application.CONFIG.getLong("timing.drop-aprs-track-static-after");
	private static final Long DROP_MOVING_APRS_TRACK_TIME = Application.CONFIG.getLong("timing.drop-aprs-track-moving-after");

	private String receiver = null;
	private String route = null; 
	private String comment = null; 
	
	public APRSTrack(String id) {
		super(id);
		setTrackType(TrackType.APRS_TRACK);
		setSymbolCode(DEFAULT_APRS_SYMBOL);
		positionHistory.setHistoryLength(60 * 60 * 1000); // 1 hour
	}

	public String getReceiver() {
		return receiver;
	}

	public void setReceiver(String receiver) {
		this.receiver = receiver;
	}

	public String getRoute() {
		return route;
	}

	public void setRoute(String route) {
		this.route = route;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}
	
	@Override
	public boolean shouldDrop() {
		if (getSpeed() == null || getSpeed() < 1.0) {
			return getTimeSinceLastUpdate() > DROP_STATIC_APRS_TRACK_TIME;
		} else {
			return getTimeSinceLastUpdate() > DROP_MOVING_APRS_TRACK_TIME;
		}
	}

	@Override
	public String getDisplayDescription1() {
		if (comment != null && !comment.isEmpty()) {
			return comment;
		} else {
			return (receiver != null && !receiver.isEmpty()) ? "RX BY " + receiver : "";
		}
	}

	@Override
	public String getDisplayDescription2() {
		if (comment != null && !comment.isEmpty()) {
			return (receiver != null && !receiver.isEmpty()) ? "RX BY " + receiver : "" + ((route != null && !route.isEmpty()) ? "  ROUTE " + route : "");
		} else {
			return (route != null && !route.isEmpty()) ? "ROUTE " + route : "";
		}
	}
}
