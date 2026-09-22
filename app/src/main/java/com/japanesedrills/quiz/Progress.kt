package com.japanesedrills.quiz

import android.content.Context
import org.json.JSONObject

/**
 * How a step has gone so far: its last [WINDOW] answers, newest in the lowest bit, and how
 * many it has had in all. It exists as soon as the step is first opened, which is how the
 * introduction knows to show only once.
 */
data class StepRecord(
    val recent: Int = 0,
    val answered: Int = 0,
    /**
     * Sticky: once the recent answers have cleared the bar, the step stays ticked. A bad
     * session later is not a reason to take it away; the ring is what shows fading.
     */
    val ready: Boolean = false,
) {
    /** How many of the recent answers were right, over how many there are. */
    val recentAccuracy: Double
        get() {
            val window = minOf(answered, WINDOW)
            return if (window == 0) 0.0 else Integer.bitCount(recent) / window.toDouble()
        }

    /**
     * Whether the recent answers clear the bar on their own, whatever [ready] says, for a step
     * that asks [questions] per session.
     */
    fun clearsTheBar(questions: Int): Boolean =
        answered >= minAnswers(questions) && recentAccuracy >= READY_ACCURACY

    fun with(correct: Boolean, questions: Int): StepRecord {
        val next = copy(
            recent = ((recent shl 1) or (if (correct) 1 else 0)) and WINDOW_MASK,
            answered = answered + 1,
        )
        return if (next.clearsTheBar(questions)) next.copy(ready = true) else next
    }

    companion object {
        const val WINDOW = 20
        private const val WINDOW_MASK = (1 shl WINDOW) - 1

        /** Enough answers that one lucky run is not the whole of the evidence. */
        const val READY_MIN_ANSWERS = 12
        const val READY_ACCURACY = 0.85

        /**
         * The answers a step needs before it can be ready: [READY_MIN_ANSWERS], or one whole
         * session where a session is shorter, or a perfect six-question step could never
         * be ready on the day it was played.
         */
        fun minAnswers(questions: Int): Int = minOf(READY_MIN_ANSWERS, questions)
    }
}

/**
 * Everything the learner has earned. This is the one state in the app that cannot be
 * recomputed from the assets, so it is written through on every change and never held
 * only in memory.
 *
 * Scheduling happens on two axes because the raw question space is about 10^5 pairs —
 * far too many to schedule individually, and each one would be seen roughly never.
 * [skills] ("past|godan") carries the grammar, [words] carries the vocabulary, and
 * [leeches] records the handful of specific pairings that keep going wrong.
 */
data class Progress(
    val steps: Map<String, StepRecord> = emptyMap(),
    val skills: Map<String, SrsState> = emptyMap(),
    val words: Map<String, SrsState> = emptyMap(),
    val leeches: Map<String, Int> = emptyMap(),
) {
    val isEmpty: Boolean get() = steps.isEmpty() && skills.isEmpty() && words.isEmpty()

    /**
     * How many skills are ready to be reviewed: the one definition of "due" for the UI.
     * Every skill here was practised, so a review can reach every one of them.
     */
    fun dueCount(today: Long): Int = skills.values.count { Scheduler.isDue(it, today) }

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

    /**
     * Bumped only when the shape changes; [decode] refuses any other version. Version 1 was
     * the gated lesson path, whose records mean nothing on the step path.
     */
    const val VERSION = 2

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
        require(version == VERSION) { "Backup is from an older version ($version)" }
        return parse(root)
    }

    /** True for a document from before the current version, which is dropped rather than read. */
    fun isOutdated(text: String): Boolean =
        runCatching { JSONObject(text).getInt("version") < VERSION }.getOrDefault(false)

    fun decodeOrNull(text: String): Progress? = runCatching { decode(text.trim()) }.getOrNull()

    private fun parse(root: JSONObject): Progress {
        val steps = root.optJSONObject("steps")?.let { obj ->
            obj.keys().asSequence().associateWith { id ->
                val o = obj.getJSONObject(id)
                StepRecord(recent = o.optInt("recent"), answered = o.optInt("answered"), ready = o.optBoolean("ready"))
            }
        }.orEmpty()
        return Progress(
            steps = steps,
            skills = root.optJSONObject("skills").states(),
            words = root.optJSONObject("words").states(),
            leeches = root.optJSONObject("leeches")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getInt(it) }
            }.orEmpty(),
        )
    }

    private fun render(progress: Progress) = JSONObject().apply {
        put("version", VERSION)
        put("steps", JSONObject().apply {
            progress.steps.forEach { (id, record) ->
                put(id, JSONObject().apply {
                    put("recent", record.recent)
                    put("answered", record.answered)
                    put("ready", record.ready)
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
        if (ProgressCodec.isOutdated(raw)) {
            // Readable, but from the lesson path: its review schedules include forms the
            // step path only reaches at the end, so starting clean is the point.
            prefs.edit().remove(KEY).apply()
            return Progress()
        }
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
