// Bake real OSM geometry into each course JSON for the offline vector hole map:
//   - hole CENTERLINES (golf=hole) -> hole.path  (follows doglegs)
//   - area polygons (green/fairway/bunker/water/tee) -> hole.features
//   - point hazards (bunker/water centroids) -> hazards + hazardRefs
//   - EU-DEM elevation profile (tee->green) -> hole.elevation
// Features are assigned to the hole whose CENTERLINE they sit nearest (tight,
// mostly single-hole), so parallel holes and doglegs don't bleed together.
// Node 24 (global fetch). Run: node tools/bake_geometry.mjs   [--no-elev]
import fs from "node:fs";
import path from "node:path";

const UA = "OdinsGolf/1.0 (personal golf app; einarsson1@gmail.com)";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const R = 6371008.8;
const toRad = (d) => (d * Math.PI) / 180;
const hav = (a, b) => {
  const dLat = toRad(b.lat - a.lat), dLon = toRad(b.lon - a.lon);
  const la1 = toRad(a.lat), la2 = toRad(b.lat);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
};
const xy = (p, refLat) => ({ x: toRad(p.lon) * Math.cos(toRad(refLat)) * R, y: toRad(p.lat) * R });
function pointSeg(p, a, b, refLat) {
  const P = xy(p, refLat), A = xy(a, refLat), B = xy(b, refLat);
  const dx = B.x - A.x, dy = B.y - A.y, len2 = dx * dx + dy * dy;
  let t = len2 === 0 ? 0 : ((P.x - A.x) * dx + (P.y - A.y) * dy) / len2;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(P.x - (A.x + t * dx), P.y - (A.y + t * dy));
}
function pointPolyline(p, line) {
  if (!line || line.length < 2) return Infinity;
  let m = Infinity;
  for (let i = 0; i < line.length - 1; i++) m = Math.min(m, pointSeg(p, line[i], line[i + 1], p.lat));
  return m;
}
// Ray-cast point-in-polygon (planar lat/lon, fine at hole scale).
function pointInRing(p, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const xi = ring[i].lon, yi = ring[i].lat, xj = ring[j].lon, yj = ring[j].lat;
    if (((yi > p.lat) !== (yj > p.lat)) && (p.lon < ((xj - xi) * (p.lat - yi)) / (yj - yi) + xi)) inside = !inside;
  }
  return inside;
}
// Does a hole's centerline run through this ring (or its centroid sit close)?
function lineThroughRing(line, ring, c) {
  if (!line) return false;
  if (line.some((v) => pointInRing(v, ring))) return true;
  return pointPolyline(c, line) < 30;
}
const centroid = (ring) => ({
  lat: ring.reduce((s, p) => s + p.lat, 0) / ring.length,
  lon: ring.reduce((s, p) => s + p.lon, 0) / ring.length,
});

// Douglas-Peucker. closed=true treats the ring cyclically (returns an open ring).
function simplify(ring, tolM, closed) {
  let r = ring.slice();
  if (closed && r.length > 1) {
    const a = r[0], b = r[r.length - 1];
    if (Math.abs(a.lat - b.lat) < 1e-9 && Math.abs(a.lon - b.lon) < 1e-9) r = r.slice(0, -1);
  }
  const n = r.length;
  if (n < (closed ? 5 : 3)) return r;
  const refLat = r[0].lat;
  const pts = r.map((p) => xy(p, refLat));
  const keep = new Array(n).fill(false);
  const dp = (idx) => {
    const rec = (a, b) => {
      if (b <= a + 1) return;
      const A = pts[idx[a]], B = pts[idx[b]], dx = B.x - A.x, dy = B.y - A.y, len = Math.hypot(dx, dy) || 1;
      let dmax = 0, mi = -1;
      for (let k = a + 1; k < b; k++) {
        const P = pts[idx[k]];
        const d = Math.abs((P.x - A.x) * dy - (P.y - A.y) * dx) / len;
        if (d > dmax) { dmax = d; mi = k; }
      }
      if (dmax > tolM && mi > 0) { keep[idx[mi]] = true; rec(a, mi); rec(mi, b); }
    };
    rec(0, idx.length - 1);
  };
  if (!closed) {
    keep[0] = keep[n - 1] = true;
    dp([...Array(n).keys()]);
  } else {
    let far = 0, fd = -1;
    for (let i = 1; i < n; i++) {
      const d = Math.hypot(pts[i].x - pts[0].x, pts[i].y - pts[0].y);
      if (d > fd) { fd = d; far = i; }
    }
    keep[0] = keep[far] = true;
    const arcA = []; for (let i = 0; i <= far; i++) arcA.push(i);
    const arcB = []; for (let i = far; i < n; i++) arcB.push(i); arcB.push(0);
    dp(arcA); dp(arcB);
  }
  return r.filter((_, i) => keep[i]);
}

