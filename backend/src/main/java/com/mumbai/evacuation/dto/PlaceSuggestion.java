package com.mumbai.evacuation.dto;

public class PlaceSuggestion {
    private String name;
    private String subText;
    private double lat;
    private double lon;
    private String type;
    private String icon;

    public PlaceSuggestion() {}

    public PlaceSuggestion(String name, String subText, double lat, double lon, String type, String icon) {
        this.name = name;
        this.subText = subText;
        this.lat = lat;
        this.lon = lon;
        this.type = type;
        this.icon = icon;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSubText() { return subText; }
    public void setSubText(String subText) { this.subText = subText; }

    public double getLat() { return lat; }
    public double lat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public double lon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
