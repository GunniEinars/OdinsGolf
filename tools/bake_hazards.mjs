// One-off: fold OSM hazards (bunkers, water) into a course JSON.
// Assigns each hazard to every hole whose tee->green line (or green/tee) it sits
// near, so the app shows it on the relevant hole(s). Run with: node bake_hazards.mjs
import fs from "node:fs";
import path from "node:path";

const R = 6371008.8;
const toRad = (d) => (d * Math.PI) / 180;
function haversine(a, b) {
  const dLat = toRad(b.lat - a.lat);
  const dLon = toRad(b.lon - a.lon);
  const la1 = toRad(a.lat), la2 = toRad(b.lat);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}
// Local equirectangular meters about a reference latitude.
function toXY(p, refLat) {
  const x = toRad(p.lon) * Math.cos(toRad(refLat)) * R;
  const y = toRad(p.lat) * R;
  return { x, y };
}
function pointSegMeters(p, a, b, refLat) {
  const P = toXY(p, refLat), A = toXY(a, refLat), B = toXY(b, refLat);
  const dx = B.x - A.x, dy = B.y - A.y;
  const len2 = dx * dx + dy * dy;
  let t = len2 === 0 ? 0 : ((P.x - A.x) * dx + (P.y - A.y) * dy) / len2;
  t = Math.max(0, Math.min(1, t));
  const cx = A.x + t * dx, cy = A.y + t * dy;
  return Math.hypot(P.x - cx, P.y - cy);
}

function classify(tags) {
  const g = tags.golf || "";
  if (g === "bunker" || tags.natural === "sand") return { type: "bunker", name: "Bunker" };
  if (g.includes("water") || tags.natural === "water" || tags.waterway) return { type: "water", name: "Water" };
  return { type: "hazard", name: "Hazard" };
}

function bake(courseFile, hazardFile, { segThresh = 55, nearGreen = 32, nearTee = 28 } = {}) {
  const course = JSON.parse(fs.readFileSync(courseFile, "utf8"));
  const raw = JSON.parse(fs.readFileSync(hazardFile, "utf8"));
  const greens = Object.fromEntries(course.greens.map((g) => [g.id, g.center]));

  // Collect + dedupe hazard centers (within ~9 m) to avoid double water polygons.
  const feats = [];
  for (const el of raw.elements) {
    const c = el.center || (el.lat != null ? { lat: el.lat, lon: el.lon } : null);
    if (!c) continue;
    const { type, name } = classify(el.tags || {});
    if (feats.some((f) => f.type === type && haversine(f.center, c) < 9)) continue;
    feats.push({ center: { lat: +c.lat.toFixed(7), lon: +c.lon.toFixed(7) }, type, name });
  }

  const counts = {};
  const hazards = [];
  const refsByHole = Object.fromEntries(course.holes.map((h) => [h.number, []]));

  for (const f of feats) {
    const refLat = f.center.lat;
    const matched = [];
    for (const h of course.holes) {
      const tee = h.tee, green = greens[h.greenId];
      if (!tee || !green) continue;
      const dSeg = pointSegMeters(f.center, tee, green, refLat);
      const dGreen = haversine(f.center, green);
      const dTee = haversine(f.center, tee);
      if (dSeg < segThresh || dGreen < nearGreen || dTee < nearTee) matched.push(h.number);
    }
    if (matched.length === 0) continue; // off-course / not near any played hole
    counts[f.type] = (counts[f.type] || 0) + 1;
    const id = `${f.type}_${counts[f.type]}`;
    hazards.push({ id, name: f.name, type: f.type, point: { lat: f.center.lat, lon: f.center.lon, quality: "GEOMETRY_FROM_OSM" } });
    for (const n of matched) refsByHole[n].push(id);
  }

  course.hazards = hazards;
  for (const h of course.holes) h.hazardRefs = refsByHole[h.number];
  if (!course.dataQuality.includes("HAZARDS_FROM_OSM")) course.dataQuality.push("HAZARDS_FROM_OSM");
  course.sourceAttribution.push(
    "Hazards (bunkers, water) derived from OpenStreetMap and assigned to the nearest hole(s) by playing line. Map data © OpenStreetMap contributors, ODbL.",
  );

  fs.writeFileSync(courseFile, JSON.stringify(course, null, 2) + "\n");
  const attached = hazards.length;
  const total = feats.length;
  console.log(`${path.basename(courseFile)}: ${attached}/${total} hazards attached (${JSON.stringify(counts)})`);
}

const ASSETS = "app/src/main/assets/courses";
const TMP = process.env.TEMP || "/tmp";
bake(path.join(ASSETS, "setbergsvollur.json"), path.join(TMP, "setberg_haz.json"));
bake(path.join(ASSETS, "kidjabergsvollur.json"), path.join(TMP, "kidja_haz.json"));
