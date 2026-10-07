package com.japanesedrills.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.japanesedrills.data.DrillData
import com.japanesedrills.data.Word
import com.japanesedrills.dev.DevTools
import com.japanesedrills.quiz.CustomSet
import com.japanesedrills.quiz.Drawn
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.Grammar
import com.japanesedrills.quiz.GrammarExamples
import com.japanesedrills.quiz.GrammarNote
import com.japanesedrills.quiz.LearnPath
import com.japanesedrills.quiz.OptionsStore
import com.japanesedrills.quiz.Palette
import com.japanesedrills.quiz.PracticePreset
import com.japanesedrills.quiz.Progress
import com.japanesedrills.quiz.ProgressCodec
import com.japanesedrills.quiz.ProgressStore
import com.japanesedrills.quiz.Question
import com.japanesedrills.quiz.QuestionPool
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.RomajiConverter
import com.japanesedrills.quiz.Scheduler
import com.japanesedrills.quiz.SrsState
import com.japanesedrills.quiz.Lesson
import com.japanesedrills.quiz.LessonRecord
import com.japanesedrills.quiz.ThemeChoice
import com.japanesedrills.quiz.WordColumn
import com.japanesedrills.quiz.WordSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The three places to be. Learn is first and the default: it is the one screen that tells
 * a newcomer what to do. Settings is not among them — it is a destination, reached from
 * the bar, not somewhere you spend time.
 */
enum class Tab(val label: String) {
    Learn("Learn"),
    Practice("Practice"),
    Grammar("Grammar"),
}

enum class Screen { Root, LessonIntro, Quiz, Results, Settings, About, ConjugationIntro, GrammarDetail, WordSet }

/**
 * What the path suggests doing next, once review has been counted. The lesson is offered
 * when review is solid; otherwise the path says which of the two it would rather have.
 */
sealed interface Recommendation {
    /** Review is solid enough to take on something new. */
    data class NextLesson(val lesson: Lesson) : Recommendation

    /** Review has a backlog: clearing it is worth more than another lesson. */
    data object ImproveReview : Recommendation

    /** Every lesson is ready. From here the app is review and free practice. */
    data object Done : Recommendation
}

/** Which of the three things the running quiz is. */
enum class SessionKind { Practice, Lesson, Review }

data class HistoryEntry(val question: Question, val response: String) {
    /** Decided once: the score badge, the grading and the results all ask again. */
    val correct: Boolean = question.isCorrect(response)

    /**
     * What was typed, as the accepted spelling it matches when there is one: that carries
     * the readings the typed text does not, so a right answer in kanji shows its furigana.
     */
    val responseDisplay: String = question.answers.firstOrNull { Furigana.toKanji(it) == response } ?: response
}

data class QuizState(
    val total: Int,
    val question: Question,
    val history: List<HistoryEntry> = emptyList(),
    /** The answer to [question] once it has been given; it is also the last [history] entry. */
    val answer: HistoryEntry? = null,
    val showExplanation: Boolean = false,
    /**
     * Incremented on every rejected submission to trigger the shake animation. Per question:
     * the answer field is rebuilt for each one and replays its shake for any count above
     * zero, so a count carried over made every later question shake as it appeared.
     */
    val shakes: Int = 0,
)

/** What the current settings add up to: how many words, and how many questions from them. */
data class PoolCounts(
    val words: Int,
    val questions: Int,
    /** Sourced words the grid asks nothing about, by column ([QuestionPool.skipped]). */
    val skipped: Map<String, Int> = emptyMap(),
)

/** One row on the learn path. */
data class LessonCard(
    val lesson: Lesson,
    /** Opened at least once, so its introduction has been seen. */
    val started: Boolean,
    /** It introduces something, so there is an introduction to reopen. */
    val hasIntro: Boolean,
    /** Its recent answers have cleared the bar at some point; see [LessonRecord.ready]. */
    val ready: Boolean,
    /** How well its content is holding up in review, 0f..1f; see [LearnPath.strength]. */
    val strength: Float,
)

/** How a session of a lesson went, shown on the results screen. */
data class LessonOutcome(
    val lesson: Lesson,
    val accuracy: Double,
    val record: LessonRecord,
    /** This session is what took the lesson over the bar. */
    val becameReady: Boolean,
    /** What the path recommends now, if anything is left to recommend. */
    val next: Lesson?,
)

