package com.odinsgolf.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.geo.CanvasProjector
import com.odinsgolf.geo.Distances
import com.odinsgolf.geo.Geo
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.formatDistance
import com.odinsgolf.ui.theme.OdinAmber
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim

@Composable
fun HoleMapScreen(state: GolfUiState) {
    Scaffold {
        val hole = state.hole
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (hole == null || !hole.hasGeometry) {
                Text(
                    "No geometry for this hole",
                    color = MaterialTheme.colors.error,
                )
                return@Box
            }

            val tee = hole.tee
            val center = hole.green.center
            val front = hole.green.front
            val back = hole.green.back
            // Ignore a GPS fix that is far from the hole (e.g. emulator default
            // location, or a stale fix from another course): including it would
            // blow up the bounding box and collapse the hole to a single dot.
            val rawMe = state.gps.point
            val me = rawMe?.takeIf { center == null || Geo.distanceMeters(it, center) < 2000 }
            val hazards = hole.hazards.map { it.point }

            // All points that must fit in view.
            val allPoints = buildList {
                tee?.let { add(it) }
                center?.let { add(it) }
                front?.let { add(it) }
                back?.let { add(it) }
                me?.let { add(it) }
                addAll(hazards)
                addAll(hole.path)
            }

            val teeColor = OdinOnDim
            val greenColor = OdinGreen
            val meColor = Color.White
            val hazardColor = OdinAmber
            val lineColor = OdinGreen.copy(alpha = 0.5f)

            Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                val proj = CanvasProjector.create(allPoints, size.width, size.height, 8f) ?: return@Canvas

                fun off(p: GeoPoint): Offset {
                    val pt = proj.project(p)
                    return Offset(pt.x, pt.y)
                }

                // Playing line tee -> green, always drawn so the hole reads even
                // without a GPS fix (e.g. indoors / before the first fix).
                if (tee != null && center != null) {
                    drawLine(
                        color = OdinGreen.copy(alpha = 0.7f),
                        start = off(tee),
                        end = off(center),
                        strokeWidth = 4f,
                    )
                }

                // Your position -> green (dashed); only when a fix is available.
                if (me != null && center != null) {
                    drawLine(
                        color = lineColor,
                        start = off(me),
                        end = off(center),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }

                // Fairway path polyline if present.
                if (hole.path.size >= 2) {
                    for (i in 0 until hole.path.size - 1) {
                        drawLine(OdinOnDim.copy(alpha = 0.4f), off(hole.path[i]), off(hole.path[i + 1]), strokeWidth = 2f)
                    }
                }

                val label = Paint().apply {
                    color = OdinOnDim.toArgb()
                    textSize = 22f
                    isAntiAlias = true
                }

                hazards.forEachIndexed { i, h ->
                    val o = off(h)
                    drawCircle(hazardColor, radius = 8f, center = o)
                    if (hazards.size > 1) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "${i + 1}", o.x + 10f, o.y + 6f,
                            label.apply { color = hazardColor.toArgb() },
                        )
                    }
                }

                // Green: a translucent body spanning front..back, with the centre pin.
                center?.let {
                    val o = off(it)
                    drawCircle(greenColor.copy(alpha = 0.22f), radius = 20f, center = o)
                    front?.let { f -> drawCircle(greenColor.copy(alpha = 0.8f), radius = 4f, center = off(f)) }
                    back?.let { b -> drawCircle(greenColor.copy(alpha = 0.8f), radius = 4f, center = off(b)) }
                    drawCircle(greenColor, radius = 7f, center = o)
                    drawContext.canvas.nativeCanvas.drawText("G", o.x + 14f, o.y + 6f, label.apply { color = greenColor.toArgb() })
                }
                tee?.let {
                    val o = off(it)
                    drawCircle(teeColor, radius = 8f, center = o)
                    drawContext.canvas.nativeCanvas.drawText("T", o.x + 10f, o.y + 6f, label.apply { color = teeColor.toArgb() })
                }
                me?.let {
                    val o = off(it)
                    drawCircle(meColor, radius = 7f, center = o)
                    drawCircle(meColor.copy(alpha = 0.3f), radius = 13f, center = o)
                }
            }

            Text(
                text = "Hole ${hole.displayNumber}",
                color = OdinOnDim,
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
                color = OdinOnDim,
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}
