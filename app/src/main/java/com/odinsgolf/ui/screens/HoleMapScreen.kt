package com.odinsgolf.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.odinsgolf.data.TileRepository
import com.odinsgolf.data.model.FeatureKind
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.GpsStatus
import com.odinsgolf.data.model.Hole
import com.odinsgolf.data.model.MapStyle
import com.odinsgolf.location.effectiveStatus
import com.odinsgolf.geo.Carry
import com.odinsgolf.geo.Distances
import com.odinsgolf.geo.Geo
import com.odinsgolf.geo.HoleProjection
import com.odinsgolf.geo.MapPlan
import com.odinsgolf.geo.PlaysLike
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.formatDistance
import com.odinsgolf.ui.theme.OdinAmber
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Vibrant golf palette; rough is the dark background showing through.
private val FairwayFill = Color(0xFF4C9A3F)
private val GreenFill = Color(0xFF7CC576)
private val BunkerFill = Color(0xFFE6CE9A)
private val WaterFill = Color(0xFF3E82C2)
private val TeeFill = Color(0xFF6E7C58)
private val FlagRed = Color(0xFFE5484D)
// Soft shadow so white overlay text stays legible over the bright vector map.
private val MapShadow = Shadow(Color(0xCC000000), Offset(0f, 1f), 5f)

@Composable
fun HoleMapScreen(state: GolfUiState, onToggleMapStyle: () -> Unit) {
    Scaffold {
        val hole = state.hole
        if (hole == null || !hole.hasGeometry) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No geometry for this hole", color = MaterialTheme.colors.error)
            }
            return@Scaffold
        }
        val satellite = state.settings.mapStyle == MapStyle.SATELLITE
        Box(Modifier.fillMaxSize().clickable(onClick = onToggleMapStyle)) {
            if (satellite) SatelliteHoleMap(hole, state) else VectorHoleMap(hole, state)
            // Hole number flanks the green (top-centre) on the left, lowered out of
            // the clipped top corner of the round display.
            Text(
                text = "H${hole.displayNumber}",
                color = Color.White,
                style = MaterialTheme.typography.caption1.copy(shadow = MapShadow, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.TopStart).padding(top = 44.dp, start = 26.dp),
            )
        }
    }
}

// ---- Vector (default) ------------------------------------------------------

