package com.japanesedrills.quiz

import android.content.Context
import org.json.JSONObject

/** How a lesson has gone so far. [passed] is sticky: a later bad run does not lock it again. */
data class LessonRecord(
    val passed: Boolean = false,
    val bestAccuracy: Double = 0.0,
    val attempts: Int = 0,
)

/**
 * Everything the learner has earned. This is the first state in the app that cannot be
 * recomputed from the assets, so it is written through on every change and never held
 * only in memory.
 *
 * Scheduling happens on two axes because the raw question space is about 10^5 pairs —
 * far too many to schedule individually, and each one would be seen roughly never.
 * [skills] ("past|godan") carries the grammar, [words] carries the vocabulary, and
 * [leeches] records the handful of specific pairings that keep going wrong.
 */
data class Progress(
    val lessons: Map<String, LessonRecord> = emptyMap(),
    val skills: Map<String, SrsState> = emptyMap(),
    val words: Map<String, SrsState> = emptyMap(),
    val leeches: Map<String, Int> = emptyMap(),
) {
    val passed: Set<String> get() = lessons.filterValues { it.passed }.keys

    val isEmpty: Boolean get() = lessons.isEmpty() && skills.isEmpty() && words.isEmpty()

    /**
     * How many skills are ready to be reviewed, of those [reachable] accepts. The one
     * definition of "due" for the UI.
     */
    fun dueCount(today: Long, reachable: (skill: String) -> Boolean = { true }): Int =
        skills.count { (skill, state) -> Scheduler.isDue(state, today) && reachable(skill) }

    companion object {
        /** A pairing missed this often is a leech: it gets picked first in review. */
        const val LEECH_THRESHOLD = 4

        fun leechKey(wordKey: String, type: String) = "$wordKey|$type"
    }
}

/**
 * Turns [Progress] into text and back.
 *
 * The same document is both what gets stored and what the user copies out, so there is
 * one format and one parser rather than a storage format plus an export format that could
 * disagree. Being free of Android types, it is also the part that unit tests can reach.
 */
object ProgressCodec {

    /** Bumped only when the shape changes; [decode] refuses anything newer. */
    const val VERSION = 1

    /** [indent] > 0 pretty-prints, which is what makes an exported backup readable. */
    fun encode(progress: Progress, indent: Int = 0): String {
        val root = render(progress)
        return if (indent > 0) root.toString(indent) else root.toString()
    }

    /** Throws if [text] is not a backup this version understands. */
    fun decode(text: String): Progress {
        val root = JSONObject(text)
        // Demanded rather than defaulted: without it any stray JSON would decode to empty
        // progress and quietly replace the real thing.
        val version = root.getInt("version")
        require(version <= VERSION) { "Backup is from a newer version ($version)" }
        return parse(root)
    }

    fun decodeOrNull(text: String): Progress? = runCatching { decode(text.trim()) }.getOrNull()

    private fun parse(root: JSONObject): Progress {
        val lessons = root.optJSONObject("lessons")?.let { obj ->
            obj.keys().asSequence().associateWith { id ->
                val o = obj.getJSONObject(id)
                LessonRecord(
                    passed = o.optBoolean("passed"),
                    bestAccuracy = o.optDouble("best", 0.0),
                    attempts = o.optInt("attempts"),
                )
            }
        }.orEmpty()
        return Progress(
            lessons = lessons,
            skills = root.optJSONObject("skills").states(),
            words = root.optJSONObject("words").states(),
            leeches = root.optJSONObject("leeches")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getInt(it) }
            }.orEmpty(),
        )
    }

    private fun render(progress: Progress) = JSONObject().apply {
        put("version", VERSION)
        put("lessons", JSONObject().apply {
            progress.lessons.forEach { (id, record) ->
                put(id, JSONObject().apply {
                    put("passed", record.passed)
                    put("best", record.bestAccuracy)
                    put("attempts", record.attempts)
                })
            }
        })
        put("skills", progress.skills.toJson())
        put("words", progress.words.toJson())
        put("leeches", JSONObject().apply { progress.leeches.forEach { (k, v) -> put(k, v) } })
    }

    private fun Map<String, SrsState>.toJson() = JSONObject().apply {
        forEach { (key, state) ->
            put(key, JSONObject().apply {
                put("step", state.step)
                put("ease", state.ease)
                put("due", state.due)
                put("reps", state.reps)
                put("lapses", state.lapses)
            })
        }
    }

    private fun JSONObject?.states(): Map<String, SrsState> = this?.let { obj ->
        obj.keys().asSequence().associateWith { key ->
            val o = obj.getJSONObject(key)
            SrsState(
                step = o.optInt("step", -1),
                ease = o.optDouble("ease", 1.0),
                due = o.optLong("due"),
                reps = o.optInt("reps"),
                lapses = o.optInt("lapses"),
            )
        }
    }.orEmpty()

}

/**
 * Persists [Progress] as one document in its own preferences file.
 *
 * SharedPreferences rather than Room or DataStore: the whole document is tens of KB, which
 * is well inside what it handles, and the alternatives would add an annotation processor or
 * a dependency to a build that has neither. `android:allowBackup` is on, so this rides
 * Android's auto-backup as well.
 */
class ProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun load(): Progress {
        val raw = prefs.getString(KEY, null) ?: return Progress()
        return runCatching { ProgressCodec.decode(raw) }.getOrElse {
            // Corrupt, truncated, or written by a newer version. Starting clean is the only
            // way to open at all, but the next answered question would persist the empty
            // document straight over it, so move the unreadable text aside first: progress
            // is the one thing here that cannot be rebuilt from the assets.
            prefs.edit().putString(SALVAGE_KEY, raw).remove(KEY).apply()
            Progress()
        }
    }

    /** True when [load] had to set a document aside; the settings screen says so. */
    fun hasSalvage(): Boolean = prefs.contains(SALVAGE_KEY)

    /** Drops a set-aside document once imported progress has replaced it. */
    fun discardSalvage() {
        prefs.edit().remove(SALVAGE_KEY).apply()
    }

    fun save(progress: Progress) {
        prefs.edit().putString(KEY, ProgressCodec.encode(progress)).apply()
    }

    /** Clears the progress and any set-aside document; the screen confirms before calling. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY = "data"
        const val SALVAGE_KEY = "unreadable"
    }
}
