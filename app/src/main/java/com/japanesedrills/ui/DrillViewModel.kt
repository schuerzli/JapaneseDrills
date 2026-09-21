package com.japanesedrills.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.japanesedrills.data.DrillData
import com.japanesedrills.data.Word
import com.japanesedrills.quiz.Grammar
import com.japanesedrills.quiz.GrammarExamples
import com.japanesedrills.quiz.GrammarNote
import com.japanesedrills.quiz.Lesson
import com.japanesedrills.quiz.LessonRecord
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
import com.japanesedrills.quiz.ThemeChoice
import com.japanesedrills.quiz.TransformationBuilder
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

enum class Screen { Root, LessonIntro, Quiz, Results, Settings, About, Primer, GrammarDetail }

/** Which of the three things the running quiz is. */
enum class SessionKind { Practice, Lesson, Review }

data class HistoryEntry(val question: Question, val response: String) {
    /** Decided once: the score badge, the grading and the results all ask again. */
    val correct: Boolean = question.isCorrect(response)
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
data class PoolCounts(val words: Int, val questions: Int)

/** One row on the learn path. */
data class LessonCard(
    val lesson: Lesson,
    val unlocked: Boolean,
    val passed: Boolean,
    /** How well the skills this lesson taught are holding up, 0f..1f. */
    val strength: Float,
)

/** How a lesson attempt went, shown on the results screen. */
data class LessonOutcome(
    val lesson: Lesson,
    val passed: Boolean,
    val accuracy: Double,
    /** Forms that fell below the per-form floor, which is why an 85% run can still fail. */
    val weakForms: List<String>,
    val unlocked: List<Lesson>,
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
    val dueCount: Int = 0,
    /** The lesson being introduced or drilled. */
    val lesson: Lesson? = null,
    /** New vocabulary to present before [lesson] starts. */
    val introWords: List<Word> = emptyList(),
    /** New grammar to present before [lesson] starts, in the order the lesson adds it. */
    val introForms: List<GrammarNote> = emptyList(),
    /** Word classes [lesson] is about, introduced before its forms and words. */
    val introClasses: List<GrammarNote> = emptyList(),
    /**
     * Chapters the learner has folded or unfolded by hand, by title. Kept here rather than in
     * the path screen, which leaves composition for every lesson and would forget. Only for
     * the session: the defaults already follow progress, so they are right on the next launch.
     */
    val chapterOpen: Map<String, Boolean> = emptyMap(),
    /** Where closing the primer returns to: it opens from the Grammar tab and from lessons. */
    val primerFrom: Screen = Screen.Root,
    /** The form being read about on the Grammar tab. */
    val grammarNote: GrammarNote? = null,
    /** Representative words for showing how a form is built. */
    val grammarExamples: GrammarExamples = GrammarExamples(),
    val outcome: LessonOutcome? = null,
    /** Set when stored progress could not be read and was put aside rather than overwritten. */
    val salvagedProgress: Boolean = false,
) {
    val canStart: Boolean
        get() = !loading && options.hasPoliteness && options.questionCount != null && (pool?.questions ?: 0) > 0

    /**
     * Any progress at all, not just finished lessons: answering a single question already
     * schedules a skill and a word, and it would be odd to still be told nothing has begun.
     */
    val started: Boolean get() = !progress.isEmpty
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

    /**
     * The rest of the running session's questions, as packed pairs. Every session kind
     * draws its questions up front so none of them can ask the same pair twice while
     * unasked ones remain.
     */
    private var queue: MutableList<Int> = mutableListOf()

    private val today: Long get() = LocalDate.now().toEpochDay()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { DrillData.load(getApplication()) }
            data = loaded
            engine = QuizEngine(loaded)
            val examples = GrammarExamples(
                Grammar.EXAMPLE_KEYS.mapNotNull(loaded.wordsByKey::get).associateBy { it.key },
                loaded.ownForms,
            )
            _state.update { it.copy(loading = false, grammarExamples = examples) }
            refreshPath()
            refreshPool()
        }

        // Progress is written by one collector instead of inline on every answer: building
        // the JSON walks every skill, word and leech, which has no business happening on
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

    fun showPrimer() = _state.update { it.copy(screen = Screen.Primer, primerFrom = it.screen) }

