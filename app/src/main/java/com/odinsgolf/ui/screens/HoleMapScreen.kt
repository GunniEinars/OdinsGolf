package com.odinsgolf.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.odinsgolf.data.TileRepository
import com.odinsgolf.data.model.FeatureKind
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.MapStyle
import com.odinsgolf.geo.Carry
import com.odinsgolf.geo.Distances
import com.odinsgolf.geo.Geo
import com.odinsgolf.geo.MapPlan
import com.odinsgolf.geo.PlaysLike
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.formatDistance
import com.odinsgolf.ui.theme.OdinAmber
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Vector palette (rough is the dark background showing through).
private val FairwayFill = Color(0xFF35602F)
private val GreenFill = Color(0xFF5BBF59)
private val BunkerFill = Color(0xFFE0C892)
private val WaterFill = Color(0xFF3E82C2)
private val TeeFill = Color(0xFF6E7C58)

@Composable
fun HoleMapScreen(state: GolfUiState, onToggleMapStyle: () -> Unit) {
    val context = LocalContext.current
    val tiles = remember { TileRepository(context) }
    Scaffold {
        val hole = state.hole
        if (hole == null || !hole.hasGeometry) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No geometry for this hole", color = MaterialTheme.colors.error)
            }
            return@Scaffold
        }

        val satellite = state.settings.mapStyle == MapStyle.SATELLITE
        val tee = hole.tee
        val center = hole.green.center
        val front = hole.green.front
        val back = hole.green.back
        val rawMe = state.gps.point
        val me = rawMe?.takeIf { center == null || Geo.distanceMeters(it, center) < 2000 }
        val hazards = hole.hazards.map { it.point }

        // Everything that should frame in view (whole hole shape for vector).
        val points = buildList {
            tee?.let { add(it) }
            center?.let { add(it) }
            front?.let { add(it) }
            back?.let { add(it) }
            me?.let { add(it) }
            addAll(hazards)
            addAll(hole.path)
            hole.features.forEach { addAll(it.ring) }
        }

        BoxWithConstraints(Modifier.fillMaxSize().clickable(onClick = onToggleMapStyle)) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val padPx = with(density) { 14.dp.toPx() }

            val plan = remember(hole.number, wPx, hPx, points.size, satellite) {
                MapPlan.compute(points, wPx, hPx, padPx)
            }

            val bitmaps = remember(plan) { mutableStateMapOf<Long, ImageBitmap>() }
            LaunchedEffect(plan, satellite) {
                if (!satellite) return@LaunchedEffect
                val p = plan ?: return@LaunchedEffect
                p.tiles.forEach { t ->
                    launch { tiles.tile(t.z, t.x, t.y)?.let { bitmaps[t.key] = it.asImageBitmap() } }
                }
            }

            Canvas(Modifier.fillMaxSize()) {
                if (plan == null) return@Canvas
                fun off(p: GeoPoint): Offset { val (x, y) = plan.project(p); return Offset(x, y) }

                if (satellite) {
                    plan.tiles.forEach { t ->
                        bitmaps[t.key]?.let { img ->
                            drawImage(
                                image = img,
                                dstOffset = IntOffset(t.left.roundToInt(), t.top.roundToInt()),
                                dstSize = IntSize(t.size.roundToInt(), t.size.roundToInt()),
                            )
                        }
                    }
                } else {
                    // Filled vector hole: draw back-to-front by kind.
                    for (kind in FeatureKind.entries) {
                        val fill = when (kind) {
                            FeatureKind.FAIRWAY -> FairwayFill
                            FeatureKind.GREEN -> GreenFill
                            FeatureKind.BUNKER -> BunkerFill
                            FeatureKind.WATER -> WaterFill
                            FeatureKind.TEE -> TeeFill
                        }
                        hole.features.filter { it.kind == kind }.forEach { f -> fillRing(f.ring, fill, ::off) }
                    }
                }

                // Overlays (both modes).
                val lineColor = if (satellite) Color.White else Color.White.copy(alpha = 0.9f)
                if (tee != null && center != null) {
                    drawLine(lineColor.copy(alpha = 0.85f), off(tee), off(center), strokeWidth = 3f)
                }
                if (me != null && center != null) {
                    drawLine(
                        OdinGreen, off(me), off(center), strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }

                val label = Paint().apply {
                    color = Color.White.toArgb(); textSize = 24f; isAntiAlias = true
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                }

                // In satellite mode hazards are only points, so mark them.
                if (satellite) {
                    hazards.forEach { drawCircle(OdinAmber, radius = 7f, center = off(it)) }
                }

                center?.let {
                    val o = off(it)
                    front?.let { f -> drawCircle(Color.White, radius = 3f, center = off(f)) }
                    back?.let { b -> drawCircle(Color.White, radius = 3f, center = off(b)) }
                    drawCircle(Color.White, radius = 4f, center = o)
                    drawContext.canvas.nativeCanvas.drawText("G", o.x + 12f, o.y + 7f, label)
                }
                tee?.let {
                    val o = off(it)
                    drawCircle(OdinOnDim, radius = 5f, center = o)
                    drawContext.canvas.nativeCanvas.drawText("T", o.x + 10f, o.y + 7f, label)
                }
                me?.let {
                    val o = off(it)
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = 13f, center = o)
                    drawCircle(Color.White, radius = 6f, center = o)
                }
            }

            // Header: hole + current style (tap anywhere toggles).
            Text(
                text = "Hole ${hole.displayNumber} · ${if (satellite) "Satellite" else "Vector"}",
                color = Color.White,
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            )

            // Bottom overlay: F/C/B (+ plays-like) and any carries.
            val units = state.settings.units
            val d = Distances.toGreen(hole, me)
            val pl = PlaysLike.toCenter(hole, me)
            val carries = Carry.ahead(hole, me)
            val line1 = if (me != null && d.centerMeters != null) {
                buildString {
                    d.frontMeters?.let { append("F ${formatDistance(it, units)}  ") }
                    append("C ${formatDistance(d.centerMeters, units)}")
                    d.backMeters?.let { append("  B ${formatDistance(it, units)}") }
                    if (pl != null && pl.significant) append("  ~${formatDistance(pl.playsLikeMeters, units)}")
                    append(" ${units.suffix}")
                }
            } else {
                "waiting for GPS"
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (carries.isNotEmpty()) {
                    Text(
                        carries.joinToString("  ") { "carry ${it.label} ${formatDistance(it.carryMeters, units)}" },
                        color = OdinAmber, style = MaterialTheme.typography.caption3,
                    )
                }
                Text(line1, color = Color.White, style = MaterialTheme.typography.caption2)
            }

            if (satellite) {
                Text(
                    "Esri, Maxar",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

private fun DrawScope.fillRing(ring: List<GeoPoint>, color: Color, off: (GeoPoint) -> Offset) {
    if (ring.size < 3) return
    val path = Path()
    ring.forEachIndexed { i, p ->
        val o = off(p)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    drawPath(path, color)
}
