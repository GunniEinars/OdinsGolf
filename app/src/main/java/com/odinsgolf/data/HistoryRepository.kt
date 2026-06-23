package com.odinsgolf.data

import android.content.Context
import com.odinsgolf.data.model.Round
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stores rounds the user chose to keep. Manual save only — not every round is
 * kept. Newest first. Local JSON, no cloud.
 */
class HistoryRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(context.filesDir, HISTORY_FILE)

    fun load(): List<Round> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<Round>>(file.readText())
    }.getOrDefault(emptyList())

    /** Prepend [round] to history (newest first), capped to [MAX] entries. */
    fun add(round: Round): List<Round> {
        val updated = (listOf(round) + load()).take(MAX)
        runCatching { file.writeText(json.encodeToString(updated)) }
        return updated
    }

    fun delete(startedEpochMillis: Long): List<Round> {
        val updated = load().filterNot { it.startedEpochMillis == startedEpochMillis }
        runCatching { file.writeText(json.encodeToString(updated)) }
        return updated
    }

    companion object {
        const val HISTORY_FILE = "round_history.json"
        const val MAX = 100
    }
}
