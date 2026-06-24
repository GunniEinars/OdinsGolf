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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.odinsgolf.data.model.Hole
import com.odinsgolf.data.model.MapStyle
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
import kotlin.math.roundToInt

// Vibrant golf palette; rough is the dark background showing through.
private val FairwayFill = Color(0xFF4C9A3F)
private val GreenFill = Color(0xFF7CC576)
private val BunkerFill = Color(0xFFE6CE9A)
private val WaterFill = Color(0xFF3E82C2)
private val TeeFill = Color(0xFF6E7C58)
private val FlagRed = Color(0xFFE5484D)

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
            Text(
                text = "Hole ${hole.displayNumber}",
                color = Color.White,
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
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

            // Range rings around the player (or tee), every 50 display units.
            val sc = p.metersToPx
            if (arcOrigin != null && toGreen != null) {
                val o = off(arcOrigin)
                var v = 50.0
                while (units.toMeters(v) <= toGreen + 12.0) {
                    val rPx = (units.toMeters(v) * sc).toFloat()
                    drawCircle(Color.White.copy(alpha = 0.22f), radius = rPx, center = o, style = Stroke(width = 2f))
                    drawContext.canvas.nativeCanvas.drawText(v.toInt().toString(), o.x - 10f, o.y - rPx + 7f, gray)
                    v += 50.0
                }
            }

            // Playing line + your line to the green.
            if (tee != null && center != null) {
                drawLine(Color.White.copy(alpha = 0.6f), off(tee), off(center), strokeWidth = 3f)
            }
            if (me != null && center != null) {
                drawLine(
                    Color.White, off(me), off(center), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)),
                )
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

        // Big distance to the green + direction arrow (top-right).
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = toGreen?.let { formatDistance(it, units) } ?: "—",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            val pl = PlaysLike.toCenter(hole, me)
            if (pl != null && pl.significant) {
                val arrow = if (pl.deltaMeters > 0) "▲" else "▼"
                Text(
                    "plays ${formatDistance(pl.playsLikeMeters, units)} $arrow",
                    color = OdinAmber,
                    style = MaterialTheme.typography.caption2,
                )
            } else {
                Text(units.suffix, color = OdinOnDim, style = MaterialTheme.typography.caption3)
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
                    color = OdinAmber, style = MaterialTheme.typography.caption3,
                )
            }
            if (me == null) {
                Text("waiting for GPS", color = OdinOnDim, style = MaterialTheme.typography.caption3)
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
        Text(
            text = if (me != null && d.centerMeters != null) "C ${formatDistance(d.centerMeters, units)} ${units.suffix}" else "waiting for GPS",
            color = Color.White,
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        )
        Text(
            "Esri, Maxar", color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.caption3,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp),
        )
    }
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