async function overpass(bbox) {
  const q = `[out:json][timeout:90];(way["golf"](${bbox});way["natural"="water"](${bbox}););out geom;`;
  const res = await fetch("https://overpass-api.de/api/interpreter", {
    method: "POST",
    headers: { "User-Agent": UA, "Content-Type": "application/x-www-form-urlencoded" },
    body: "data=" + encodeURIComponent(q),
  });
  if (!res.ok) throw new Error("overpass " + res.status);
  return (await res.json()).elements || [];
}

async function elevations(points) {
  const out = [];
  for (let i = 0; i < points.length; i += 100) {
    const batch = points.slice(i, i + 100);
    const locs = batch.map((p) => `${p.lat},${p.lon}`).join("|");
    const res = await fetch("https://api.opentopodata.org/v1/eudem25m?locations=" + encodeURIComponent(locs));
    const j = await res.json();
    if (j.status !== "OK") throw new Error("opentopodata " + j.status + " " + (j.error || ""));
    out.push(...j.results.map((r) => r.elevation));
    await sleep(1100);
  }
  return out;
}

const ringOf = (w) => (w.geometry || []).filter(Boolean).map((g) => ({ lat: g.lat, lon: g.lon }));
const KIND = { green: "green", fairway: "fairway", bunker: "bunker", tee: "tee", water: "water", lateral_water_hazard: "water" };
const roundRing = (r) => r.map((p) => [+p.lat.toFixed(6), +p.lon.toFixed(6)]);
const roundPt = (p) => ({ lat: +p.lat.toFixed(6), lon: +p.lon.toFixed(6), quality: "GEOMETRY_FROM_OSM" });

