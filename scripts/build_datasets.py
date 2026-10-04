"""
Builds every dataset the backend loads from backend/src/main/resources/data/:

  mumbai_nodes.csv     road intersections (OSM ids) + elevation_m
  mumbai_edges.csv     directed drivable road segments
  rail_stations.csv    Mumbai Suburban Railway stations (+ lines, elevation)
  rail_links.csv       consecutive stations on each suburban line
  shelters.json        municipal schools from OpenStreetMap with estimated capacity
  flood_hotspots.json  curated chronic water-logging spots (monsoon risk layer)

Sources
  * Roads, rail, schools: (c) OpenStreetMap contributors, via the Overpass API (ODbL).
  * Elevation: Copernicus DEM GLO-30 (ESA, free & open), read from the public AWS bucket.
    NOTE: Copernicus GLO-30 is a surface model (DSM) - in dense areas it partly
    includes buildings and trees, so absolute heights are biased upwards. It is still
    useful for ranking low-lying vs. higher ground.

Usage (Python 3.10+):
  python -m venv .venv && .venv/Scripts/pip install -r scripts/requirements.txt
  python scripts/build_datasets.py                     # default area, residential streets included
  python scripts/build_datasets.py --no-residential    # major roads only (smaller, faster)
  python scripts/build_datasets.py --bbox 72.77 18.89 73.12 19.32

Re-running is safe; downloads are cached in scripts/.cache/.
"""

import argparse
import csv
import json
import math
import os
import sys
import time
import urllib.parse
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT_DIR = os.path.join(ROOT, "backend", "src", "main", "resources", "data")
CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache")
OVERPASS_URL = "https://overpass-api.de/api/interpreter"
USER_AGENT = "MumbaiEvacuationProject/1.1 (student research project)"

# Default: Colaba -> Dahisar / Thane, and Vashi in Navi Mumbai (west, south, east, north)
DEFAULT_BBOX = (72.77, 18.89, 73.06, 19.30)

ROAD_SPEED_KMH = {
    "motorway": 80, "motorway_link": 70, "trunk": 60, "trunk_link": 50,
    "primary": 50, "primary_link": 40, "secondary": 40, "secondary_link": 35,
    "tertiary": 30, "tertiary_link": 25, "unclassified": 25, "residential": 20,
    "living_street": 10,
}
ROAD_CAPACITY_VPH = {
    "motorway": 500, "motorway_link": 450, "trunk": 400, "trunk_link": 350,
    "primary": 300, "primary_link": 250, "secondary": 200, "secondary_link": 150,
    "tertiary": 100, "tertiary_link": 80, "unclassified": 80, "residential": 60,
    "living_street": 30,
}

# Chronic water-logging spots that are reported in the news and in BMC monsoon
# preparedness coverage year after year. Coordinates are APPROXIMATE (hand-placed
# from map review), and this is NOT an official BMC list - verify against BMC's
# latest published flooding-spot list before relying on it.
FLOOD_HOTSPOTS = [
    ("Hindmata Junction, Dadar East", 19.0063, 72.8421),
    ("Parel TT / Dr. Ambedkar Road", 19.0035, 72.8430),
    ("Gandhi Market, King's Circle", 19.0300, 72.8575),
    ("Sion Circle / Road No. 24", 19.0405, 72.8615),
    ("Dharavi T-Junction", 19.0385, 72.8535),
    ("Chunabhatti", 19.0515, 72.8695),
    ("Kurla (LBS Marg, Kamani)", 19.0790, 72.8850),
    ("Milan Subway, Santacruz", 19.0880, 72.8435),
    ("Khar Subway", 19.0712, 72.8385),
    ("Andheri Subway", 19.1180, 72.8470),
    ("Saki Naka Junction", 19.1035, 72.8880),
    ("Malad Subway", 19.1868, 72.8486),
    ("Dahisar Subway", 19.2505, 72.8595),
    ("Nair Hospital / Mumbai Central", 18.9700, 72.8205),
    ("Wadala (Antop Hill)", 19.0235, 72.8650),
    ("Postal Colony, Chembur", 19.0480, 72.8920),
    ("Bhandup (LBS Marg, Sonapur)", 19.1545, 72.9370),
]