data class DrillUiState(
    val loading: Boolean = true,
    val options: QuizOptions = QuizOptions(),
    /** Null while the pool for the current options is being counted. */
    val pool: PoolCounts? = null,
    val tab: Tab = Tab.Learn,
    val screen: Screen = Screen.Root,
    val quiz: QuizState? = null,
    /** Options the running quiz was started with. */
    val quizOptions: QuizOptions = QuizOptions(),
    val kind: SessionKind = SessionKind.Practice,
    val progress: Progress = Progress(),
    val path: List<LessonCard> = emptyList(),
    /**
     * The lesson the path recommends: the earliest not yet ready, even when the learner has
     * jumped ahead, since everything after it builds on it. Its chapter starts unfolded.
     */
    val nextLesson: Lesson? = null,
    /** How many lessons review is waiting on: the note under the Review row. */
    val dueCount: Int = 0,
    /** How long the review it would start is, in questions: the Review row's own line. */
    val dueQuestions: Int = 0,
    /**
     * Set once review has been counted. Until then the path offers nothing, so a
     * recommendation cannot flash up and vanish when review turns out to have work.
     */
    val reviewCounted: Boolean = false,
    /** Whether review's backlog is small enough to take on a new lesson ([ReviewLoad]). */
    val reviewSolid: Boolean = true,
    /** The lesson being introduced or drilled. */
    val lesson: Lesson? = null,
    /** New vocabulary to present before [lesson] starts. */
    val introWords: List<Word> = emptyList(),
    /** New grammar to present before [lesson] starts. */
    val introForms: List<GrammarNote> = emptyList(),
    /** Word classes [lesson] introduces, presented before its forms and words. */
    val introClasses: List<GrammarNote> = emptyList(),
    /**
     * Chapters the learner has folded or unfolded by hand, by title. Kept here rather than in
     * the path screen, which leaves composition for every session and would forget. Only for
     * the session: the defaults already follow progress, so they are right on the next launch.
     */
    val chapterOpen: Map<String, Boolean> = emptyMap(),
    /**
     * Where closing the Conjugation Intro returns to: it opens from the Grammar tab and from
     * lessons.
     */
    val conjugationIntroFrom: Screen = Screen.Root,
    /** The form being read about on the Grammar tab. */
    val grammarNote: GrammarNote? = null,
    /** The word set being put together, if the editor is open. */
    val editingSet: String? = null,
    /** Every word there is, for the word-set editor to pick from. */
    val words: List<Word> = emptyList(),
    /** How many words each built-in set holds, beside its name on the practice screen. */
    val setSizes: Map<String, Int> = emptyMap(),
    /** Representative words for showing how a form is built. */
    val grammarExamples: GrammarExamples = GrammarExamples(),
    /**
     * The forms each grid column has at all, so the grid leaves out what cannot exist: an
     * adjective has no passive, and no square is drawn for one.
     */
    val columnForms: Map<String, Set<String>> = emptyMap(),
    /**
     * The class columns the chosen words fall into, which is what the grid draws. It keeps
     * the last answer while a new count runs: counting again would otherwise put every
     * column back for a moment, and the grid grew and shrank under the finger that tapped it.
     */
    val columns: Set<String> = QuizOptions.COLUMNS.mapTo(LinkedHashSet()) { it.key },
    val outcome: LessonOutcome? = null,
    /** Set when stored progress could not be read and was put aside rather than overwritten. */
    val salvagedProgress: Boolean = false,
) {
    /**
     * What to do next, as the path sees it. A lesson once review is solid; otherwise
     * review itself, which is what moves every lesson already taken. The row is always
     * there, so "nothing new yet" is something the path says rather than something the
     * learner has to infer from an absence.
     */
    val recommendation: Recommendation?
        get() = when {
            !reviewCounted -> null
            nextLesson == null -> Recommendation.Done
            reviewSolid -> Recommendation.NextLesson(nextLesson)
            else -> Recommendation.ImproveReview
        }

    /** Whether there is a review at all: before the first lesson there is nothing to hold up. */
    val hasReview: Boolean get() = progress.lessons.isNotEmpty()

    /**
     * Which half of the today panel is lit. Review while it has work to do, and the
     * recommendation once it has none — [hasReview] only says review exists, so lighting
     * on that left "Nothing due" as the loudest thing on the screen for the rest of the day.
     */
    val reviewLeads: Boolean get() = dueCount > 0 || recommendation == null

    val canStart: Boolean
        get() = !loading && options.hasPoliteness && options.questionCount != null && (pool?.questions ?: 0) > 0

    /**
     * The learn path has begun: opening a lesson already counts, and it would be odd to still
     * be told nothing has. A word set made on the practice tab is not a lesson taken, which
     * is why this is not simply "there is something in the document".
     */
    val started: Boolean get() = progress.onPath

    /**
     * The set a wrong word can be dropped from while drilling: the one set the session is
     * drawing on, when that set is the learner's own. Two sets at once, or every word there
     * is, and the question does not say which set to drop it from.
     */
    val droppableSet: CustomSet?
        get() = if (kind == SessionKind.Practice && !quizOptions.allWords) {
            progress.sets[quizOptions.sets.singleOrNull()]
        } else {
            null
        }
}

