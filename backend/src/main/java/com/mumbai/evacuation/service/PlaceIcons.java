package com.mumbai.evacuation.service;

import java.util.Locale;

/** Picks a category emoji for a place from OSM tags, falling back to keywords in its name. */
final class PlaceIcons {
    private PlaceIcons() {}

    static String resolve(String name, String osmKey, String osmValue) {
        switch (osmValue) {
            case "station", "halt", "subway_entrance" -> { return "🚇"; }
            case "aerodrome" -> { return "✈️"; }
            case "hospital", "clinic", "doctors" -> { return "🏥"; }
            case "school", "college", "university" -> { return "🎓"; }
            case "police" -> { return "👮"; }
            case "fire_station" -> { return "🚒"; }
            case "stadium", "sports_centre" -> { return "🏟️"; }
            case "park", "garden", "recreation_ground" -> { return "🌳"; }
            case "mall", "supermarket", "marketplace" -> { return "🛍️"; }
            default -> { }
        }
        if ("railway".equals(osmKey)) return "🚇";
        if ("highway".equals(osmKey)) return "🛣️";

        String t = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (t.contains("hospital") || t.contains("clinic") || t.contains("medical")) return "🏥";
        if (t.contains("station") || t.contains("metro") || t.contains("railway")) return "🚇";
        if (t.contains("airport")) return "✈️";
        if (t.contains("school") || t.contains("college") || t.contains("university")) return "🎓";
        if (t.contains("stadium") || t.contains("ground") || t.contains("maidan")) return "🏟️";
        if (t.contains("park") || t.contains("garden")) return "🌳";
        if (t.contains("mall") || t.contains("market")) return "🛍️";
        return "📍";
    }
}