@Composable
private fun VectorHoleMap(hole: Hole, state: GolfUiState) {
    val units = state.settings.units
    val center = hole.green.center
    val tee = hole.tee
    val front = hole.green.front
    val back = hole.green.back
    val rawMe = state.gps.point
    val me = rawMe?.takeIf { center == null || Geo.distanceMeters(it, center) < 2000 }
    val arcOrigin = me ?: tee
    val toGreen = center?.let { c -> arcOrigin?.let { Geo.distanceMeters(it, c) } }

    val points = buildList {
        tee?.let { add(it) }
        center?.let { add(it) }
        me?.let { add(it) }
        hole.features.forEach { addAll(it.ring) }
        addAll(hole.path)
        addAll(hole.hazards.map { it.point })
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val padPx = with(density) { 16.dp.toPx() }
        val proj = remember(hole.number, wPx, hPx, points.size) {
            HoleProjection.build(hole, points, wPx, hPx, padPx)
        }
        val cornerIdx = remember(hole.number) { doglegCorner(hole.path) }

        Canvas(Modifier.fillMaxSize()) {
            val p = proj ?: return@Canvas
            fun off(pt: GeoPoint): Offset { val (x, y) = p.project(pt); return Offset(x, y) }

            // Filled hole, back-to-front.
            for (kind in FeatureKind.entries) {
                val fill = when (kind) {
                    FeatureKind.FAIRWAY -> FairwayFill
                    FeatureKind.GREEN -> GreenFill
                    FeatureKind.BUNKER -> BunkerFill
                    FeatureKind.WATER -> WaterFill
                    FeatureKind.TEE -> TeeFill
                }
                hole.features.filter { it.kind == kind }.forEach { fillRing(it.ring, fill, ::off) }
            }

            val gray = Paint().apply {
                color = Color.White.toArgb(); textSize = 21f; isAntiAlias = true
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                alpha = 210
            }

            // Distance-to-green rings: 150 then 100 (display units). Skipped on par 3 —
            // the big number is enough, and short holes don't need markers.
            val sc = p.metersToPx
            if (hole.par > 3 && center != null && tee != null) {
                val og = off(center)
                val teeGreen = Geo.distanceMeters(tee, center)
                for (ringVal in intArrayOf(250, 150, 100)) {
                    val rM = units.toMeters(ringVal.toDouble())
                    if (rM > teeGreen - 8.0) continue
                    val rPx = (rM * sc).toFloat()
                    drawCircle(Color.White.copy(alpha = 0.20f), radius = rPx, center = og, style = Stroke(width = 2f))
                    drawContext.canvas.nativeCanvas.drawText(ringVal.toString(), og.x - 10f, og.y + rPx + 7f, gray)
                }
            }

            // Playing line follows the OSM centerline, so doglegs bend correctly.
            val playLine = hole.path.ifEmpty { listOfNotNull(tee, center) }
            for (i in 0 until playLine.size - 1) {
                drawLine(Color.White.copy(alpha = 0.6f), off(playLine[i]), off(playLine[i + 1]), strokeWidth = 3f)
            }
            // Your line to the green (dashed).
            if (me != null && center != null) {
                drawLine(
                    Color.White, off(me), off(center), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)),
                )
            }
            // Dogleg corner: a subtle aim dot only. The bent line shows the shape and
            // the rings give the distances, so no number here (it overlapped the rings).
            if (cornerIdx in 1 until hole.path.size - 1) {
                drawCircle(Color.White.copy(alpha = 0.7f), radius = 4f, center = off(hole.path[cornerIdx]))
            }

            // Tee marker.
            tee?.let { drawCircle(Color.White, radius = 5f, center = off(it)) }

            // Green: front/back ticks + pin flag.
            center?.let {
                val o = off(it)
                front?.let { f -> drawCircle(Color.White.copy(alpha = 0.8f), radius = 3f, center = off(f)) }
                back?.let { b -> drawCircle(Color.White.copy(alpha = 0.8f), radius = 3f, center = off(b)) }
                drawCircle(Color.White, radius = 3f, center = o)
                drawLine(Color.White, Offset(o.x, o.y), Offset(o.x, o.y - 20f), strokeWidth = 2.5f)
                val flag = Path().apply {
                    moveTo(o.x, o.y - 20f); lineTo(o.x + 13f, o.y - 16f); lineTo(o.x, o.y - 12f); close()
                }
                drawPath(flag, FlagRed)
            }

            // You-are-here.
            me?.let {
                val o = off(it)
                drawCircle(Color.White.copy(alpha = 0.3f), radius = 13f, center = o)
                drawCircle(Color(0xFF2E78D2), radius = 6f, center = o)
                drawCircle(Color.White, radius = 6f, center = o, style = Stroke(width = 2f))
            }
        }

        // Big distance to the green — kept out of the round display's clipped
        // top-right corner: dropped into the wider band and inset from the edge.
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 24.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // Dim the number when the fix is stale, so it never looks live.
            val stale = state.gps.effectiveStatus(state.nowElapsed) == GpsStatus.STALE_FIX
            Text(
                text = toGreen?.let { formatDistance(it, units) } ?: "—",
                style = TextStyle(
                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    color = if (stale) OdinOnDim else Color.White, shadow = MapShadow,
                ),
            )
            val pl = PlaysLike.toCenter(hole, me)
            if (pl != null && pl.significant) {
                val arrow = if (pl.deltaMeters > 0) "▲" else "▼"
                Text(
                    "plays ${formatDistance(pl.playsLikeMeters, units)} $arrow",
                    color = OdinAmber,
                    style = MaterialTheme.typography.caption2.copy(shadow = MapShadow),
                )
            } else {
                Text(units.suffix, color = Color.White, style = MaterialTheme.typography.caption3.copy(shadow = MapShadow))
            }
        }

        // Carries over hazards ahead, and a GPS hint when unfixed.
        val carries = Carry.ahead(hole, me)
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            carries.forEach { c ->
                Text(
                    "carry ${c.label} ${formatDistance(c.carryMeters, units)}",
                    color = OdinAmber, style = MaterialTheme.typography.caption2.copy(shadow = MapShadow),
                )
            }
            if (me == null) {
                Text("waiting for GPS", color = Color.White, style = MaterialTheme.typography.caption3.copy(shadow = MapShadow))
            }
        }
    }
}

