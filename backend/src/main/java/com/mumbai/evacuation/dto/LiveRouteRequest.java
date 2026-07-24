package com.mumbai.evacuation.dto;

/**
 * Request DTO for the live route proxy endpoint.
 * Frontend sends plain coordinates — no API key involved.
 */
public class LiveRouteRequest {
    private double fromLat;
    private double fromLon;
    private double toLat;
    private double toLon;

    public LiveRouteRequest() {}

    public double getFromLat() { return fromLat; }
    public void setFromLat(double fromLat) { this.fromLat = fromLat; }

    public double getFromLon() { return fromLon; }
    public void setFromLon(double fromLon) { this.fromLon = fromLon; }

    public double getToLat() { return toLat; }
    public void setToLat(double toLat) { this.toLat = toLat; }

    public double getToLon() { return toLon; }
    public void setToLon(double toLon) { this.toLon = toLon; }
}
