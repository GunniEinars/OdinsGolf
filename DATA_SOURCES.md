# DATA_SOURCES.md

## Principle

Course **facts** (par, length, stroke index) and **physical-feature coordinates** you collect
yourself are not anyone's proprietary product. Commercial golf-GPS course databases **are**
proprietary. We only use openly licensed or self-collected data.

## Allowed

- **OpenStreetMap / Overpass** — open geometry (ODbL). Used here.
- **Official club info / public scorecard metadata** (e.g. Rástímar / Golf Iceland) — for
  factual par, length, stroke index, course name. Facts, used for cross-checking.
- **Your own field captures** — Survey mode GPS points. Gold standard, zero ambiguity.
- **Your own manual coordinates.**

## Not allowed (do not copy from)

Garmin, Golf Pad, Hole19, 18Birdies, SkyGolf, mScorecard, or any commercial golf-GPS course
database / hole diagrams. "Visible in an app" ≠ "licensed for reuse."

## Setberg research (done)

Located via Overpass as OSM **`golf_course` relation 8318198**, center ≈ `64.07056, -21.92560`.
Queried golf features around it:

| Feature | Found in OSM | Used as |
|---|---|---|
| `golf=hole` centerlines ×18 | ✅ with `ref` (hole #) + `par` + `handicap` | par, stroke index, tee (first node), green end (last node) |
| `golf=green` polygons ×9 | ✅ | green **center** (centroid) |
| `golf=tee` ×31 | ✅ (no hole refs) | confirmed tee positions |
| green front/back edges | ❌ | **must be field-captured** |

**Confirmed facts:** par sums to **72**; the two loops differ — e.g. hole 1 par 5 → hole 10 par 4,
hole 8 par 3 → hole 17 par 4 (exactly the shared-green/different-par structure).

**Data-quality flags in the JSON:**
`GEOMETRY_FROM_OSM`, `PAR_VERIFIED_OSM`, `STROKE_INDEX_NEEDS_VERIFICATION`,
`GREEN_FRONT_BACK_NEEDS_FIELD_VERIFICATION`.

> ⚠️ The OSM `handicap` (stroke index) tags are internally inconsistent (holes 9 and 10 both
> tagged SI 3; SI 10 absent). Verify against the printed scorecard and fix `strokeIndex` in
> `app/src/main/assets/courses/setbergsvollur.json`.

## Hazards from OSM (done, both courses)

Bunkers and water are openly mapped in OSM, so they're pulled and baked into the course JSON
(top-level `hazards` + per-hole `hazardRefs`) by `tools/bake_hazards.mjs`:

1. Query Overpass within each course bbox for `golf=bunker` / `golf=water_hazard` /
   `golf=lateral_water_hazard` and `natural=water`.
2. Dedupe feature centres within ~9 m (water polygons are sometimes double-tagged).
3. Assign each hazard to a hole when it lies within ~55 m of that hole's tee→green line, or
   within ~32 m of its green centre, or ~28 m of its tee. A greenside hazard on a shared green
   can therefore attach to both holes that use the green.

Result: **Setberg 24** hazards (1 water, 23 bunkers), **Kiðjaberg 55** bunkers. Flagged
`HAZARDS_FROM_OSM` in the JSON; an extra ODbL attribution line is appended in each file.
Hazards show numbered on the hole map and as distances on the Distance screen.

## Green front/back edges (approximated, not from OSM)

Front/back greens are **not** in OSM. Rather than leave them blank (and force a Survey
capture), they're synthesized at load time in `CourseDto.toDomain`: step the green centre
±11 m along that hole's tee→green bearing (front toward the tee, back away). This is an
**approximation** — good enough for an approach yardage, not a surveyed edge — and any real
Survey capture overrides it. The `GREEN_FRONT_BACK_NEEDS_FIELD_VERIFICATION` flag stays.

## Reproduce the hazard query

```overpassql
[out:json][timeout:25];
(
  way["golf"~"bunker|water_hazard|lateral_water_hazard"]({{bbox}});
  way["natural"="water"]({{bbox}});
  relation["natural"="water"]({{bbox}});
);
out center tags;
```

`{{bbox}}` = `south,west,north,east`. Setberg ≈ `64.0655,-21.9320,64.0750,-21.9185`;
Kiðjaberg ≈ `63.9895,-20.7925,64.0040,-20.7575`. Overpass needs a `User-Agent` header (a bare
request gets HTTP 406). Then run `node tools/bake_hazards.mjs`.

## Hole feature polygons + elevation (vector map, carry, plays-like)

`tools/bake_geometry.mjs` pulls OSM **area polygons** for both courses — `golf=green`,
`golf=fairway`, `golf=bunker`, `golf=tee`, `natural=water`/`golf=*water_hazard` — simplifies
each ring (Douglas-Peucker, ~1–2 m), assigns it to the nearest hole(s) by playing line, and
writes them per hole (`features`) plus a regenerated point-hazard list. These drive the
offline **vector hole map** and **hazard carry** distances. Flagged `FEATURES_FROM_OSM`.

Each hole also gets an **elevation profile** (`elevation.profile`, 9 samples tee→green) from
**Copernicus EU-DEM 25 m** via OpenTopoData. EU-DEM was chosen because it covers Iceland
(>60°N, where SRTM does not) and agreed with ASTER GDEM to ~1 m on test points; the
*delta* along a hole (what "plays like" uses) is internally consistent. Flagged
`ELEVATION_FROM_EUDEM`. Plays-like is elevation-only and gated to ≥3 m — no wind/weather,
which would need live data and be less reliable.

## Map imagery (hole map base layer)

The hole map draws a **satellite base layer** from **Esri "World Imagery"** raster tiles
(`https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`),
no API key, fine for low-volume personal use. Tiles are cached under `cacheDir/tiles` so a hole
viewed once works offline afterward; the schematic still draws (on the dark background) when a
tile can't be fetched. Attribution shown on the map: **Source: Esri, Maxar, Earthstar
Geographics**. This is map *imagery*, not course data — it's drawn under our own OSM/field
geometry, never traced into the course JSON.

## OSM attribution (required)

This project uses data from **OpenStreetMap**, © OpenStreetMap contributors, licensed under the
**Open Database License (ODbL)**. https://www.openstreetmap.org/copyright
The attribution string is embedded in `setbergsvollur.json` and shown on the Settings screen.

## Reproduce the Overpass query

Overpass Turbo (https://overpass-turbo.eu), or the API directly:

```overpassql
[out:json][timeout:60];
( nwr["golf"](around:800, 64.0705595, -21.9256014); );
out geom;
```

Narrow with `["golf"="green"]`, `["golf"="tee"]`, `way["golf"="hole"]` for each feature type.
`golf=hole` ways carry `ref` (hole number) and `par`; the first node ≈ tee, last node ≈ green.