# Large open-air venues used as assembly points (fires, collapses, chemical leaks).
# They are open-air, so they are marked flood-prone and excluded while a flood is
# active. Capacities are rough estimates.
OPEN_VENUES = [
    ("Wankhede Stadium (Churchgate)", 18.9389, 72.8258, 25000),
    ("Brabourne Stadium (Marine Drive)", 18.9328, 72.8242, 20000),
    ("Azad Maidan (CST)", 18.9412, 72.8315, 15000),
    ("Cross Maidan (Churchgate)", 18.9378, 72.8302, 12000),
    ("Oval Maidan", 18.9295, 72.8288, 10000),
    ("Shivaji Park (Dadar)", 19.0269, 72.8381, 30000),
    ("BKC Exhibition Ground (Bandra East)", 19.0665, 72.8680, 35000),
    ("Andheri Sports Complex", 19.1310, 72.8350, 25000),
    ("Goregaon Sports Club ground", 19.1680, 72.8390, 20000),
    ("Dadoji Konddev Stadium (Thane)", 19.1880, 72.9650, 20000),
]

FLOOD_PRONE_ELEVATION_M = 4.0   # DSM height at or below which a site is treated as low-lying
FLOOD_PRONE_HOTSPOT_M = 400     # shelters this close to a hotspot are treated as flood-prone


# ---------------------------------------------------------------- helpers

def haversine_m(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(a))


def overpass(query, label):
    os.makedirs(CACHE_DIR, exist_ok=True)
    cache = os.path.join(CACHE_DIR, f"overpass_{label}.json")
    if os.path.exists(cache):
        with open(cache, encoding="utf-8") as f:
            return json.load(f)
    data = urllib.parse.urlencode({"data": query}).encode()
    for attempt in range(4):
        try:
            req = urllib.request.Request(OVERPASS_URL, data=data, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=300) as resp:
                result = json.loads(resp.read().decode("utf-8"))
            with open(cache, "w", encoding="utf-8") as f:
                json.dump(result, f)
            return result
        except Exception as e:  # rate limit / timeout: back off and retry
            wait = 20 * (attempt + 1)
            print(f"  Overpass {label} failed ({e}); retrying in {wait}s")
            time.sleep(wait)
    sys.exit(f"Overpass query '{label}' kept failing - try again later.")


def polygon_area_m2(geometry):
    """Shoelace area of a closed lat/lon ring, in square metres (local projection)."""
    if not geometry or len(geometry) < 4:
        return None
    lat0 = math.radians(sum(p["lat"] for p in geometry) / len(geometry))
    kx = 111_320 * math.cos(lat0)
    ky = 110_540
    pts = [(p["lon"] * kx, p["lat"] * ky) for p in geometry]
    area = 0.0
    for (x1, y1), (x2, y2) in zip(pts, pts[1:] + pts[:1]):
        area += x1 * y2 - x2 * y1
    return abs(area) / 2


# ---------------------------------------------------------------- elevation

class Elevation:
    """Samples Copernicus GLO-30 heights; tiles are downloaded once into the cache."""

    def __init__(self, bbox):
        import rasterio  # noqa: F401  (fail early with a clear error)
        self.tiles = {}
        west, south, east, north = bbox
        for lat in range(math.floor(south), math.floor(north) + 1):
            for lon in range(math.floor(west), math.floor(east) + 1):
                path = self._download(lat, lon)
                if path:
                    self.tiles[(lat, lon)] = path

    @staticmethod
    def _download(lat, lon):
        name = f"Copernicus_DSM_COG_10_N{lat:02d}_00_E{lon:03d}_00_DEM"
        path = os.path.join(CACHE_DIR, name + ".tif")
        if os.path.exists(path):
            return path
        url = f"https://copernicus-dem-30m.s3.amazonaws.com/{name}/{name}.tif"
        print(f"  downloading elevation tile {name} ...")
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=600) as resp, open(path, "wb") as f:
                f.write(resp.read())
            return path
        except Exception as e:  # tiles over open sea do not exist
            print(f"  no tile {name} ({e})")
            return None

    def sample(self, points):
        """points: list of (lat, lon) -> list of elevation (m) or None."""
        import rasterio
        out = [None] * len(points)
        by_tile = {}
        for i, (lat, lon) in enumerate(points):
            by_tile.setdefault((math.floor(lat), math.floor(lon)), []).append(i)
        for key, idxs in by_tile.items():
            path = self.tiles.get(key)
            if not path:
                continue
            with rasterio.open(path) as ds:
                nodata = ds.nodata
                for i, val in zip(idxs, ds.sample([(points[i][1], points[i][0]) for i in idxs])):
                    v = float(val[0])
                    out[i] = None if (nodata is not None and v == nodata) or v < -100 else round(v, 1)
        return out


# ---------------------------------------------------------------- roads

