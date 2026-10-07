package com.japanesedrills.quiz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * How a lesson has gone so far: its last [WINDOW] answers, newest in the lowest bit, and how
 * many it has had in all. It exists as soon as a session of the lesson is started, which is
 * what marks the lesson as started on the path.
 */
data class LessonRecord(
    val recent: Int = 0,
    val answered: Int = 0,
    /**
     * Sticky: once the recent answers have cleared the bar, the lesson stays ticked. A bad
     * session later is not a reason to take it away; the bar is what shows fading.
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
     * Whether the recent answers clear the bar on their own, whatever [ready] says, for a lesson
     * that asks [questions] per session.
     */
    fun clearsTheBar(questions: Int): Boolean =
        answered >= minAnswers(questions) && recentAccuracy >= READY_ACCURACY

    fun with(correct: Boolean, questions: Int): LessonRecord {
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
         * The answers a lesson needs before it can be ready: [READY_MIN_ANSWERS], or one whole
         * session where a session is shorter, or a perfect six-question lesson could never
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
 * [lessons] (by lesson id) carries the grammar, [words] carries the vocabulary, and
 * [leeches] records the handful of specific pairings that keep going wrong.
 */
data class Progress(
    val records: Map<String, LessonRecord> = emptyMap(),
    /**
     * Each lesson's review schedule, by lesson id, once it has been graded: a lesson is
     * reviewed on its own questions and nothing else, and moves on its own answers alone.
     */
    val lessons: Map<String, SrsState> = emptyMap(),
    val words: Map<String, SrsState> = emptyMap(),
    val leeches: Map<String, Int> = emptyMap(),
    /**
     * The word sets the learner has put together, by id. Not earned like the rest of this,
     * but their work all the same, and as impossible to rebuild from the assets — so it is
     * stored and backed up in the same document.
     */
    val sets: Map<String, CustomSet> = emptyMap(),
) {
    /** Nothing worth keeping: nothing earned, and no set the learner put together. */
    val isEmpty: Boolean get() = records.isEmpty() && lessons.isEmpty() && words.isEmpty() && sets.isEmpty()

    /**
     * [lesson] graded once on a session's [answers] to it, passing at [LESSON_PASS]. Once
     * per session rather than once per answer: a lesson is asked many times a session, and
     * a schedule step per answer let a single late slip undo the rest.
     */
    fun withLessonGraded(lesson: String, answers: List<Boolean>, day: Long): Progress {
        val passed = answers.count { it } >= LESSON_PASS * answers.size
        return copy(lessons = lessons + (lesson to Scheduler.review(lessons[lesson] ?: SrsState(), passed, day)))
    }

    companion object {
        /** A pairing missed this often is a leech: it gets picked first in review. */
        const val LEECH_THRESHOLD = 4

        /** The share of a lesson's questions in one session that passes it ([withLessonGraded]). */
        const val LESSON_PASS = 0.8

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
     * the old gated course, whose records mean nothing on today's path; version 2 was
     * before the learner could put word sets together; version 3 scheduled review by skill
     * (a question type on a word group) rather than by lesson.
     */
    const val VERSION = 4

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
        val records = root.optJSONObject("records")?.let { obj ->
            obj.keys().asSequence().associateWith { id ->
                val o = obj.getJSONObject(id)
                LessonRecord(recent = o.optInt("recent"), answered = o.optInt("answered"), ready = o.optBoolean("ready"))
            }
        }.orEmpty()
        return Progress(
            records = records,
            lessons = root.optJSONObject("lessons").states(),
            words = root.optJSONObject("words").states(),
            leeches = root.optJSONObject("leeches")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getInt(it) }
            }.orEmpty(),
            sets = root.optJSONObject("sets")?.let { obj ->
                obj.keys().asSequence().associateWith { id ->
                    val set = obj.getJSONObject(id)
                    val words = set.getJSONArray("words")
                    CustomSet(
                        id = id,
                        name = set.getString("name"),
                        words = (0 until words.length()).mapTo(LinkedHashSet()) { words.getString(it) },
                    )
                }
            }.orEmpty(),
        )
    }

    private fun render(progress: Progress) = JSONObject().apply {
        put("version", VERSION)
        put("records", JSONObject().apply {
            progress.records.forEach { (id, record) ->
                put(id, JSONObject().apply {
                    put("recent", record.recent)
                    put("answered", record.answered)
                    put("ready", record.ready)
                })
            }
        })
        put("lessons", progress.lessons.toJson())
        put("words", progress.words.toJson())
        put("leeches", JSONObject().apply { progress.leeches.forEach { (k, v) -> put(k, v) } })
        put("sets", JSONObject().apply {
            progress.sets.forEach { (id, set) ->
                put(id, JSONObject().apply {
                    put("name", set.name)
                    put("words", JSONArray(set.words.toList()))
                })
            }
        })
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
            // Readable, but from an older path: its review schedules mean nothing to this
            // one, so starting clean is the point.
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