// ---- Satellite (toggle) ----------------------------------------------------

@Composable
private fun SatelliteHoleMap(hole: Hole, state: GolfUiState) {
    val context = LocalContext.current
    val tiles = remember { TileRepository(context) }
    val center = hole.green.center
    val tee = hole.tee
    val rawMe = state.gps.point
    val me = rawMe?.takeIf { center == null || Geo.distanceMeters(it, center) < 2000 }
    val hazards = hole.hazards.map { it.point }
    val points = buildList {
        tee?.let { add(it) }; center?.let { add(it) }; me?.let { add(it) }
        addAll(hazards); addAll(hole.path); hole.features.forEach { addAll(it.ring) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val padPx = with(density) { 14.dp.toPx() }
        val plan = remember(hole.number, wPx, hPx, points.size) { MapPlan.compute(points, wPx, hPx, padPx) }
        val bitmaps = remember(plan) { mutableStateMapOf<Long, ImageBitmap>() }
        LaunchedEffect(plan) {
            val pl = plan ?: return@LaunchedEffect
            pl.tiles.forEach { t -> launch { tiles.tile(t.z, t.x, t.y)?.let { bitmaps[t.key] = it.asImageBitmap() } } }
        }
        Canvas(Modifier.fillMaxSize()) {
            val p = plan ?: return@Canvas
            fun off(pt: GeoPoint): Offset { val (x, y) = p.project(pt); return Offset(x, y) }
            p.tiles.forEach { t ->
                bitmaps[t.key]?.let { img ->
                    drawImage(
                        image = img,
                        dstOffset = IntOffset(t.left.roundToInt(), t.top.roundToInt()),
                        dstSize = IntSize(t.size.roundToInt(), t.size.roundToInt()),
                    )
                }
            }
            if (tee != null && center != null) drawLine(Color.White.copy(alpha = 0.85f), off(tee), off(center), strokeWidth = 3f)
            if (me != null && center != null) {
                drawLine(OdinGreen, off(me), off(center), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            hazards.forEach { drawCircle(OdinAmber, radius = 7f, center = off(it)) }
            center?.let { drawCircle(OdinGreen, radius = 7f, center = off(it)) }
            tee?.let { drawCircle(OdinOnDim, radius = 6f, center = off(it)) }
            me?.let { val o = off(it); drawCircle(Color.White.copy(alpha = 0.3f), 13f, o); drawCircle(Color.White, 6f, o) }
        }
        val units = state.settings.units
        val d = Distances.toGreen(hole, me)
        // Distance + attribution centred (the round display clips bottom corners).
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (me != null && d.centerMeters != null) "C ${formatDistance(d.centerMeters, units)} ${units.suffix}" else "waiting for GPS",
                color = Color.White,
                style = MaterialTheme.typography.caption2.copy(shadow = MapShadow),
            )
            Text(
                "Esri, Maxar",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.caption3.copy(shadow = MapShadow),
            )
        }
    }
}

/** Index of the dogleg corner in [path] (max turn vertex), or -1 if the hole is straight. */
private fun doglegCorner(path: List<GeoPoint>): Int {
    if (path.size < 3) return -1
    var best = -1
    var bestTurn = 22.0
    for (i in 1 until path.size - 1) {
        val b1 = Geo.bearingDegrees(path[i - 1], path[i])
        val b2 = Geo.bearingDegrees(path[i], path[i + 1])
        var turn = abs(b2 - b1) % 360.0
        if (turn > 180.0) turn = 360.0 - turn
        val l1 = Geo.distanceMeters(path[i - 1], path[i])
        val l2 = Geo.distanceMeters(path[i], path[i + 1])
        if (turn > bestTurn && l1 > 40.0 && l2 > 40.0) { bestTurn = turn; best = i }
    }
    return best
}

private fun DrawScope.fillRing(ring: List<GeoPoint>, color: Color, off: (GeoPoint) -> Offset) {
    if (ring.size < 3) return
    val path = Path()
    ring.forEachIndexed { i, pt ->
        val o = off(pt)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    drawPath(path, color)
}