def build_roads(bbox, residential):
    """
    Builds a simplified, directed, drivable road graph straight from Overpass data.

    Memory-light replacement for OSMnx (which needs several GB for a city with
    residential streets): ways are split at intersections, segment lengths are
    summed along the original geometry, one-way rules follow OSM tags, and only
    the largest strongly connected component is kept so every node can reach
    every other.
    """
    west, south, east, north = bbox
    classes = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link"
    if residential:
        classes += "|unclassified|residential|living_street"
    label = "roads_residential" if residential else "roads_major"
    print(f"Downloading road network ({'with' if residential else 'without'} residential streets) ...")
    data = overpass(f"""
        [out:json][timeout:600];
        way["highway"~"^({classes})$"]["area"!="yes"]["access"!~"^(private|no)$"]["motor_vehicle"!~"^(private|no)$"]
           ({south},{west},{north},{east});
        (._;>;);
        out body;
    """, label)

    coords, ways = {}, []
    for e in data["elements"]:
        if e["type"] == "node":
            coords[e["id"]] = (e["lat"], e["lon"])
        elif e["type"] == "way" and len(e.get("nodes", [])) >= 2:
            ways.append(e)
    del data

    # Intersections (and way endpoints) become graph nodes; everything else is geometry.
    usage = {}
    for w in ways:
        for n in w["nodes"]:
            usage[n] = usage.get(n, 0) + 1
    keep = {n for n, c in usage.items() if c > 1}
    for w in ways:
        keep.add(w["nodes"][0])
        keep.add(w["nodes"][-1])

    raw_edges = []  # (u, v, length, highway)
    for w in ways:
        tags = w.get("tags", {})
        hw = tags.get("highway", "residential")
        hw = hw if hw in ROAD_SPEED_KMH else "residential"
        oneway = tags.get("oneway", "no")
        forward = oneway in ("yes", "true", "1") or tags.get("junction") in ("roundabout", "circular") \
            or (hw in ("motorway", "motorway_link") and oneway != "no")
        backward = oneway == "-1"
        seq = [n for n in w["nodes"] if n in coords]
        start, length = seq[0], 0.0
        for a, b in zip(seq, seq[1:]):
            length += haversine_m(*coords[a], *coords[b])
            if b in keep:
                if b != start and length > 0:
                    if not backward:
                        raw_edges.append((start, b, length, hw))
                    if not forward:
                        raw_edges.append((b, start, length, hw))
                start, length = b, 0.0

    largest = largest_strongly_connected(raw_edges)
    nodes = {n: coords[n] for n in largest}
    edges = []
    for u, v, length, hw in raw_edges:
        if u in largest and v in largest:
            edges.append((len(edges) + 1, u, v, round(length, 2), hw, ROAD_SPEED_KMH[hw], ROAD_CAPACITY_VPH[hw]))
    print(f"  {len(nodes):,} nodes, {len(edges):,} directed edges (largest strongly connected component)")
    return nodes, edges


def largest_strongly_connected(edges):
    """Iterative Kosaraju: returns the node set of the largest strongly connected component."""
    out_adj, in_adj = {}, {}
    for u, v, *_ in edges:
        out_adj.setdefault(u, []).append(v)
        in_adj.setdefault(v, []).append(u)
        out_adj.setdefault(v, [])
        in_adj.setdefault(u, [])

    order, seen = [], set()
    for root in out_adj:
        if root in seen:
            continue
        seen.add(root)
        stack = [(root, iter(out_adj[root]))]
        while stack:
            node, it = stack[-1]
            for nxt in it:
                if nxt not in seen:
                    seen.add(nxt)
                    stack.append((nxt, iter(out_adj[nxt])))
                    break
            else:
                stack.pop()
                order.append(node)

    assigned, best = set(), set()
    for root in reversed(order):
        if root in assigned:
            continue
        component, stack = {root}, [root]
        assigned.add(root)
        while stack:
            node = stack.pop()
            for prev in in_adj[node]:
                if prev not in assigned:
                    assigned.add(prev)
                    component.add(prev)
                    stack.append(prev)
        if len(component) > len(best):
            best = component
    return best


# ---------------------------------------------------------------- rail

