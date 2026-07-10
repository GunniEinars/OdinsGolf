package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.odinsgolf.data.model.Bag
import com.odinsgolf.data.model.GpsStatus
import com.odinsgolf.data.model.PinDepth
import com.odinsgolf.data.model.ScoringFormat
import com.odinsgolf.data.model.Weather
import com.odinsgolf.data.model.Wind
import com.odinsgolf.geo.Caddie
import com.odinsgolf.geo.Carry
import com.odinsgolf.geo.Distances
import com.odinsgolf.geo.Geo
import com.odinsgolf.geo.PlaysLike
import com.odinsgolf.scoring.Scoring
import com.odinsgolf.ui.GolfUiState
import com.odinsgolf.ui.components.DebugGpsReadout
import com.odinsgolf.ui.components.GpsStatusPill
import com.odinsgolf.ui.components.formatDistance
import com.odinsgolf.ui.components.rotaryScroll
import com.odinsgolf.ui.theme.OdinAmber
import com.odinsgolf.ui.theme.OdinGreen
import com.odinsgolf.ui.theme.OdinOnDim

/**
 * The glanceable home: hole + the hero centre distance, front/back, plays-like and
 * any carry — one screen. Map and scorecard are a swipe away (the pager), and
 * everything occasional is behind the single More chip.
 */