    /** Back to wherever the primer was opened from, with that screen's state still in place. */
    fun closePrimer() {
        if (_state.value.primerFrom == Screen.Root) backToRoot()
        else _state.update { it.copy(screen = it.primerFrom) }
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
        // part-way. Reviews and abandoned lessons still moved the schedule, so the due
        // count and the mastery rings have to be recomputed even though nothing was graded.
        refreshPath()
    }

    // Settings

    fun setFlag(key: String, value: Boolean) = updateOptions { it.with(key, value) }

    fun setFocus(focus: String) = updateOptions { it.withFocus(focus) }

    fun applyPreset(preset: PracticePreset) {
        val learned = learned() ?: return
        updateOptions { it.withPreset(preset, learned.forms, learned.groups) }
    }

    fun setNumQuestions(text: String) = updateOptions { it.copy(numQuestions = text.filter(Char::isDigit).take(3)) }

    fun setTheme(theme: ThemeChoice) = updateOptions { it.copy(theme = theme) }

    fun setPalette(palette: Palette) = updateOptions { it.copy(palette = palette) }

    /** Resets the practice settings only; the appearance is not one of them. */
    fun resetDefaults() = updateOptions { QuizOptions(theme = it.theme, palette = it.palette) }

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
            _state.update { it.copy(pool = PoolCounts(newPool.words, newPool.size)) }
        }
    }

    // Learn path

    private fun refreshPath() {
        val curriculum = data?.curriculum ?: return
        val progress = _state.value.progress
        val passed = progress.passed
        val day = today

        val path = curriculum.lessons.map { lesson ->
            LessonCard(
                lesson = lesson,
                unlocked = curriculum.isUnlocked(lesson.id, passed),
                passed = lesson.id in passed,
                strength = strengthOf(lesson, progress),
            )
        }
        // Only what a review can reach: a failed lesson also schedules its forms, and
        // counting those showed work due that no review would ever ask about.
        val learned = learned()
        val dueCount = if (learned == null) 0 else progress.dueCount(day) { skill ->
            QuizEngine.typeOfSkill(skill) in learned.types &&
                QuizEngine.groupOfSkill(skill) in learned.groups
        }
        _state.update { it.copy(path = path, dueCount = dueCount) }
    }

    /** What the passed lessons have taught between them, or null if none has been passed. */
    private class Learned(val words: Set<String>, val forms: Set<String>, val groups: Set<String>) {
        val types: Set<String> = forms.mapTo(HashSet(), TransformationBuilder::typeOfForm)
    }

    private fun learned(): Learned? {
        val data = data ?: return null
        val passed = _state.value.progress.passed
        if (passed.isEmpty()) return null
        val words = passed.flatMapTo(HashSet()) { data.curriculum.words(it) }
        return Learned(
            words = words,
            forms = passed.flatMapTo(HashSet()) { data.curriculum.forms(it) },
            groups = words.mapNotNullTo(HashSet()) { data.wordsByKey[it]?.group },
        )
    }

    /** A lesson's mastery is the weakest of the skills it drills, so one rusty form shows. */
    private fun strengthOf(lesson: Lesson, progress: Progress): Float {
        // Form options and transformation types are near-identical vocabularies, but
        // plain/polite both record as "politeness"; map before comparing or the first
        // lesson's ring can never fill.
        val types = data?.curriculum?.forms(lesson.id)
            .orEmpty()
            .mapTo(HashSet(), TransformationBuilder::typeOfForm)
        val relevant = progress.skills.filterKeys { QuizEngine.typeOfSkill(it) in types }
        if (relevant.isEmpty()) return 0f
        return relevant.values.minOf { Scheduler.strength(it) }
    }

    fun openLesson(lesson: Lesson) {
        val data = data ?: return
        val words = lesson.newWords.mapNotNull(data.wordsByKey::get)
        val forms = lesson.newForms.mapNotNull(Grammar::get)
        val classes = lesson.newClasses.mapNotNull(Grammar::classNote)
        if (words.isEmpty() && forms.isEmpty() && classes.isEmpty()) {
            startLesson(lesson)
            return
        }
        // Whatever the lesson adds is shown before it is graded. Vocabulary because the
        // drill tests production, and grading a word never shown tests nothing useful;
        // grammar because a form lesson used to go straight to questions about a rule it
        // had never stated. A new word class likewise, or な-adjectives arrive as eight
        // words with nothing to say that they do not conjugate.
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

    fun showGrammar(formKey: String) {
        val note = Grammar[formKey] ?: return
        _state.update { it.copy(screen = Screen.GrammarDetail, grammarNote = note) }
    }

    fun startLesson(lesson: Lesson) {
        val engine = engine ?: return
        val curriculum = data?.curriculum ?: return
        val options = curriculum.optionsFor(lesson, _state.value.options)

        viewModelScope.launch {
            val drawn = withContext(Dispatchers.Default) {
                engine.buildQueue(engine.buildPool(options), lesson.questions)
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

    fun startReview() {
        val engine = engine ?: return
        val curriculum = data?.curriculum ?: return
        val learned = learned() ?: return
        val progress = _state.value.progress
        val options = curriculum.optionsFor(learned.words, learned.forms, _state.value.options)

        viewModelScope.launch {
            val drawn = withContext(Dispatchers.Default) {
                engine.buildReviewQueue(options, progress, today)
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
        val question = startQueue(engine.buildQueue(pool, total)) ?: return
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
    private fun startQueue(drawn: List<Int>): Question? {
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
        if (_state.value.kind != SessionKind.Practice) record(entry)
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

    /** Folds one answer into the schedule. Practice never touches progress. */
    private fun record(entry: HistoryEntry) {
        val word = entry.question.word
        val t = entry.question.transformation
        val skill = QuizEngine.skillOf(word, t)
        val leech = Progress.leechKey(word.key, t.type)
        val day = today

        val progress = _state.value.progress
        val misses = progress.leeches[leech] ?: 0
        val updated = progress.copy(
            skills = progress.skills + (skill to Scheduler.review(
                progress.skills[skill] ?: SrsState(), entry.correct, day
            )),
            words = progress.words + (word.key to Scheduler.review(
                progress.words[word.key] ?: SrsState(), entry.correct, day
            )),
            // Dropped once it reaches zero rather than left sitting there: otherwise the
            // map keeps an entry for every pairing ever missed, and the whole document is
            // re-serialised on every answer.
            leeches = (if (entry.correct) misses - 1 else misses + 1).let { next ->
                if (next <= 0) progress.leeches - leech else progress.leeches + (leech to next)
            },
        )
        persist(updated)
    }

    /** Publishes new progress; the collector in [init] is what writes it to disk. */
    private fun persist(progress: Progress) {
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
        val state = _state.value
        val lesson = state.lesson
        val outcome = if (state.kind == SessionKind.Lesson && lesson != null) {
            gradeLesson(lesson, quiz.history)
        } else {
            null
        }
        _state.update { it.copy(screen = Screen.Results, outcome = outcome) }
        refreshPath()
    }

    /**
     * A lesson passes on overall accuracy *and* a floor under every form it asked about,
     * so a て-form lesson cannot be passed on the strength of the negatives mixed into it.
     */
    private fun gradeLesson(lesson: Lesson, history: List<HistoryEntry>): LessonOutcome {
        val accuracy = if (history.isEmpty()) 0.0 else history.count { it.correct } / history.size.toDouble()
        val weakForms = history.groupBy { it.question.transformation.type }
            .filterValues { entries -> entries.count { it.correct } / entries.size.toDouble() < lesson.passPerForm }
            .keys.sorted()
        val passed = accuracy >= lesson.passAccuracy && weakForms.isEmpty()

        val progress = _state.value.progress
        val previous = progress.lessons[lesson.id] ?: LessonRecord()
        val updated = progress.copy(
            lessons = progress.lessons + (lesson.id to LessonRecord(
                // Passing is sticky: a later bad run does not take a lesson away again.
                passed = previous.passed || passed,
                bestAccuracy = maxOf(previous.bestAccuracy, accuracy),
                attempts = previous.attempts + 1,
            ))
        )
        persist(updated)

        val curriculum = data?.curriculum
        return LessonOutcome(
            lesson = lesson,
            passed = passed,
            accuracy = accuracy,
            weakForms = weakForms,
            unlocked = if (passed) curriculum?.unlockedBy(lesson.id, updated.passed).orEmpty() else emptyList(),
        )
    }
}