def build_rail(bbox):
    west, south, east, north = bbox
    pad = 0.05
    area = f"{south - pad},{west - pad},{north + pad},{east + pad}"
    print("Downloading Mumbai Suburban Railway lines and stations ...")
    data = overpass(f"""
        [out:json][timeout:180];
        relation["route"="train"]["network"="Mumbai Suburban Railway"]({area})->.lines;
        .lines out body;
        node(r.lines);
        out body;
        node["railway"="station"]({area});
        out body;
    """, "rail")

    nodes = {e["id"]: e for e in data["elements"] if e["type"] == "node"}
    stations = [e for e in data["elements"] if e["type"] == "node"
                and e.get("tags", {}).get("railway") == "station" and e.get("tags", {}).get("name")]

    def canonical(node):
        """Map a stop/platform node to the nearest named railway=station (within 600 m)."""
        best, best_d = None, 600
        for s in stations:
            d = haversine_m(node["lat"], node["lon"], s["lat"], s["lon"])
            if d < best_d:
                best, best_d = s, d
        return best

    def inside(s):
        return west <= s["lon"] <= east and south <= s["lat"] <= north

    station_ids, station_rows, links = {}, [], {}
    for rel in (e for e in data["elements"] if e["type"] == "relation"):
        name = rel.get("tags", {}).get("name", "")
        if "fast" in name.lower():
            continue  # slow (all-stop) services already cover every station
        line = name.split(":")[0].split("(")[0].strip() or "Suburban line"
        sequence = []
        for m in rel.get("members", []):
            if m["type"] != "node" or not m.get("role", "").startswith("stop"):
                continue
            node = nodes.get(m["ref"])
            st = canonical(node) if node else None
            if st and inside(st) and (not sequence or sequence[-1]["id"] != st["id"]):
                sequence.append(st)
        for st in sequence:
            if st["id"] not in station_ids:
                station_ids[st["id"]] = len(station_rows)
                station_rows.append({"name": st["tags"]["name"], "lat": st["lat"], "lon": st["lon"], "lines": set()})
            station_rows[station_ids[st["id"]]]["lines"].add(line)
        for a, b in zip(sequence, sequence[1:]):
            ia, ib = station_ids[a["id"]], station_ids[b["id"]]
            key = (min(ia, ib), max(ia, ib), line)
            links[key] = round(haversine_m(a["lat"], a["lon"], b["lat"], b["lon"]) * 1.05, 1)
    print(f"  {len(station_rows)} stations, {len(links)} station-to-station links")
    return station_rows, links


# ---------------------------------------------------------------- shelters

def build_shelters(bbox, hotspots):
    west, south, east, north = bbox
    print("Downloading municipal schools (BMC shelter candidates) ...")
    data = overpass(f"""
        [out:json][timeout:180];
        (
          nwr["amenity"="school"]["name"~"municipal|bmc|mcgm|m\\\\.p\\\\.s|mumbai public school|tmc|nmmc",i]({south},{west},{north},{east});
          nwr["amenity"="school"]["operator"~"municipal|bmc|mcgm|brihanmumbai|thane municipal|navi mumbai municipal",i]({south},{west},{north},{east});
        );
        out center geom tags;
    """, "schools")

    shelters, seen = [], []
    for e in data["elements"]:
        tags = e.get("tags", {})
        name = tags.get("name")
        if not name:
            continue
        lat = e.get("lat") or e.get("center", {}).get("lat")
        lon = e.get("lon") or e.get("center", {}).get("lon")
        geometry = e.get("geometry") or []
        if (lat is None or lon is None) and geometry:
            # Ways/relations returned with full geometry carry no centre point: use the vertex mean.
            lat = sum(p["lat"] for p in geometry) / len(geometry)
            lon = sum(p["lon"] for p in geometry) / len(geometry)
        if lat is None or lon is None or not (west <= lon <= east and south <= lat <= north):
            continue
        if any(haversine_m(lat, lon, a, b) < 60 for a, b in seen):
            continue  # duplicate mapping of the same campus
        seen.append((lat, lon))
        area = polygon_area_m2(e.get("geometry")) if e["type"] == "way" else None
        # Capacity: ~40 % of the campus is built, ~3 usable floors, 3.5 m2 per person
        # (Sphere minimum covered living space). Clamped to a plausible range.
        capacity = 400 if not area else int(min(3000, max(150, round(area * 0.4 * 3 / 3.5, -1))))
        shelters.append({"name": name.strip(), "kind": "school", "lat": round(lat, 6), "lon": round(lon, 6),
                         "capacity": capacity, "capacityEstimated": True, "osmId": f"{e['type']}/{e['id']}"})
    schools = len(shelters)
    for name, lat, lon, capacity in OPEN_VENUES:
        if west <= lon <= east and south <= lat <= north:
            shelters.append({"name": name, "kind": "open_ground", "lat": lat, "lon": lon,
                             "capacity": capacity, "capacityEstimated": True})
    shelters.sort(key=lambda s: (s["kind"] != "school", s["name"]))
    for i, s in enumerate(shelters, start=1):
        s["id"] = i
        near_hotspot = any(haversine_m(s["lat"], s["lon"], h["lat"], h["lon"]) <= FLOOD_PRONE_HOTSPOT_M for h in hotspots)
        s["floodProne"] = near_hotspot or s["kind"] == "open_ground"
    print(f"  {schools} municipal school shelters + {len(shelters) - schools} open-air venues")
    return shelters