async function bake(file, bbox, opts) {
  const course = JSON.parse(fs.readFileSync(file, "utf8"));
  const greens = Object.fromEntries(course.greens.map((g) => [g.id, g.center]));
  const els = await overpass(bbox);

  // --- 1. Hole centerlines (golf=hole), matched to holes by tee/green endpoints.
  const centerlines = els.filter((w) => w.tags?.golf === "hole").map(ringOf).filter((l) => l.length >= 2);
  const holePath = {}; // hole number -> [{lat,lon}] tee..green
  for (const h of course.holes) {
    const tee = h.tee, green = greens[h.greenId];
    if (!tee || !green) { holePath[h.number] = null; continue; }
    let best = null, bestScore = Infinity, rev = false;
    for (const cl of centerlines) {
      const a = cl[0], b = cl[cl.length - 1];
      const s1 = hav(tee, a) + hav(green, b);
      const s2 = hav(tee, b) + hav(green, a);
      const sc = Math.min(s1, s2);
      if (sc < bestScore) { bestScore = sc; best = cl; rev = s2 < s1; }
    }
    holePath[h.number] = best && bestScore < 220 ? simplify(rev ? [...best].reverse() : best, 3.0, false) : null;
  }

  // --- 2. Typed, simplified area polygons.
  const feats = [];
  for (const w of els) {
    const tag = (w.tags && (w.tags.golf || w.tags.natural)) || "";
    const kind = KIND[tag];
    if (!kind || tag === "hole") continue;
    let ring = ringOf(w);
    if (ring.length < 3) continue;
    ring = simplify(ring, kind === "green" ? 0.8 : kind === "bunker" ? 1.0 : 2.0, true);
    if (ring.length >= 3) feats.push({ kind, ring, c: centroid(ring) });
  }

  // --- 3. Assign features to the hole whose centerline they sit nearest.
  const thr = { bunker: 30, water: 35 };
  const GREENSIDE = 28, TEE_NEAR = 35;
  const perHole = Object.fromEntries(course.holes.map((h) => [h.number, []]));
  const hazRefs = Object.fromEntries(course.holes.map((h) => [h.number, []]));
  const hazards = [];
  let hazN = 0;

  const nearestGreenId = (c) => {
    let id = null, d = Infinity;
    for (const [gid, ctr] of Object.entries(greens)) { const dd = hav(c, ctr); if (dd < d) { d = dd; id = gid; } }
    return { id, d };
  };
  const holesWithGreen = (gid) => course.holes.filter((h) => h.greenId === gid).map((h) => h.number);
  const nearestCenterlineHole = (c, max) => {
    let n = null, d = Infinity;
    for (const h of course.holes) { const dd = pointPolyline(c, holePath[h.number]); if (dd < d) { d = dd; n = h.number; } }
    return d <= max ? n : null;
  };

  for (const f of feats) {
    let holesFor = [];
    if (f.kind === "green") {
      const g = nearestGreenId(f.c);
      if (g.id != null && g.d < 60) holesFor = holesWithGreen(g.id);
    } else if (f.kind === "fairway") {
      // Every hole whose playing line runs through it (handles shared fairways).
      holesFor = course.holes.filter((h) => lineThroughRing(holePath[h.number], f.ring, f.c)).map((h) => h.number);
    } else if (f.kind === "tee") {
      let n = null, d = Infinity;
      for (const h of course.holes) { if (!h.tee) continue; const dd = hav(f.c, h.tee); if (dd < d) { d = dd; n = h.number; } }
      if (n != null && d < TEE_NEAR) holesFor = [n];
    } else {
      // Bunkers/water: greenside ones belong to every hole sharing that green;
      // otherwise the single hole whose centerline is nearest.
      const g = nearestGreenId(f.c);
      if (g.id != null && g.d < GREENSIDE) {
        holesFor = holesWithGreen(g.id);
      } else {
        const n = nearestCenterlineHole(f.c, thr[f.kind] ?? 30);
        if (n != null) holesFor = [n];
      }
    }
    if (!holesFor.length) continue;
    for (const n of holesFor) perHole[n].push({ kind: f.kind, ring: roundRing(f.ring) });
    if (f.kind === "bunker" || f.kind === "water") {
      hazN++;
      const id = `${f.kind}_${hazN}`;
      hazards.push({ id, name: f.kind === "water" ? "Water" : "Bunker", type: f.kind, point: roundPt(f.c) });
      for (const n of holesFor) hazRefs[n].push(id);
    }
  }

  // --- 4. Elevation profile per hole (9 samples tee->green).
  const SAMPLES = 9;
  const queryPts = [];
  for (const h of course.holes) {
    const tee = h.tee, green = greens[h.greenId];
    if (!tee || !green) continue;
    for (let i = 0; i < SAMPLES; i++) {
      const t = i / (SAMPLES - 1);
      queryPts.push({ lat: tee.lat + (green.lat - tee.lat) * t, lon: tee.lon + (green.lon - tee.lon) * t });
    }
  }
  let elev = [];
  if (!opts.skipElevation) elev = await elevations(queryPts);

  // --- 5. Write back.
  let ei = 0;
  for (const h of course.holes) {
    const tee = h.tee, green = greens[h.greenId];
    h.path = holePath[h.number] ? roundRing(holePath[h.number]) : [];
    h.features = perHole[h.number];
    h.hazardRefs = hazRefs[h.number];
    if (tee && green && elev.length) {
      h.elevation = { profile: elev.slice(ei, ei + SAMPLES).map((e) => Math.round(e * 10) / 10) };
      ei += SAMPLES;
    }
  }
  course.hazards = hazards;
  for (const flag of ["FEATURES_FROM_OSM"]) if (!course.dataQuality.includes(flag)) course.dataQuality.push(flag);
  if (!opts.skipElevation && !course.dataQuality.includes("ELEVATION_FROM_EUDEM")) course.dataQuality.push("ELEVATION_FROM_EUDEM");
  const attrib = "Hole centerlines + feature polygons (greens, fairways, bunkers, water, tees) from OpenStreetMap, ODbL. Elevation from Copernicus EU-DEM (25 m) via OpenTopoData.";
  if (!course.sourceAttribution.includes(attrib)) course.sourceAttribution.push(attrib);

  fs.writeFileSync(file, JSON.stringify(course, null, 2) + "\n");
  const counts = {};
  for (const h of course.holes) for (const f of h.features) counts[f.kind] = (counts[f.kind] || 0) + 1;
  const noPath = course.holes.filter((h) => !h.path.length).map((h) => h.number);
  console.log(`${path.basename(file)}: features ${JSON.stringify(counts)}, hazards ${hazards.length}, ` +
    `holes w/o centerline: ${noPath.join(",") || "none"}, elev ${elev.length ? "yes" : "skipped"}`);
}

const ASSETS = "app/src/main/assets/courses";
const skipElevation = process.argv.includes("--no-elev");
await bake(path.join(ASSETS, "setbergsvollur.json"), "64.0655,-21.9320,64.0750,-21.9185", { skipElevation });
await bake(path.join(ASSETS, "kidjabergsvollur.json"), "63.9895,-20.7925,64.0040,-20.7575", { skipElevation });
console.log("done");
