package com.japanesedrills.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.japanesedrills.data.DrillData
import com.japanesedrills.data.Word
import com.japanesedrills.quiz.LearnPath
import com.japanesedrills.quiz.Grammar
import com.japanesedrills.quiz.GrammarExamples
import com.japanesedrills.quiz.GrammarNote
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
import com.japanesedrills.quiz.Step
import com.japanesedrills.quiz.StepRecord
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

enum class Screen { Root, StepIntro, Quiz, Results, Settings, About, Primer, GrammarDetail }

/** Which of the three things the running quiz is. */
enum class SessionKind { Practice, Step, Review }

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
data class StepCard(
    val step: Step,
    /** Opened at least once, so its introduction has been seen. */
    val started: Boolean,
    /** Its recent answers have cleared the bar at some point; see [StepRecord.ready]. */
    val ready: Boolean,
    /** How well its content is holding up in review, 0f..1f; see [LearnPath.solidity]. */
    val strength: Float,
    val newWords: Int,
)

/** How a session of a step went, shown on the results screen. */
data class StepOutcome(
    val step: Step,
    val accuracy: Double,
    val record: StepRecord,
    /** This session is what took the step over the bar. */
    val becameReady: Boolean,
    /** What the path recommends now, if anything is left to recommend. */
    val next: Step?,
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
    val path: List<StepCard> = emptyList(),
    /**
     * The step the path recommends: the earliest not yet ready, even when the learner has
     * jumped ahead, since everything after it builds on it. Its chapter starts unfolded.
     */
    val nextStep: Step? = null,
    val dueCount: Int = 0,
    /** The step being introduced or drilled. */
    val step: Step? = null,
    /** New vocabulary to present before [step] starts. */
    val introWords: List<Word> = emptyList(),
    /** New grammar to present before [step] starts. */
    val introForms: List<GrammarNote> = emptyList(),
    /** Word classes [step] introduces, presented before its forms and words. */
    val introClasses: List<GrammarNote> = emptyList(),
    /**
     * Chapters the learner has folded or unfolded by hand, by title. Kept here rather than in
     * the path screen, which leaves composition for every session and would forget. Only for
     * the session: the defaults already follow progress, so they are right on the next launch.
     */
    val chapterOpen: Map<String, Boolean> = emptyMap(),
    /** Where closing the primer returns to: it opens from the Grammar tab and from steps. */
    val primerFrom: Screen = Screen.Root,
    /** The form being read about on the Grammar tab. */
    val grammarNote: GrammarNote? = null,
    /** Representative words for showing how a form is built. */
    val grammarExamples: GrammarExamples = GrammarExamples(),
    val outcome: StepOutcome? = null,
    /** Set when stored progress could not be read and was put aside rather than overwritten. */
    val salvagedProgress: Boolean = false,
) {
    val canStart: Boolean
        get() = !loading && options.hasPoliteness && options.questionCount != null && (pool?.questions ?: 0) > 0

    /**
     * Any progress at all: opening a step already counts, and it would be odd to still be
     * told nothing has begun.
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

    /** Whether the running step was already ready when it started, to tell when it became so. */
    private var wasReady = false

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
                step = null,
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

    fun setFocus(focus: String) = updateOptions { it.withFocus(focus) }

    fun applyPreset(preset: PracticePreset) {
        val practised = practised() ?: return
        updateOptions { it.withPreset(preset, practised.forms, practised.groups) }
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
        val learnPath = data?.learnPath ?: return
        val progress = _state.value.progress
        val words = data?.wordsByKey.orEmpty()
        val path = learnPath.steps.map { step ->
            val record = progress.steps[step.id]
            StepCard(
                step = step,
                started = record != null,
                ready = record?.ready == true,
                strength = learnPath.solidity(step, progress, words::get),
                newWords = learnPath.newWords(step).size,
            )
        }
        val next = nextStep(learnPath, progress)
        _state.update { it.copy(path = path, nextStep = next, dueCount = progress.dueCount(today)) }
    }

    private fun nextStep(learnPath: LearnPath, progress: Progress): Step? =
        learnPath.steps.firstOrNull { progress.steps[it.id]?.ready != true }

    /** What the path has drilled so far: every word and question type answered there. */
    private class Practised(val words: Set<String>, val forms: Set<String>, val groups: Set<String>)

    private fun practised(): Practised? {
        val data = data ?: return null
        val progress = _state.value.progress
        if (progress.skills.isEmpty()) return null
        val words = progress.words.keys.filterTo(HashSet()) { it in data.wordsByKey }
        return Practised(
            words = words,
            forms = progress.skills.keys.flatMapTo(hashSetOf("plain")) {
                TransformationBuilder.formsOfType(QuizEngine.typeOfSkill(it))
            },
            groups = words.mapNotNullTo(HashSet()) { data.wordsByKey[it]?.group },
        )
    }

    /**
     * Shows whatever the step adds the first time it is opened, and goes straight to the
     * questions after that: the Grammar tab holds every note for rereading.
     */
    fun openStep(step: Step) {
        val data = data ?: return
        val words = data.learnPath.newWords(step).mapNotNull(data.wordsByKey::get)
        val forms = step.newForms.mapNotNull(Grammar::get)
        val classes = step.newClasses.mapNotNull(Grammar::classNote)
        if (step.id in _state.value.progress.steps || (words.isEmpty() && forms.isEmpty() && classes.isEmpty())) {
            startStep(step)
            return
        }
        // Vocabulary because the drill tests production, and asking for a form of a word
        // never shown tests nothing useful; grammar and word classes because a rule should
        // be stated before it is drilled.
        _state.update {
            it.copy(
                screen = Screen.StepIntro,
                step = step,
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

    fun startStep(step: Step) {
        val engine = engine ?: return
        val learnPath = data?.learnPath ?: return
        val options = learnPath.optionsFor(step, _state.value.options)
        // Recorded on opening rather than on the first answer, so quitting at once still
        // counts as having seen the introduction.
        val progress = _state.value.progress
        if (step.id !in progress.steps) persist(progress.copy(steps = progress.steps + (step.id to StepRecord())))
        wasReady = progress.steps[step.id]?.ready == true

        viewModelScope.launch {
            val drawn = withContext(Dispatchers.Default) {
                engine.buildQueue(engine.buildPool(options), step.questions)
            }
            val question = startQueue(drawn)
            if (question == null) {
                backToRoot()
                return@launch
            }
            _state.update {
                it.copy(
                    screen = Screen.Quiz,
                    kind = SessionKind.Step,
                    step = step,
                    introWords = emptyList(),
                    introForms = emptyList(),
                    introClasses = emptyList(),
                    quiz = QuizState(step.questions, question),
                    quizOptions = options,
                )
            }
        }
    }

    fun startReview() {
        val engine = engine ?: return
        val learnPath = data?.learnPath ?: return
        val practised = practised() ?: return
        val progress = _state.value.progress
        val options = learnPath.optionsFor(practised.words, practised.forms, _state.value.options)

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
                    step = null,
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
                step = null,
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

    /** Folds one answer into the schedule and the step's record. Practice never touches progress. */
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
            steps = stepOf(_state.value)?.let { step ->
                progress.steps + (step.id to (progress.steps[step.id] ?: StepRecord()).with(entry.correct))
            } ?: progress.steps,
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
        val step = stepOf(_state.value)
        val learnPath = data?.learnPath
        val progress = _state.value.progress
        val history = quiz.history
        val record = step?.let { progress.steps[it.id] }
        val outcome = if (step != null && record != null && learnPath != null && history.isNotEmpty()) {
            StepOutcome(
                step = step,
                accuracy = history.count { it.correct } / history.size.toDouble(),
                record = record,
                becameReady = record.ready && !wasReady,
                next = nextStep(learnPath, progress),
            )
        } else {
            null
        }
        _state.update { it.copy(screen = Screen.Results, outcome = outcome) }
        refreshPath()
    }

    /** The step the running session drills, if it is a step session at all. */
    private fun stepOf(state: DrillUiState): Step? = state.step?.takeIf { state.kind == SessionKind.Step }
}
