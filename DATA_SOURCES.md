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
