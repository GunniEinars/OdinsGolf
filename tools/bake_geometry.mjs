// Bake real OSM polygons (green/fairway/bunker/water/tee) and a EU-DEM elevation
// profile into each course JSON, for the offline vector hole map, hazard carry
// distances and plays-like (elevation) distance. Node 24 (global fetch). Run:
//   node tools/bake_geometry.mjs
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
const centroid = (ring) => ({
  lat: ring.reduce((s, p) => s + p.lat, 0) / ring.length,
  lon: ring.reduce((s, p) => s + p.lon, 0) / ring.length,
});

// Douglas-Peucker on a CLOSED lat/lon ring (cyclic), tolerance in metres.
// Returns an open ring (no repeated closing point); the renderer closes it.
function simplify(ring, tolM) {
  // Drop the OSM closing-duplicate node if present.
  let r = ring.slice();
  if (r.length > 1) {
    const a = r[0], b = r[r.length - 1];
    if (Math.abs(a.lat - b.lat) < 1e-9 && Math.abs(a.lon - b.lon) < 1e-9) r = r.slice(0, -1);
  }
  const n = r.length;
  if (n < 5) return r;
  const refLat = r[0].lat;
  const pts = r.map((p) => xy(p, refLat));
  // Two anchors: point 0 and the point farthest from it, so the ring splits into
  // two arcs that each simplify as a normal polyline.
  let far = 0, fd = -1;
  for (let i = 1; i < n; i++) {
    const d = Math.hypot(pts[i].x - pts[0].x, pts[i].y - pts[0].y);
    if (d > fd) { fd = d; far = i; }
  }
  const keep = new Array(n).fill(false);
  keep[0] = keep[far] = true;
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
  const arcA = []; for (let i = 0; i <= far; i++) arcA.push(i);
  const arcB = []; for (let i = far; i < n; i++) arcB.push(i); arcB.push(0);
  dp(arcA); dp(arcB);
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

// EU-DEM elevation for many points (batched 100/req, 1 req/s).
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

function ringOf(w) {
  return (w.geometry || []).filter(Boolean).map((g) => ({ lat: g.lat, lon: g.lon }));
}

const KIND = { green: "green", fairway: "fairway", bunker: "bunker", tee: "tee", water: "water", lateral_water_hazard: "water" };

async function bake(file, bbox, opts) {
  const course = JSON.parse(fs.readFileSync(file, "utf8"));
  const greens = Object.fromEntries(course.greens.map((g) => [g.id, g.center]));
  const els = await overpass(bbox);

  // Build typed, simplified rings.
  const feats = [];
  for (const w of els) {
    const tag = (w.tags && (w.tags.golf || w.tags.natural)) || "";
    const kind = KIND[tag];
    if (!kind) continue;
    let ring = ringOf(w);
    if (ring.length < 3) continue;
    ring = simplify(ring, kind === "green" ? 0.8 : kind === "bunker" ? 1.0 : 2.0);
    feats.push({ kind, ring, c: centroid(ring) });
  }

  // Assign features to holes.
  const thr = { fairway: 75, bunker: 45, water: 60, tee: 30, green: 30 };
  const perHole = Object.fromEntries(course.holes.map((h) => [h.number, []]));
  const hazards = [];
  const hazRefs = Object.fromEntries(course.holes.map((h) => [h.number, []]));
  let hazN = 0;

  for (const f of feats) {
    const refLat = f.c.lat;
    let holesFor = [];
    if (f.kind === "green") {
      // Attach a green polygon to every hole sharing the nearest greenId.
      let best = null, bestD = Infinity;
      for (const [id, ctr] of Object.entries(greens)) {
        const d = hav(f.c, ctr); if (d < bestD) { bestD = d; best = id; }
      }
      if (best != null && bestD < 60) holesFor = course.holes.filter((h) => h.greenId === best).map((h) => h.number);
    } else {
      for (const h of course.holes) {
        const tee = h.tee, green = greens[h.greenId];
        if (!tee || !green) continue;
        const dSeg = pointSeg(f.c, tee, green, refLat);
        const dGreen = hav(f.c, green), dTee = hav(f.c, tee);
        if (dSeg < thr[f.kind] || dGreen < 30 || (f.kind === "tee" && dTee < thr.tee)) holesFor.push(h.number);
      }
    }
    if (!holesFor.length) continue;
    for (const n of holesFor) perHole[n].push({ kind: f.kind, ring: f.ring.map((p) => round(p)) });
    // Bunkers + water also feed the point-hazard list (one centroid each).
    if (f.kind === "bunker" || f.kind === "water") {
      hazN++;
      const hid = `${f.kind}_${hazN}`;
      hazards.push({ id: hid, name: f.kind === "water" ? "Water" : "Bunker", type: f.kind, point: round(f.c, true) });
      for (const n of holesFor) hazRefs[n].push(hid);
    }
  }

  // Elevation profile per hole: 9 samples tee->green (eudem25m).
  const SAMPLES = 9;
  const queryPts = [];
  const holeOrder = [];
  for (const h of course.holes) {
    const tee = h.tee, green = greens[h.greenId];
    if (!tee || !green) { holeOrder.push(null); continue; }
    holeOrder.push(h.number);
    for (let i = 0; i < SAMPLES; i++) {
      const t = i / (SAMPLES - 1);
      queryPts.push({ lat: tee.lat + (green.lat - tee.lat) * t, lon: tee.lon + (green.lon - tee.lon) * t });
    }
  }
  let elev = [];
  if (!opts.skipElevation) elev = await elevations(queryPts);

  // Write back.
  let ei = 0;
  for (const h of course.holes) {
    h.features = perHole[h.number];
    h.hazardRefs = hazRefs[h.number];
    if (holeOrder.includes(h.number) && elev.length) {
      h.elevation = { profile: elev.slice(ei, ei + SAMPLES).map((e) => Math.round(e * 10) / 10) };
      ei += SAMPLES;
    }
  }
  course.hazards = hazards;
  if (!course.dataQuality.includes("FEATURES_FROM_OSM")) course.dataQuality.push("FEATURES_FROM_OSM");
  if (!opts.skipElevation && !course.dataQuality.includes("ELEVATION_FROM_EUDEM")) course.dataQuality.push("ELEVATION_FROM_EUDEM");
  const attrib = "Hole feature polygons (greens, fairways, bunkers, water, tees) from OpenStreetMap, ODbL. Elevation from Copernicus EU-DEM (25 m) via OpenTopoData.";
  if (!course.sourceAttribution.includes(attrib)) course.sourceAttribution.push(attrib);

  fs.writeFileSync(file, JSON.stringify(course, null, 2) + "\n");
  const counts = {};
  for (const h of course.holes) for (const f of h.features) counts[f.kind] = (counts[f.kind] || 0) + 1;
  console.log(`${path.basename(file)}: features(per-hole) ${JSON.stringify(counts)}, hazards ${hazards.length}, elev ${elev.length ? "yes" : "skipped"}`);
}

const round = (p, q = false) => q ? { lat: +p.lat.toFixed(6), lon: +p.lon.toFixed(6), quality: "GEOMETRY_FROM_OSM" } : [+p.lat.toFixed(6), +p.lon.toFixed(6)];

const ASSETS = "app/src/main/assets/courses";
const skipElevation = process.argv.includes("--no-elev");
await bake(path.join(ASSETS, "setbergsvollur.json"), "64.0655,-21.9320,64.0750,-21.9185", { skipElevation });
await bake(path.join(ASSETS, "kidjabergsvollur.json"), "63.9895,-20.7925,64.0040,-20.7575", { skipElevation });
console.log("done");
