package com.odinsgolf.data

import android.content.Context
import com.odinsgolf.data.model.Bag
import com.odinsgolf.data.model.ClubDistance
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the player's bag (club planning carries) to files/bag.json. Seeded from
 * [Bag.DEFAULT] until the player edits it, then their numbers stick. Local JSON, no cloud.
 */
class BagRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(context.filesDir, BAG_FILE)

    fun load(): Bag = runCatching {
        if (!file.exists()) Bag.DEFAULT else json.decodeFromString<Bag>(file.readText())
    }.getOrDefault(Bag.DEFAULT)

    fun save(bag: Bag) {
        runCatching { file.writeText(json.encodeToString(bag)) }
    }

    /** Nudge one club's carry by [deltaMeters] (clamped sane), persist, and return the new bag. */
    fun adjustClub(name: String, deltaMeters: Int): Bag {
        val current = load()
        val updated = Bag(
            current.clubs.map {
                if (it.name == name) it.copy(carryMeters = (it.carryMeters + deltaMeters).coerceIn(30, 320))
                else it
            },
        )
        save(updated)
        return updated
    }

    fun setFullBag(on: Boolean): Bag {
        val updated = load().copy(fullBag = on)
        save(updated)
        return updated
    }

    fun resetToDefault(): Bag {
        save(Bag.DEFAULT)
        return Bag.DEFAULT
    }

    companion object {
        const val BAG_FILE = "bag.json"
    }
}