class DrillViewModel(application: Application) : AndroidViewModel(application) {

    private val store = OptionsStore(application)
    private val progressStore = ProgressStore(application)
    private val _state = MutableStateFlow(
        DrillUiState(
            options = store.load(),
            progress = progressStore.load(),
            // Read after load(), which is what sets it.
            salvagedProgress = progressStore.hasSalvage(),
        )
    )
    val state: StateFlow<DrillUiState> = _state.asStateFlow()

    private var data: DrillData? = null
    private var engine: QuizEngine? = null
    private var pool: QuestionPool? = null
    private var poolJob: Job? = null

    /** The session being drawn, so a second tap on Start while it is cannot start another. */
    private var startJob: Job? = null

    /**
     * The rest of the running session's questions, each with the lesson it is asked for.
     * Every session kind draws its questions up front so none of them can ask the same pair
     * twice while unasked ones remain, and so that a lesson can be graded once its last
     * question in the session has been answered.
     */
    private var queue: MutableList<Drawn> = mutableListOf()

    /** Whether the running lesson was already ready when it started, to tell when it became so. */
    private var wasReady = false

    /** The date everything is scheduled against; a debug build can move it forward ([DevTools]). */
    private val today: Long get() = LocalDate.now().toEpochDay() + DevTools.dayOffset

