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

    fun dueSkills(today: Long): List<String> =
        skills.filterValues { Scheduler.isDue(it, today) }.keys.toList()

    fun dueCount(today: Long): Int = dueSkills(today).size

    companion object {
        /** A pairing missed this often is a leech: it gets picked first in review. */
        const val LEECH_THRESHOLD = 4

        fun leechKey(wordKey: String, type: String) = "$wordKey|$type"
    }
}

/**
 * Persists [Progress] as one JSON document in its own preferences file.
 *
 * SharedPreferences rather than Room or DataStore: the whole document is tens of KB and
 * is written once per answered question, which is well inside what it handles, and the
 * alternatives would add an annotation processor or a dependency to a build that has
 * neither. `android:allowBackup` is on, so this rides Android's auto-backup.
 */
class ProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun load(): Progress {
        val raw = prefs.getString(KEY, null) ?: return Progress()
        return runCatching { parse(raw) }.getOrElse {
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

    fun save(progress: Progress) {
        prefs.edit().putString(KEY, render(progress).toString()).apply()
    }

    /** Clears the progress and any set-aside document; the screen confirms before calling. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun parse(raw: String): Progress {
        val root = JSONObject(raw)
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

    private companion object {
        const val KEY = "data"
        const val SALVAGE_KEY = "unreadable"
        const val VERSION = 1
    }
}
