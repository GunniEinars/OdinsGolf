package com.odinsgolf.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.odinsgolf.data.model.FeatureKind
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.GpsStatus
import com.odinsgolf.data.model.Hole
import com.odinsgolf.data.model.Weather
import com.odinsgolf.geo.Carry
import com.odinsgolf.geo.Geo
import com.odinsgolf.geo.HoleProjection
import com.odinsgolf.geo.PlaysLike
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.formatDistance
import com.odinsgolf.ui.theme.OdinAmber
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim
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
fun HoleMapScreen(
    state: GolfUiState,
    weather: Weather?,
    aim: GeoPoint?,
    onSetAim: (GeoPoint) -> Unit,
    onClearAim: () -> Unit,
) {
    Scaffold {
        val hole = state.hole
        if (hole == null || !hole.hasGeometry) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (state.loading) "Loading course…" else "No geometry for this hole",
                    color = if (state.loading) OdinOnDim else MaterialTheme.colors.error,
                )
            }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize()) {
            VectorHoleMap(hole, state, weather, aim, onSetAim, onClearAim)
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
private fun VectorHoleMap(
    hole: Hole,
    state: GolfUiState,
    weather: Weather?,
    aim: GeoPoint?,
    onSetAim: (GeoPoint) -> Unit,
    onClearAim: () -> Unit,
) {
    val units = state.settings.units
    val center = hole.green.center
    val tee = hole.tee
    val front = hole.green.front
    val back = hole.green.back
    val rawMe = state.gps.point
    val me = rawMe?.takeIf { center == null || Geo.distanceMeters(it, center) < 2000 }
    // Big number is the live distance from where you stand — like the Distance hero. With no live
    // fix it blanks to "—" rather than quietly showing the tee→green length as if it were live.
    val toGreen = center?.let { c -> me?.let { Geo.distanceMeters(it, c) } }

    // Tap-to-measure: distance from where you'd play the shot (live position, else the tee) to the
    // tapped aim point, and what that leaves to the green.
    val aimOrigin = me ?: tee
    val aimFromOrigin = aim?.let { a -> aimOrigin?.let { Geo.distanceMeters(it, a) } }
    val aimToGreen = aim?.let { a -> center?.let { Geo.distanceMeters(a, it) } }

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

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(proj) {
                    // Tap = drop/move the aim point at that spot; long-press = clear it.
                    detectTapGestures(
                        onTap = { off -> proj?.let { onSetAim(it.unproject(off.x, off.y)) } },
                        onLongPress = { onClearAim() },
                    )
                },
        ) {
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

            // Aim point (tap-to-measure): amber shot line from where you play to the target, a green
            // remainder line target→green, and a crosshair marker. Drawn last so it sits on top.
            if (aim != null) {
                val ao = off(aim)
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                aimOrigin?.let { drawLine(OdinAmber, off(it), ao, strokeWidth = 3f, pathEffect = dash) }
                center?.let { drawLine(OdinGreen.copy(alpha = 0.85f), ao, off(it), strokeWidth = 2f, pathEffect = dash) }
                drawCircle(OdinAmber.copy(alpha = 0.22f), radius = 13f, center = ao)
                drawLine(OdinAmber, Offset(ao.x - 10f, ao.y), Offset(ao.x + 10f, ao.y), strokeWidth = 2.5f)
                drawLine(OdinAmber, Offset(ao.x, ao.y - 10f), Offset(ao.x, ao.y + 10f), strokeWidth = 2.5f)
                drawCircle(OdinAmber, radius = 3.5f, center = ao)
            }
        }

        // Big distance to the green — kept out of the round display's clipped
        // top-right corner: dropped into the wider band and inset from the edge.
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 24.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // Dim the number when the fix is stale or absent, so it never looks live.
            val stale = state.gpsStatus == GpsStatus.STALE_FIX
            Text(
                text = toGreen?.let { formatDistance(it, units) } ?: "—",
                style = TextStyle(
                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    color = if (stale || me == null) OdinOnDim else Color.White, shadow = MapShadow,
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

        // Aim readout (tap-to-measure), carries over hazards ahead, and a GPS hint when unfixed.
        val carries = Carry.ahead(hole, me)
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (aimFromOrigin != null) {
                Text(
                    buildString {
                        append("aim ${formatDistance(aimFromOrigin, units)}")
                        aimToGreen?.let { append(" · leaves ${formatDistance(it, units)}") }
                    },
                    color = OdinAmber,
                    style = MaterialTheme.typography.caption1.copy(shadow = MapShadow, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    "long-press to clear",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.caption3.copy(shadow = MapShadow),
                )
            }
            carries.forEach { c ->
                Text(
                    "carry ${c.label} ${formatDistance(c.carryMeters, units)}",
                    color = OdinAmber, style = MaterialTheme.typography.caption2.copy(shadow = MapShadow),
                )
            }
            if (me == null && aim == null) {
                Text("tap map to measure · waiting for GPS", color = Color.White, style = MaterialTheme.typography.caption3.copy(shadow = MapShadow))
            }
        }

        // Wind arrow: shown when there's live wind, pointing downwind (the way it pushes the ball),
        // rotated from a real-world bearing into this play-line-up map frame.
        val windMps = weather?.windSpeedMps ?: 0.0
        if (proj != null && weather != null && windMps >= 1.0) {
            val downwindBearing = weather.windFromDeg + 180.0
            val screenAngle = (downwindBearing - proj.upBearingDegrees()).toFloat()
            WindArrow(
                screenAngle = screenAngle,
                mps = windMps,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            )
        }
    }
}

/** Compact wind badge: a dark disc, an arrow pointing downwind (rotated into the map frame), and
 *  the speed in m/s. */
@Composable
private fun WindArrow(screenAngle: Float, mps: Double, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(30.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(Color(0x66000000), radius = size.minDimension / 2f, center = Offset(cx, cy))
            rotate(screenAngle, pivot = Offset(cx, cy)) {
                val top = Offset(cx, size.height * 0.16f)
                val bottom = Offset(cx, size.height * 0.84f)
                drawLine(Color.White, bottom, top, strokeWidth = 3f)
                drawLine(Color.White, top, Offset(cx - size.width * 0.17f, size.height * 0.38f), strokeWidth = 3f)
                drawLine(Color.White, top, Offset(cx + size.width * 0.17f, size.height * 0.38f), strokeWidth = 3f)
            }
        }
        Text(
            "${mps.roundToInt()} m/s",
            color = Color.White,
            style = MaterialTheme.typography.caption3.copy(shadow = MapShadow),
        )
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