    /** Re-reckons everything that depends on the date, after the developer clock has moved. */
    fun refreshDay() = refreshPath()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { DrillData.load(getApplication()) }
            data = loaded
            val loadedEngine = QuizEngine(loaded)
            loadedEngine.customSets = _state.value.progress.sets
            engine = loadedEngine
            val examples = GrammarExamples(
                Grammar.EXAMPLE_KEYS.mapNotNull(loaded.wordsByKey::get).associateBy { it.key },
                loaded.ownForms,
            )
            val columnForms = QuizOptions.COLUMNS.associate { column ->
                // "plain" is implied by every conjugation rather than named by one, so every
                // column has it; the rest come from the forms the groups actually define.
                column.key to column.groups.flatMapTo(hashSetOf("plain")) { loaded.groupForms[it].orEmpty() }
            }
            _state.update {
                it.copy(
                    loading = false,
                    grammarExamples = examples,
                    columnForms = columnForms,
                    setSizes = loadedEngine.setSizes(),
                    words = loaded.words,
                )
            }
            refreshPath()
            refreshPool()
        }

        // Progress is written by one collector instead of inline on every answer: building
        // the JSON walks every lesson, word and leech, which has no business happening on
        // the main thread between a keystroke and the next frame. StateFlow conflates, so a
        // burst of answers costs one write, and a single collector keeps them ordered.
        viewModelScope.launch {
            _state.map { it.progress }
                .distinctUntilChanged()
                .drop(1) // what load() just returned is already on disk
                .collect { progress -> withContext(Dispatchers.IO) { progressStore.save(progress) } }
        }
    }

    // Navigation

    fun selectTab(tab: Tab) = _state.update { it.copy(tab = tab) }

    fun showSettings() = _state.update { it.copy(screen = Screen.Settings) }

    fun showAbout() = _state.update { it.copy(screen = Screen.About) }

    fun setChapterOpen(title: String, open: Boolean) =
        _state.update { it.copy(chapterOpen = it.chapterOpen + (title to open)) }

    fun showConjugationIntro() =
        _state.update { it.copy(screen = Screen.ConjugationIntro, conjugationIntroFrom = it.screen) }

    /**
     * Back to wherever the Conjugation Intro was opened from, with that screen's state still in
     * place.
     */
    fun closeConjugationIntro() {
        if (_state.value.conjugationIntroFrom == Screen.Root) backToRoot()
        else _state.update { it.copy(screen = it.conjugationIntroFrom) }
    }

    fun backToRoot() {
        queue.clear()
        _state.update {
            it.copy(
                screen = Screen.Root,
                quiz = null,
                lesson = null,
                introWords = emptyList(),
                introForms = emptyList(),
                introClasses = emptyList(),
                grammarNote = null,
                outcome = null,
            )
        }
        // Every route back to the path runs through here, including quitting a session
        // part-way. Abandoned sessions still moved the schedule, so the due count has to be
        // recomputed even though nothing finished.
        refreshPath()
    }

    // Settings

    fun setFlag(key: String, value: Boolean) = updateOptions { it.with(key, value) }

    // Word sets the learner makes

    /**
     * A new set, switched on and opened for picking words. It leaves "All words" alone: an
     * empty set draws on nothing, so switching the mode off here would empty the pool for
     * anyone who backed out of the editor. [setWordInSet] does it once the set can carry it.
     */
    fun newWordSet() {
        val progress = _state.value.progress
        val set = CustomSet(WordSets.newId(progress.sets.keys), "My words")
        persist(progress.copy(sets = progress.sets + (set.id to set)))
        _state.update { it.copy(screen = Screen.WordSet, editingSet = set.id) }
        updateOptions { it.withSet(set.id, true) }
    }

    fun editWordSet(id: String) = _state.update { it.copy(screen = Screen.WordSet, editingSet = id) }

    fun closeWordSet() = _state.update { it.copy(screen = Screen.Root, editingSet = null) }

    fun renameWordSet(id: String, name: String) = editSet(id) { it.copy(name = name) }

    /**
     * One word added to or taken out of the set being edited. The word that makes a
     * switched-on set stop being empty is also what makes it worth drawing on, so that is
     * where "All words" gives way to it.
     */
    fun setWordInSet(id: String, word: String, value: Boolean) {
        val first = value && _state.value.progress.sets[id]?.words.isNullOrEmpty() == true
        editSet(id) { it.copy(words = if (value) it.words + word else it.words - word) }
        if (first && _state.value.options.isSetOn(id)) updateOptions { it.copy(allWords = false) }
    }

    fun deleteWordSet(id: String) {
        val progress = _state.value.progress
        persist(progress.copy(sets = progress.sets - id))
        _state.update { it.copy(screen = Screen.Root, editingSet = null) }
        updateOptions { it.withSet(id, false) }
    }

    /**
     * The word the current question asks about, dropped from the set the session is drawing
     * on, with its remaining questions dropped from the queue: having said the word does not
     * belong here, being asked it three more times is the wrong answer.
     */
    fun dropCurrentWord() {
        val state = _state.value
        val set = state.droppableSet ?: return
        val word = state.quiz?.question?.word?.key ?: return
        editSet(set.id) { it.copy(words = it.words - word) }
        queue.removeAll { engine?.wordOf(it.packed)?.key == word }
    }

    private fun editSet(id: String, change: (CustomSet) -> CustomSet) {
        val progress = _state.value.progress
        val set = progress.sets[id] ?: return
        persist(progress.copy(sets = progress.sets + (id to change(set))))
        // The pool is built from the set's words, and its options did not change, so
        // nothing else would notice that it now holds different ones.
        refreshPool()
    }

    /** One word set switched on or off; several stack, and a word in two counts once. */
    fun setWordSet(id: String, value: Boolean) = updateOptions { it.withSet(id, value) }

    /** Draw on every word there is, remembering the sets underneath for when it goes off. */
    fun setAllWords(value: Boolean) = updateOptions { it.copy(allWords = value) }

    /** A whole row of the grid: the form, for every class that has it. */
    fun setForm(form: String, value: Boolean) = updateOptions { options ->
        // Switching a row back on clears the holes punched in it, or a row can read as on
        // and ask nothing.
        val cleared = if (value) options.copy(offSquares = options.offSquares.filterNotTo(HashSet()) {
            it.substringBefore('|') == form
        }) else options
        cleared.with(form, value)
    }

    /** A whole column of the grid: every form, for one class. */
    fun setColumn(column: WordColumn, value: Boolean) = updateOptions { options ->
        val cleared = if (value) options.copy(offSquares = options.offSquares.filterNotTo(HashSet()) {
            it.substringAfter('|') == column.key
        }) else options
        cleared.withColumn(column, value)
    }

    /**
     * One square. Switching on a square whose row or column is off switches those on too and
     * leaves the rest of them off, so a tap always asks exactly what it points at.
     */
    fun setSquare(form: String, column: WordColumn, value: Boolean) = updateOptions { options ->
        if (!value) return@updateOptions options.withSquare(form, column.key, false)
        var next = options.withSquare(form, column.key, true)
        if (!options.isOn(form)) {
            next = next.with(form, true)
            for (other in QuizOptions.COLUMNS) {
                if (other.key != column.key) next = next.withSquare(form, other.key, false)
            }
        }
        if (!options.isColumnOn(column)) {
            next = next.withColumn(column, true)
            for (other in QuizOptions.FORMS) {
                if (other.key != form) next = next.withSquare(other.key, column.key, false)
            }
        }
        next
    }

    fun setFocus(focus: String) = updateOptions { it.withFocus(focus) }

    /**
     * Only "What I've learned" is built out of the path's history. The other two state a
     * whole selection of their own, so they have to work on a fresh install — gating all
     * three on there being progress left every preset doing nothing until the path started.
     */
    fun applyPreset(preset: PracticePreset) {
        val practised = practised()
        if (preset == PracticePreset.Practised && practised == null) return
        updateOptions { it.withPreset(preset, practised?.forms.orEmpty(), practised?.groups.orEmpty()) }
    }

    fun setNumQuestions(text: String) = updateOptions { it.copy(numQuestions = text.filter(Char::isDigit).take(3)) }

    fun setTheme(theme: ThemeChoice) = updateOptions { it.copy(theme = theme) }

    fun setPalette(palette: Palette) = updateOptions { it.copy(palette = palette) }

    /** The pool is untouched, but the Review row states the session's length, which is capped. */
    fun setReviewCap(cap: Int) {
        updateOptions { it.copy(reviewCap = cap) }
        refreshDue()
    }

    /** One setting, flipped from Settings or by tapping the question card. */
    fun setFurigana(on: Boolean) = updateOptions { it.copy(furigana = on) }

    fun toggleFurigana() = setFurigana(!_state.value.options.furigana)

    /** Resets the practice settings only; the appearance is not one of them. */
    fun resetDefaults() = updateOptions { QuizOptions(theme = it.theme, palette = it.palette, furigana = it.furigana) }

    /** The whole learn path as text, for copying somewhere safe. */
    fun exportProgress(): String = ProgressCodec.encode(_state.value.progress, indent = 2)

    /**
     * Replaces the learn path from pasted text. Returns false, changing nothing, if the
     * text is not a backup — a paste of the wrong thing must not wipe real progress.
     */
    fun importProgress(text: String): Boolean {
        val imported = ProgressCodec.decodeOrNull(text) ?: return false
        // The notice would otherwise come back on the next launch, from the copy on disk.
        progressStore.discardSalvage()
        _state.update { it.copy(progress = imported, salvagedProgress = false) }
        refreshPath()
        return true
    }

    /** Wipes the learn path. Irreversible, so the screen confirms before calling this. */
    fun resetProgress() {
        progressStore.clear()
        _state.update { it.copy(progress = Progress(), salvagedProgress = false) }
        refreshPath()
    }

    private fun updateOptions(transform: (QuizOptions) -> QuizOptions) {
        val options = transform(_state.value.options)
        store.save(options)
        val poolAffected = !options.sameQuestions(_state.value.options)
        _state.update { it.copy(options = options) }
        if (poolAffected) refreshPool()
    }

    private fun refreshPool() {
        val engine = engine ?: return
        val options = _state.value.options
        poolJob?.cancel()
        _state.update { it.copy(pool = null) }
        poolJob = viewModelScope.launch {
            val newPool = withContext(Dispatchers.Default) { engine.buildPool(options) { ensureActive() } }
            pool = newPool
            _state.update {
                it.copy(
                    pool = PoolCounts(newPool.words, newPool.size, newPool.skipped),
                    columns = newPool.columns,
                )
            }
        }
    }

    // Learn path

    private fun refreshPath() {
        val data = data ?: return
        val learnPath = data.learnPath
        val progress = _state.value.progress
        val path = learnPath.lessons.map { lesson ->
            val record = progress.records[lesson.id]
            LessonCard(
                lesson = lesson,
                started = record != null,
                hasIntro = lesson.newBatches.isNotEmpty() || lesson.newForms.isNotEmpty() ||
                    lesson.newClasses.isNotEmpty() || lesson.builds.isNotEmpty() || lesson.point != null,
                ready = record?.ready == true,
                strength = learnPath.strength(lesson, progress),
            )
        }
        val next = nextLesson(learnPath, progress)
        _state.update { it.copy(path = path, nextLesson = next) }
        refreshDue()
    }

    private fun nextLesson(learnPath: LearnPath, progress: Progress): Lesson? =
        learnPath.lessons.firstOrNull { progress.records[it.id]?.ready != true }

    /** What the path has drilled so far: the forms of every lesson in review, and the word groups answered. */
    private class Practised(val forms: Set<String>, val groups: Set<String>)

    private fun practised(): Practised? {
        val data = data ?: return null
        val progress = _state.value.progress
        val lessons = data.learnPath.inReview(progress)
        if (lessons.isEmpty()) return null
        return Practised(
            forms = lessons.flatMapTo(hashSetOf("plain")) { it.forms },
            groups = progress.words.keys.mapNotNullTo(HashSet()) { data.wordsByKey[it]?.group },
        )
    }

    /**
     * A lesson opens on what it is about, every time — never straight into questions. A lesson
     * that adds nothing new shows the notes for the forms it drills instead, so the way in
     * is the same page whether it is the first visit or the tenth.
     */
    fun openLesson(lesson: Lesson) {
        // The Conjugation Intro is a lesson that is read: opening it is finishing it.
        if (lesson.reading) {
            markRead(lesson)
            showConjugationIntro()
            return
        }
        open(lesson)
    }

    /** The same page: a lesson is always entered through what it is about. */
    fun showLessonIntro(lesson: Lesson) = openLesson(lesson)

    private fun markRead(lesson: Lesson) {
        val progress = _state.value.progress
        if (progress.records[lesson.id]?.ready == true) return
        persist(progress.copy(records = progress.records + (lesson.id to LessonRecord(ready = true))))
        refreshPath()
    }

    private fun open(lesson: Lesson) {
        val data = data ?: return
        val words = data.learnPath.newWords(lesson).mapNotNull(data.wordsByKey::get)
        // What it teaches. A lesson that only takes something known somewhere new — ない's
        // past, a new class's negative — shows how it builds that instead of the form's notes
        // again; a mixed lesson builds nothing new, so its notes are the forms it puts together.
        val forms = when {
            lesson.newForms.isNotEmpty() -> lesson.newForms
            lesson.builds.isNotEmpty() -> emptyList()
            else -> lesson.forms.filterNot { it == "plain" }
        }.mapNotNull(Grammar::get)
        val classes = lesson.newClasses.mapNotNull(Grammar::classNote)
        if (words.isEmpty() && forms.isEmpty() && classes.isEmpty() && lesson.builds.isEmpty() && lesson.point == null) {
            startLesson(lesson)
            return
        }
        // Vocabulary because the drill tests production, and asking for a form of a word
        // never shown tests nothing useful; grammar and word classes because a rule should
        // be stated before it is drilled.
        _state.update {
            it.copy(
                screen = Screen.LessonIntro,
                lesson = lesson,
                introWords = words,
                introForms = forms,
                introClasses = classes,
            )
        }
    }

    // Grammar

    fun showGrammar(key: String) {
        val note = Grammar.note(key) ?: return
        _state.update { it.copy(screen = Screen.GrammarDetail, grammarNote = note) }
    }

    fun startLesson(lesson: Lesson) {
        if (startJob?.isActive == true) return
        val engine = engine ?: return
        val learnPath = data?.learnPath ?: return
        val options = learnPath.optionsFor(lesson, _state.value.options)
        // Recorded on opening rather than on the first answer, so quitting at once still
        // counts as having seen the introduction.
        val progress = _state.value.progress
        if (lesson.id !in progress.records) persist(progress.copy(records = progress.records + (lesson.id to LessonRecord())))
        wasReady = progress.records[lesson.id]?.ready == true

        startJob = viewModelScope.launch {
            val drawn = withContext(Dispatchers.Default) {
                engine.buildQueue(engine.buildPool(options), lesson.questions).map { Drawn(it, lesson.id) }
            }
            val question = startQueue(drawn)
            if (question == null) {
                backToRoot()
                return@launch
            }
            _state.update {
                it.copy(
                    screen = Screen.Quiz,
                    kind = SessionKind.Lesson,
                    lesson = lesson,
                    introWords = emptyList(),
                    introForms = emptyList(),
                    introClasses = emptyList(),
                    quiz = QuizState(lesson.questions, question),
                    quizOptions = options,
                )
            }
        }
    }

    /**
     * How review stands. It reads the lessons' schedules and nothing else, so it is cheap
     * enough to reckon on the spot; only the session itself walks the words.
     */
    private fun refreshDue() {
        val engine = engine ?: return
        val learnPath = data?.learnPath ?: return
        val progress = _state.value.progress
        val lessons = learnPath.inReview(progress).map { it.id }
        val load = engine.reviewLoad(lessons, progress, today, _state.value.options.reviewCap)
        _state.update {
            it.copy(
                dueCount = load.due,
                dueQuestions = load.questions,
                reviewSolid = load.solid,
                reviewCounted = true,
            )
        }
    }

    fun startReview() {
        if (startJob?.isActive == true) return
        val engine = engine ?: return
        val learnPath = data?.learnPath ?: return
        val progress = _state.value.progress
        // Each lesson on its own options, so every question it asks is one of its own.
        val options = _state.value.options
        val lessons = learnPath.inReview(progress).associate { it.id to learnPath.optionsFor(it, options) }
        if (lessons.isEmpty()) return

        startJob = viewModelScope.launch {
            val drawn = withContext(Dispatchers.Default) {
                engine.buildReviewQueue(lessons, progress, today, options.reviewCap)
            }
            val question = startQueue(drawn)
            if (question == null) {
                backToRoot()
                return@launch
            }
            _state.update {
                it.copy(
                    screen = Screen.Quiz,
                    kind = SessionKind.Review,
                    lesson = null,
                    quiz = QuizState(drawn.size, question),
                    quizOptions = options,
                )
            }
        }
    }

    // Quiz flow

    fun start() {
        val state = _state.value
        val engine = engine ?: return
        val pool = pool?.takeIf { it.options.sameQuestions(state.options) } ?: return
        val total = state.options.questionCount ?: return
        if (!state.canStart) return
        val question = startQueue(engine.buildQueue(pool, total).map { Drawn(it) }) ?: return
        _state.update {
            it.copy(
                screen = Screen.Quiz,
                kind = SessionKind.Practice,
                lesson = null,
                quiz = QuizState(total, question),
                quizOptions = state.options,
            )
        }
    }

    /** Loads a drawn session and takes its first question, or null if nothing was drawn. */
    private fun startQueue(drawn: List<Drawn>): Question? {
        if (drawn.isEmpty()) return null
        queue = drawn.toMutableList()
        return engine?.questionFor(queue.removeAt(0))
    }

    fun submit(rawResponse: String) {
        val quiz = _state.value.quiz ?: return
        if (quiz.answer != null) return

        val response = RomajiConverter.finish(rawResponse.trim())
        if (response.isEmpty() || !QuizEngine.isJapanese(response)) {
            _state.update { it.copy(quiz = quiz.copy(shakes = quiz.shakes + 1)) }
            return
        }

        val entry = HistoryEntry(quiz.question, response)
        val options = _state.value.quizOptions
        if (_state.value.kind != SessionKind.Practice) record(entry, quiz.history)
        _state.update {
            it.copy(
                quiz = quiz.copy(
                    history = quiz.history + entry,
                    answer = entry,
                    showExplanation = !entry.correct && options.autoExplain,
                )
            )
        }
        if (entry.correct && options.autoNext) proceed()
    }

    /**
     * Folds one answer into progress: the word's schedule and its leech count at once, the
     * lesson's record when this is the lesson's own session, and the lesson's schedule once this
     * was its last question in the session, graded on all of them
     * ([Progress.withLessonGraded]). Practice never touches progress.
     */
    private fun record(entry: HistoryEntry, earlier: List<HistoryEntry>) {
        val word = entry.question.word
        val t = entry.question.transformation
        val leech = Progress.leechKey(word.key, t.type)
        val day = today

        val progress = _state.value.progress
        val misses = progress.leeches[leech] ?: 0
        val updated = progress.copy(
            words = progress.words + (word.key to Scheduler.review(
                progress.words[word.key] ?: SrsState(), entry.correct, day
            )),
            // Dropped once it reaches zero rather than left sitting there: otherwise the
            // map keeps an entry for every pairing ever missed, and the whole document is
            // re-serialised on every answer.
            leeches = (if (entry.correct) misses - 1 else misses + 1).let { next ->
                if (next <= 0) progress.leeches - leech else progress.leeches + (leech to next)
            },
            records = lessonOf(_state.value)?.let { lesson ->
                progress.records + (lesson.id to (progress.records[lesson.id] ?: LessonRecord()).with(entry.correct, lesson.questions))
            } ?: progress.records,
        )
        // The last of its lesson's questions in this session: grade the lesson on all of them.
        val lesson = entry.question.lesson?.takeIf { id -> queue.none { it.lesson == id } }
        persist(
            lesson?.let { id ->
                val answers = (earlier + entry).filter { it.question.lesson == id }.map { it.correct }
                updated.withLessonGraded(id, answers, day)
            } ?: updated
        )
    }

    /** Publishes new progress; the collector in [init] is what writes it to disk. */
    private fun persist(progress: Progress) {
        // The engine draws on the sets, which only progress knows.
        engine?.customSets = progress.sets
        _state.update { it.copy(progress = progress) }
    }

    fun explain() {
        _state.update { s -> s.copy(quiz = s.quiz?.copy(showExplanation = true)) }
    }

    fun proceed() {
        val state = _state.value
        val quiz = state.quiz ?: return
        if (quiz.answer == null) return

        if (quiz.history.size >= quiz.total) {
            finish(quiz)
            return
        }
        // removeAt rather than removeFirst: the latter clashes with the SequencedCollection
        // default method on newer JDKs and fails at runtime on older Android.
        val question = queue.takeIf { it.isNotEmpty() }
            ?.removeAt(0)
            ?.let { engine?.questionFor(it) }
        if (question == null) {
            finish(quiz)
            return
        }
        _state.update {
            it.copy(quiz = quiz.copy(question = question, answer = null, showExplanation = false, shakes = 0))
        }
    }

    private fun finish(quiz: QuizState) {
        val lesson = lessonOf(_state.value)
        val learnPath = data?.learnPath
        val progress = _state.value.progress
        val history = quiz.history
        val record = lesson?.let { progress.records[it.id] }
        val outcome = if (lesson != null && record != null && learnPath != null && history.isNotEmpty()) {
            LessonOutcome(
                lesson = lesson,
                accuracy = history.count { it.correct } / history.size.toDouble(),
                record = record,
                becameReady = record.ready && !wasReady,
                next = nextLesson(learnPath, progress),
            )
        } else {
            null
        }
        _state.update { it.copy(screen = Screen.Results, outcome = outcome) }
        refreshPath()
    }

    /** The lesson the running session drills, if it is a lesson session at all. */
    private fun lessonOf(state: DrillUiState): Lesson? = state.lesson?.takeIf { state.kind == SessionKind.Lesson }
}
