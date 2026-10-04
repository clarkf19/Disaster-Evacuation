package com.mumbai.evacuation.dto;

/** Loose sanity bounds for coordinates accepted by the API (wider MMR incl. Thane, Navi Mumbai, Vasai-Virar). */
public final class MumbaiBounds {
    public static final String MIN_LAT = "18.5";
    public static final String MAX_LAT = "19.8";
    public static final String MIN_LON = "72.5";
    public static final String MAX_LON = "73.5";

    private MumbaiBounds() {}
}
