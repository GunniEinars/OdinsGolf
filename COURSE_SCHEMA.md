# COURSE_SCHEMA.md

Course files live in `app/src/main/assets/courses/<courseId>.json` and are parsed leniently
(unknown keys ignored). Internally everything is **WGS84 lat/lon**, distances in **meters**.

## Top level

| Field | Type | Notes |
|---|---|---|
| `schemaVersion` | int | 2 |
| `courseId` | string | unique, also used for survey/round filenames |
| `courseName`, `clubName`, `country`, `locationHint` | string | display |
| `defaultUnits` | `"meters"` \| `"yards"` | initial unit |
| `par` | int | total, sanity-check |
| `physicalHoles`, `playedHoles` | int | e.g. 9 and 18 |
| `sourceAttribution` | string[] | first entry shown in Settings |
| `dataQuality` | string[] | flags (see below) |
| `notes` | string | free text |
| `greens` | Green[] | **shared** physical greens |
| `hazards` | Hazard[] | shared, referenced by id |
| `holes` | Hole[] | **playing** holes (18 for Setberg) |

## Green (shared)

```json
{ "id": "G1", "center": { "lat": 64.0696949, "lon": -21.9290879 }, "quality": "GEOMETRY_FROM_OSM" }
```

`center` is the green centroid. Hole *N* and *N+9* reference the same green `id`.

## Hole (playing hole)

```json
{
  "number": 1, "displayNumber": "1",
  "par": 5, "strokeIndex": 6,
  "greenId": "G1",
  "tee":        { "lat": 64.0668091, "lon": -21.9238815, "quality": "GEOMETRY_FROM_OSM" },
  "greenFront": null,
  "greenBack":  null,
  "hazardRefs": ["bunker_1", "water_2"],
  "path":     [[64.0668,-21.9239], [64.0683,-21.9266], [64.0697,-21.9291]],
  "features": [
    { "kind": "fairway", "ring": [[64.067,-21.924], [64.068,-21.926], [64.069,-21.928]] },
    { "kind": "green",   "ring": [[64.0696,-21.9290], …] },
    { "kind": "bunker",  "ring": [[…]] }
  ],
  "elevation": { "profile": [27.1, 24.4, 24.4, 25.7, 26.9, 29.2, 32.0, 33.5, 34.0] },
  "notes": ""
}
```

- `greenId` resolves to the shared green's `center`. Because hole *N* and *N+9* point at the
  same `greenId` (one physical green), a green **centre** captured in Survey mode is applied to
  **both** of those holes, not just the one you stood on.
- `greenFront` / `greenBack` are **per hole**. When `null`, the app **approximates** them
  (green centre ±~11 m along the tee→green line) so approach yardages show without field work;
  a real Survey capture overrides them.
- `tee` is this playing hole's tee.
- `hazardRefs` lists ids from the top-level `hazards` array (regenerated from bunker/water).
- `path` is the **hole centerline** (tee→green) as `[[lat,lon], …]` — drives the playing line
  (doglegs bend) and the dogleg-corner detection.
- `features` are OSM **area polygons** drawn on the vector map. `kind` ∈
  `fairway` | `green` | `bunker` | `water` | `tee`; `ring` is `[[lat,lon], …]` (open, not closed).
- `elevation.profile` is ground elevation in metres at 9 even samples tee→green (EU-DEM),
  used for "plays like".

> `path`, `features`, the point `hazards`/`hazardRefs`, and `elevation` are **generated** by
> `tools/bake_geometry.mjs` (pulls OSM + EU-DEM and assigns features to holes by centerline).
> Don't hand-edit them; re-run the tool. Hand-edit `tee`/`greenFront`/`greenBack`/`par`/
> `strokeIndex` only.

## Coordinate object

```json
{ "lat": 64.07, "lon": -21.92, "quality": "GEOMETRY_FROM_OSM" }
```

A coordinate is treated as **missing** (→ shows `—`, never a fake distance) when:
- `quality == "PLACEHOLDER"`, or
- `lat == 0.0 && lon == 0.0`.

## Quality flags

`GEOMETRY_FROM_OSM` · `FEATURES_FROM_OSM` · `HAZARDS_FROM_OSM` · `ELEVATION_FROM_EUDEM` ·
`PAR_VERIFIED_OSM` / `PAR_FROM_OSM` · `STROKE_INDEX_VERIFIED` ·
`GREEN_FRONT_BACK_NEEDS_FIELD_VERIFICATION` · `FIELD_VERIFIED` · `PLACEHOLDER`.

Par and stroke index are **verified against the official scorecards** (Rástímar). Green
front/back are approximated until field-captured (the one remaining `NEEDS_FIELD_VERIFICATION`).

## How to replace / add coordinates

**Option A — Survey mode (recommended).** On the course, open Settings → Survey, stand on the
spot, wait for a `Live` fix, tap CAPTURE TEE / GREEN FRONT / CENTER / BACK / HAZARD. Captures
save to `files/survey_<courseId>.json` and overlay onto the course immediately. A **CENTER**
capture applies to both holes sharing that green (e.g. Setberg 1 and 10); TEE / FRONT / BACK are
per playing hole. Pull the file:

```
adb pull /data/data/com.odinsgolf/files/survey_setbergsvollur.json
```

Then fold those lat/lon values into `setbergsvollur.json` (set `greenFront`/`greenBack`/`tee`,
quality `FIELD_VERIFIED`) so they ship in the APK.

**Option B — Hand edit.** Edit `setbergsvollur.json` directly. Get coordinates from Overpass
Turbo or by reading them off the map. Keep `quality` honest.

## Adding another course

1. Create `app/src/main/assets/courses/<id>.json` with the top-level fields, `greens`, and
   `holes` (number, par, strokeIndex, greenId, tee). Verify par/stroke index against the
   official scorecard.
2. Add the course's bbox to `tools/bake_geometry.mjs` and run it to fill `path`, `features`,
   `hazards`/`hazardRefs` and `elevation` from OSM + EU-DEM.
3. It appears in the in-app picker automatically (**More → Course**). A `CourseDataTest` parses
   every bundled course in CI, so a malformed file fails the build, not the watch.
