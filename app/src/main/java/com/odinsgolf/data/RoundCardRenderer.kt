package com.odinsgolf.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.odinsgolf.data.model.HoleScore
import com.odinsgolf.data.model.Round
import com.odinsgolf.scoring.Scoring
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a round to a shareable square card bitmap using android.graphics
 * (stable API). Mirrors the on-screen Round Summary card.
 */
object RoundCardRenderer {

    // Not const: .toInt() on a Long literal isn't a compile-time constant.
    private val BG = 0xFF1C2026.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val DIM = 0xFF9CA3AF.toInt()
    private val GREEN = 0xFF22C55E.toInt()
    private val AMBER = 0xFFF59E0B.toInt()
    private val RED = 0xFFEF4444.toInt()

    fun render(round: Round): Bitmap {
        val w = 480
        val h = 480
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = w / 2f

        fun text(s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align, bold: Boolean = false) {
            p.color = color
            p.textSize = size
            p.textAlign = align
            p.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            c.drawText(s, x, y, p)
        }

        // The card is square but shown on a ROUND watch (and round-cropped in the gallery), so all
        // content is kept inside the inscribed circle — otherwise the edge labels/scores clip
        // ("OUT" → "UT"). Positions below are tuned to sit within radius ~218 of centre (240,240).
        val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(round.startedEpochMillis))
        text(round.courseName, cx, 64f, 24f, WHITE, Paint.Align.CENTER, bold = true)
        text(date, cx, 90f, 19f, DIM, Paint.Align.CENTER)

        text(Scoring.toParLabel(round.toPar), cx, 192f, 84f, GREEN, Paint.Align.CENTER, bold = true)
        text("to par", cx, 220f, 19f, DIM, Paint.Align.CENTER)

        text("${round.totalStrokes} strokes · ${Scoring.totalStableford(round)} pts", cx, 264f, 23f, WHITE, Paint.Align.CENTER)
        if (round.handicapIndex > 0) {
            val hcp = "%.1f".format(Locale.US, round.handicapIndex)
            text("Net ${Scoring.toParLabel(Scoring.netToPar(round))} · HCP $hcp", cx, 292f, 19f, DIM, Paint.Align.CENTER)
        }

        drawNine(c, p, "OUT", 1..9, round, 342f)
        drawNine(c, p, "IN", 10..18, round, 382f)
        text("OdinsGolf", cx, 424f, 18f, DIM, Paint.Align.CENTER)
        return bmp
    }

    // Round-safe row: the lower (IN) row at y≈382 must fit within the circle, so the label starts at
    // x≈78 and the total ends at x≈398 (both inside the chord ~76..404 there), cells in between.
    private fun drawNine(c: Canvas, p: Paint, label: String, range: IntRange, round: Round, y: Float) {
        p.typeface = Typeface.DEFAULT
        p.textAlign = Paint.Align.LEFT
        p.color = DIM
        p.textSize = 18f
        c.drawText(label, 78f, y, p)

        val holes = round.holes.filter { it.holeNumber in range }
        val left = 128f
        val right = 356f
        val cellW = (right - left) / 9f
        p.textAlign = Paint.Align.CENTER
        p.textSize = 21f
        holes.forEachIndexed { i, hs ->
            p.color = cellColor(hs)
            val x = left + cellW * i + cellW / 2f
            val cell = when {
                hs.pickedUp -> "P"
                hs.entered -> hs.strokes.toString()
                else -> "–"
            }
            c.drawText(cell, x, y, p)
        }

        val sub = round.strokesForRange(range)
        p.color = WHITE
        p.textAlign = Paint.Align.RIGHT
        p.textSize = 22f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText(if (sub == 0) "–" else sub.toString(), 398f, y, p)
    }

    private fun cellColor(s: HoleScore): Int = when {
        s.pickedUp -> AMBER
        !s.entered -> DIM
        s.strokes < s.par -> GREEN
        s.strokes == s.par -> WHITE
        s.strokes == s.par + 1 -> AMBER
        else -> RED
    }
}