@Composable
fun DistanceScreen(
    state: GolfUiState,
    bag: Bag,
    weather: Weather?,
    onPrevHole: () -> Unit,
    onNextHole: () -> Unit,
    onSelectHole: (Int) -> Unit,
    onSetPin: (PinDepth) -> Unit,
    onOpenMore: () -> Unit,
) {
    Scaffold(timeText = { TimeText() }) {
        // Course JSON parses off the main thread on cold start; show a clean placeholder until the
        // (light) course is ready, instead of a bare half-drawn screen.
        if (state.loading) {
            LoadingCourse()
            return@Scaffold
        }
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .rotaryScroll(scroll)
                .padding(horizontal = 14.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val hole = state.hole
            val units = state.settings.units

            // Hole navigation: ‹  H7 · Par 4  ›
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NavArrow("‹", onPrevHole)
                Text(
                    text = if (hole != null) "H${hole.displayNumber} · Par ${hole.par}" else "—",
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.SemiBold,
                )
                NavArrow("›", onNextHole)
            }

            // Hole length (tee→green) + stroke index — tee strategy at a glance.
            if (hole != null) {
                val tee = hole.tee
                val center = hole.green.center
                val len = if (tee != null && center != null) Geo.distanceMeters(tee, center) else null
                val siText = hole.strokeIndex?.let { " · SI $it" } ?: ""
                if (len != null) {
                    Text(
                        "${formatDistance(len, units)} ${units.suffix}$siText",
                        color = OdinOnDim,
                        style = MaterialTheme.typography.caption2,
                    )
                }
            }

            // Net-play cue: handicap strokes you receive on this hole.
            val shotsHere = state.round?.let { Scoring.strokesReceived(Scoring.playingHandicap(it), hole?.strokeIndex) } ?: 0
            if (shotsHere > 0) {
                Text(
                    "+$shotsHere shot${if (shotsHere > 1) "s" else ""} here",
                    color = OdinAmber,
                    style = MaterialTheme.typography.caption2,
                )
            }

            Spacer(Modifier.height(4.dp))

            if (state.loadError != null) {
                Text(state.loadError, color = MaterialTheme.colors.error, textAlign = TextAlign.Center)
            } else if (hole != null && !hole.hasGeometry) {
                Text(
                    "Course geometry missing.\nCapture points in Survey.",
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2,
                )
            } else if (hole != null) {
                val d = Distances.toGreen(hole, state.gps.point)
                // Hero tracks today's pin (front/middle/back), so the big number is the one
                // you actually club to — front↔back can be a club or two on these greens.
                val pin = state.settings.pinDepth
                val pinMeters = when (pin) {
                    PinDepth.FRONT -> d.frontMeters
                    PinDepth.BACK -> d.backMeters
                    PinDepth.MIDDLE -> d.centerMeters
                }
                val pinName = when (pin) {
                    PinDepth.FRONT -> "front"
                    PinDepth.BACK -> "back"
                    PinDepth.MIDDLE -> "center"
                }
                // Honest hero: a stale or absent fix is dimmed and flagged, so an
                // out-of-date yardage never looks live.
                val stale = state.gpsStatus == GpsStatus.STALE_FIX
                val hasFix = state.gps.point != null
                val heroColor = if (!hasFix || stale) OdinOnDim else OdinGreen

                Text(
                    text = formatDistance(pinMeters, units),
                    color = heroColor,
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (stale) "stale fix · $pinName" else "$pinName · ${units.suffix}",
                    color = if (stale) OdinAmber else OdinOnDim,
                    style = MaterialTheme.typography.caption2,
                )

                // Plays-like = elevation + wind/rain, applied to the chosen pin target.
                val pl = PlaysLike.toCenter(hole, state.gps.point)
                val elevDelta = pl?.takeIf { it.significant }?.deltaMeters ?: 0.0
                val center = hole.green.center
                val shotBearing = state.gps.point?.let { me -> center?.let { c -> Geo.bearingDegrees(me, c) } }
                val windDelta = if (weather != null && shotBearing != null) Wind.playingDelta(weather, shotBearing) else 0.0
                val totalDelta = elevDelta + windDelta

                if (pinMeters != null && abs(totalDelta) >= 3.0) {
                    val arrow = if (totalDelta > 0) "↑" else "↓"
                    Text(
                        "plays ${formatDistance(pinMeters + totalDelta, units)} $arrow",
                        color = OdinAmber,
                        style = MaterialTheme.typography.title3,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Why it plays differently — wind direction/strength + rain.
                if (weather != null && shotBearing != null) {
                    val hw = Wind.headwindMps(weather, shotBearing)
                    val windLabel = when {
                        hw >= 1.0 -> "↑ headwind ${hw.roundToInt()} m/s"
                        hw <= -1.0 -> "↓ tailwind ${(-hw).roundToInt()} m/s"
                        weather.windSpeedMps >= 2.0 -> "→ cross ${weather.windSpeedMps.roundToInt()} m/s"
                        else -> null
                    }
                    val parts = listOfNotNull(windLabel, if (Wind.isRaining(weather)) "rain" else null)
                    if (parts.isNotEmpty()) {
                        Text(parts.joinToString(" · "), color = OdinOnDim, style = MaterialTheme.typography.caption3)
                    }
                }

                // Caddie club (live fix only — a club off a stale yardage would mislead):
                // on/near the tee of a two-shotter, suggest a position club that leaves a full
                // scoring iron; otherwise the club for the plays-like, wind-adjusted pin target.
                val caddieTarget = pinMeters?.let { it + totalDelta }
                val holeLen = hole.tee?.let { t -> center?.let { c -> Geo.distanceMeters(t, c) } }
                val nearTee = holeLen != null && pinMeters != null && hole.par >= 4 && pinMeters > holeLen * 0.7
                if (hasFix && !stale) {
                    if (nearTee && holeLen != null) {
                        Caddie.tee(bag.activeClubs, holeLen)?.let { t ->
                            Text(
                                "Tee ${t.club.name}",
                                color = OdinGreen,
                                style = MaterialTheme.typography.title3,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "leaves ~${formatDistance(t.leavesMeters.toDouble(), units)} ${units.suffix}",
                                color = OdinOnDim,
                                style = MaterialTheme.typography.caption3,
                            )
                        }
                    } else if (caddieTarget != null && pinMeters != null && pinMeters >= Caddie.MIN_APPROACH_METERS) {
                        // On/near the green it's a chip or putt — no club to name.
                        Caddie.approach(bag.activeClubs, caddieTarget)?.let { pick ->
                            Text(
                                "→ ${pick.club.name}" + if (pick.overClub) " · layup" else "",
                                color = OdinGreen,
                                style = MaterialTheme.typography.title3,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Pin depth selector: a light one-tap Front/Mid/Back so the hero clubs to the
                // pin, not just the centre. Active target is highlighted; kept visually quiet
                // so it never competes with the yardage. (The subtitle above names the target.)
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    PinSeg("Front", pin == PinDepth.FRONT) { onSetPin(PinDepth.FRONT) }
                    PinSeg("Mid", pin == PinDepth.MIDDLE) { onSetPin(PinDepth.MIDDLE) }
                    PinSeg("Back", pin == PinDepth.BACK) { onSetPin(PinDepth.BACK) }
                }

                // Nearest-hole hint: on a compact course it's easy to leave the watch on
                // the wrong hole and read a real yardage to the wrong green. Offer a
                // one-tap switch; it only shows when another hole is clearly closer.
                state.suggestedHole?.let { n ->
                    Spacer(Modifier.height(6.dp))
                    CompactChip(
                        onClick = { onSelectHole(n) },
                        label = { Text("⚑ At Hole $n? tap to switch", color = OdinAmber) },
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Front / Back.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    LabeledValue("Front", formatDistance(d.frontMeters, units))
                    LabeledValue("Back", formatDistance(d.backMeters, units))
                }

                // Where you stand in the round — so you never have to swipe to the card to
                // check. Stableford shows points; stroke play shows to-par. Hidden until you
                // enter your first score.
                state.round?.let { r ->
                    val thru = r.enteredHoles.size
                    if (thru > 0) {
                        val scoreText = if (state.settings.scoringFormat == ScoringFormat.STABLEFORD) {
                            "${Scoring.totalStableford(r)} pts · thru $thru"
                        } else {
                            "${Scoring.toParLabel(r.toPar)} · thru $thru"
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(scoreText, color = OdinGreen, style = MaterialTheme.typography.caption2)
                    }
                }

                // Carry over hazards ahead and within reach (the actionable hazard info).
                val carries = Carry.ahead(hole, state.gps.point)
                if (carries.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    carries.forEach { c ->
                        Text(
                            "carry ${c.label}  ${formatDistance(c.carryMeters, units)} ${units.suffix}",
                            color = OdinAmber,
                            style = MaterialTheme.typography.caption2,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            GpsStatusPill(state.gps, state.nowElapsed, state.settings.gpsMode.staleAfterMillis, state.settings.debugGps)
            if (state.settings.playMode) {
                Text("▶ Play mode", color = OdinGreen, style = MaterialTheme.typography.caption3)
            }
            if (state.settings.debugGps) {
                Spacer(Modifier.height(4.dp))
                DebugGpsReadout(state.gps, state.nowElapsed)
            }

            Spacer(Modifier.height(10.dp))
            CompactChip(label = { Text("More") }, onClick = onOpenMore)
        }
    }
}

@Composable
private fun LoadingCourse() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(10.dp))
            Text("Loading course…", color = OdinOnDim, style = MaterialTheme.typography.caption1)
        }
    }
}

@Composable
private fun PinSeg(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) OdinGreen else OdinOnDim,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        style = MaterialTheme.typography.caption1,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun NavArrow(symbol: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier.height(36.dp),
    ) {
        Text(symbol, fontSize = 20.sp)
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.title2, fontWeight = FontWeight.SemiBold)
        Text(label, color = OdinOnDim, style = MaterialTheme.typography.caption3)
    }
}
