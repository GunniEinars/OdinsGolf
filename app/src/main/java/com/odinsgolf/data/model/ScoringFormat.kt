package com.odinsgolf.data.model

/**
 * How the scorecard headlines a round. Both formats record gross strokes per hole
 * (entry is identical); this only changes which result is shown prominently.
 *
 * - [STABLEFORD]: points are the headline (Icelandic club-golf default).
 * - [STROKE_PLAY]: gross total, to-par and net are the headline.
 */
enum class ScoringFormat(val label: String) {
    STABLEFORD("Stableford"),
    STROKE_PLAY("Stroke play");

    companion object {
        fun fromName(name: String?): ScoringFormat =
            entries.firstOrNull { it.name == name } ?: STABLEFORD
    }
}
