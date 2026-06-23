package com.odinsgolf.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
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
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.geo.Distances
import com.odinsgolf.geo.Geo
import com.odinsgolf.geo.MapPlan
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.formatDistance
import com.odinsgolf.ui.theme.OdinAmber
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HoleMapScreen(state: GolfUiState) {
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

        val tee = hole.tee
        val center = hole.green.center
        val front = hole.green.front
        val back = hole.green.back
        // Ignore a GPS fix far from the hole (emulator default / stale fix from
        // another course): it would blow up the framing and shrink the hole.
        val rawMe = state.gps.point
        val me = rawMe?.takeIf { center == null || Geo.distanceMeters(it, center) < 2000 }
        val hazards = hole.hazards.map { it.point }

        val points = buildList {
            tee?.let { add(it) }
            center?.let { add(it) }
            front?.let { add(it) }
            back?.let { add(it) }
            me?.let { add(it) }
            addAll(hazards)
            addAll(hole.path)
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val padPx = with(density) { 14.dp.toPx() }

            val plan = remember(hole.number, wPx, hPx, points.size) {
                MapPlan.compute(points, wPx, hPx, padPx)
            }

            // Decoded satellite tiles for this plan; fills in as they download.
            val bitmaps = remember(plan) { mutableStateMapOf<Long, ImageBitmap>() }
            LaunchedEffect(plan) {
                val p = plan ?: return@LaunchedEffect
                p.tiles.forEach { t ->
                    launch {
                        tiles.tile(t.z, t.x, t.y)?.let { bitmaps[t.key] = it.asImageBitmap() }
                    }
                }
            }

            val greenColor = OdinGreen
            val teeColor = OdinOnDim
            val meColor = Color.White
            val hazardColor = OdinAmber
            val lineColor = OdinGreen.copy(alpha = 0.6f)

            Canvas(Modifier.fillMaxSize()) {
                if (plan == null) return@Canvas
                fun off(p: GeoPoint): Offset {
                    val (x, y) = plan.project(p)
                    return Offset(x, y)
                }

                // Satellite base layer. Until tiles arrive (or when offline) the
                // dark Scaffold background shows through and the schematic still reads.
                plan.tiles.forEach { t ->
                    bitmaps[t.key]?.let { img ->
                        drawImage(
                            image = img,
                            dstOffset = IntOffset(t.left.roundToInt(), t.top.roundToInt()),
                            dstSize = IntSize(t.size.roundToInt(), t.size.roundToInt()),
                        )
                    }
                }

                // Tee -> green playing line (always drawn).
                if (tee != null && center != null) {
                    drawLine(Color.White.copy(alpha = 0.85f), off(tee), off(center), strokeWidth = 4f)
                }
                // You -> green (dashed), only with a fix.
                if (me != null && center != null) {
                    drawLine(
                        lineColor, off(me), off(center), strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }
                // Fairway path polyline if present.
                if (hole.path.size >= 2) {
                    for (i in 0 until hole.path.size - 1) {
                        drawLine(Color.White.copy(alpha = 0.5f), off(hole.path[i]), off(hole.path[i + 1]), strokeWidth = 2f)
                    }
                }

                val label = Paint().apply {
                    color = Color.White.toArgb()
                    textSize = 24f
                    isAntiAlias = true
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                }

                hazards.forEachIndexed { i, h ->
                    val o = off(h)
                    drawCircle(hazardColor, radius = 8f, center = o)
                    drawCircle(Color.Black.copy(alpha = 0.6f), radius = 8f, center = o, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    if (hazards.size > 1) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "${i + 1}", o.x + 11f, o.y + 7f, label.apply { color = hazardColor.toArgb() },
                        )
                    }
                }

                center?.let {
                    val o = off(it)
                    drawCircle(greenColor.copy(alpha = 0.30f), radius = 20f, center = o)
                    front?.let { f -> drawCircle(greenColor, radius = 4f, center = off(f)) }
                    back?.let { b -> drawCircle(greenColor, radius = 4f, center = off(b)) }
                    drawCircle(greenColor, radius = 7f, center = o)
                    drawContext.canvas.nativeCanvas.drawText("G", o.x + 14f, o.y + 7f, label.apply { color = greenColor.toArgb() })
                }
                tee?.let {
                    val o = off(it)
                    drawCircle(teeColor, radius = 8f, center = o)
                    drawContext.canvas.nativeCanvas.drawText("T", o.x + 11f, o.y + 7f, label.apply { color = Color.White.toArgb() })
                }
                me?.let {
                    val o = off(it)
                    drawCircle(meColor.copy(alpha = 0.35f), radius = 14f, center = o)
                    drawCircle(meColor, radius = 7f, center = o)
                }
            }

            Text(
                text = "Hole ${hole.displayNumber}",
                color = Color.White,
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            )

            // Bottom overlay: live front/centre/back yardages, or a GPS hint.
            val units = state.settings.units
            val d = Distances.toGreen(hole, me)
            val overlay = if (me != null && d.centerMeters != null) {
                buildString {
                    d.frontMeters?.let { append("F ${formatDistance(it, units)}  ") }
                    append("C ${formatDistance(d.centerMeters, units)}")
                    d.backMeters?.let { append("  B ${formatDistance(it, units)}") }
                    append(" ${units.suffix}")
                }
            } else {
                "waiting for GPS"
            }
            Text(
                text = overlay,
                color = Color.White,
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            )
            Text(
                text = "Esri, Maxar",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.caption3,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp),
            )
        }
    }
}
