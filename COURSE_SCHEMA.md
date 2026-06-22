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
  "lengthMeters": null,
  "tee":        { "lat": 64.0668091, "lon": -21.9238815, "quality": "GEOMETRY_FROM_OSM" },
  "greenFront": null,
  "greenBack":  null,
  "hazardRefs": [],
  "path": [],
  "notes": ""
}
```

- `greenId` resolves to the shared green's `center`.
- `greenFront` / `greenBack` are **per hole** (the two loops approach a shared green from
  different sides), and are `null` until captured.
- `tee` is this playing hole's tee.
- `hazardRefs` lists ids from the top-level `hazards` array.
- `path` is an optional fairway polyline `[{lat,lon}, …]`.

## Coordinate object

```json
{ "lat": 64.07, "lon": -21.92, "quality": "GEOMETRY_FROM_OSM" }
```

A coordinate is treated as **missing** (→ shows `—`, never a fake distance) when:
- `quality == "PLACEHOLDER"`, or
- `lat == 0.0 && lon == 0.0`.

## Quality flags

`GEOMETRY_FROM_OSM` · `FIELD_VERIFIED` · `GEOMETRY_MANUAL` · `PLACEHOLDER` ·
`NEEDS_FIELD_VERIFICATION` · `PAR_VERIFIED_OSM` · `STROKE_INDEX_NEEDS_VERIFICATION` ·
`GREEN_FRONT_BACK_NEEDS_FIELD_VERIFICATION`.

## How to replace / add coordinates

**Option A — Survey mode (recommended).** On the course, open Settings → Survey, stand on the
spot, wait for a `Live` fix, tap CAPTURE TEE / GREEN FRONT / CENTER / BACK / HAZARD. Captures
save to `files/survey_<courseId>.json` and overlay onto the course immediately. Pull the file:

```
adb pull /data/data/com.odinsgolf/files/survey_setbergsvollur.json
```

Then fold those lat/lon values into `setbergsvollur.json` (set `greenFront`/`greenBack`/`tee`,
quality `FIELD_VERIFIED`) so they ship in the APK.

**Option B — Hand edit.** Edit `setbergsvollur.json` directly. Get coordinates from Overpass
Turbo or by reading them off the map. Keep `quality` honest.

## Adding another course

Drop `app/src/main/assets/courses/<id>.json` with the same schema. (A course-picker UI is a
future phase; today the app loads `setbergsvollur.json`.)
