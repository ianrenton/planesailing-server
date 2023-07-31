package com.ianrenton.planesailing.comms;

public enum ConnectionStatus {
    DISABLED("Disabled"), OFFLINE("Offline"), ONLINE("Online"), ACTIVE("Active");

    private final String prettyName;

    ConnectionStatus(String s) {
        prettyName = s;
    }

    public String toString() {
        return prettyName;
    }

}