# ---------------------------------------------------------------- main

def write_csv(path, header, rows):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--bbox", type=float, nargs=4, metavar=("WEST", "SOUTH", "EAST", "NORTH"), default=DEFAULT_BBOX)
    parser.add_argument("--no-residential", action="store_true", help="major roads only (smaller graph)")
    args = parser.parse_args()
    bbox = tuple(args.bbox)
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(CACHE_DIR, exist_ok=True)

    nodes, edges = build_roads(bbox, not args.no_residential)
    stations, links = build_rail(bbox)
    hotspots = [{"name": n, "lat": la, "lon": lo} for n, la, lo in FLOOD_HOTSPOTS]
    shelters = build_shelters(bbox, hotspots)

    print("Sampling elevation (Copernicus GLO-30) ...")
    elev = Elevation(bbox)
    node_ids = list(nodes)
    node_elev = elev.sample([nodes[n] for n in node_ids])
    station_elev = elev.sample([(s["lat"], s["lon"]) for s in stations])
    shelter_elev = elev.sample([(s["lat"], s["lon"]) for s in shelters])
    hotspot_elev = elev.sample([(h["lat"], h["lon"]) for h in hotspots])

    write_csv(os.path.join(OUT_DIR, "mumbai_nodes.csv"), ["id", "latitude", "longitude", "elevation_m"],
              [(n, round(nodes[n][0], 7), round(nodes[n][1], 7), "" if e is None else e)
               for n, e in zip(node_ids, node_elev)])
    write_csv(os.path.join(OUT_DIR, "mumbai_edges.csv"),
              ["id", "source", "destination", "distance_meters", "road_type", "speed_limit_kmh", "capacity"], edges)
    write_csv(os.path.join(OUT_DIR, "rail_stations.csv"), ["id", "name", "latitude", "longitude", "lines", "elevation_m"],
              [(i, s["name"], round(s["lat"], 7), round(s["lon"], 7), ";".join(sorted(s["lines"])), "" if e is None else e)
               for i, (s, e) in enumerate(zip(stations, station_elev))])
    write_csv(os.path.join(OUT_DIR, "rail_links.csv"), ["from_station", "to_station", "line", "distance_meters"],
              [(a, b, line, d) for (a, b, line), d in sorted(links.items())])

    for s, e in zip(shelters, shelter_elev):
        s["elevationM"] = e
        if e is not None and e <= FLOOD_PRONE_ELEVATION_M:
            s["floodProne"] = True
    with open(os.path.join(OUT_DIR, "shelters.json"), "w", encoding="utf-8") as f:
        json.dump({
            "_note": "Shelter candidates are municipal schools from OpenStreetMap (BMC opens municipal schools as "
                     "temporary shelters during floods) plus large open-air venues used as assembly points (marked "
                     "floodProne because they are open-air). Capacities are ESTIMATES (schools: campus area x 40% built "
                     "x 3 floors / 3.5 m2 per person). This is not the official BMC shelter list - verify before real use.",
            "source": "OpenStreetMap contributors (ODbL); elevation Copernicus GLO-30",
            "verified": False,
            "shelters": shelters,
        }, f, ensure_ascii=False, indent=1)

    for h, e in zip(hotspots, hotspot_elev):
        h["elevationM"] = e
        h["radiusMeters"] = 350
    with open(os.path.join(OUT_DIR, "flood_hotspots.json"), "w", encoding="utf-8") as f:
        json.dump({
            "_note": "Approximate locations of chronic monsoon water-logging spots that are reported year after year. "
                     "Not an official BMC list - verify against BMC's latest published flooding spots.",
            "verified": False,
            "hotspots": hotspots,
        }, f, ensure_ascii=False, indent=1)

    known = [e for e in node_elev if e is not None]
    print(f"Done. Elevation known for {len(known):,}/{len(node_elev):,} nodes "
          f"(median {sorted(known)[len(known) // 2] if known else 'n/a'} m). Files written to {OUT_DIR}")


if __name__ == "__main__":
    main()
