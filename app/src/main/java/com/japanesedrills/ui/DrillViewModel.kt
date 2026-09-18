package com.japanesedrills.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.japanesedrills.data.DrillData
import com.japanesedrills.data.Word
import com.japanesedrills.quiz.Lesson
import com.japanesedrills.quiz.LessonRecord
import com.japanesedrills.quiz.OptionsStore
import com.japanesedrills.quiz.Progress
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class Tab(val label: String) { Learn("Learn Path"), Practice("Free Practice"), Settings("Settings") }

enum class Screen { Root, LessonIntro, Quiz, Results, About }

/** Which of the three things the running quiz is. */
enum class SessionKind { Practice, Lesson, Review }

data class HistoryEntry(val question: Question, val response: String) {
    val correct: Boolean get() = question.isCorrect(response)
}

data class QuizState(
    val total: Int,
    val question: Question,
    val history: List<HistoryEntry> = emptyList(),
    /** The answer to [question] once it has been given; it is also the last [history] entry. */
    val answer: HistoryEntry? = null,
    val showExplanation: Boolean = false,
    /** Incremented on every rejected submission to trigger the shake animation. */
    val shakes: Int = 0,
)

/** What the current settings add up to: how many words, and how many questions from them. */
data class PoolCounts(val words: Int, val questions: Int)

/** One row on the learn path. */
data class LessonCard(
    val lesson: Lesson,
    val unlocked: Boolean,
    val passed: Boolean,
    val bestAccuracy: Double,
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
    private var activePool: QuestionPool? = null
    private var poolJob: Job? = null

    /** Pre-picked packed pairs for a review session; empty for other session kinds. */
    private var reviewQueue: MutableList<Int> = mutableListOf()

    private val today: Long get() = LocalDate.now().toEpochDay()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { DrillData.load(getApplication()) }
            data = loaded
            engine = QuizEngine(loaded)
            _state.update { it.copy(loading = false) }
            refreshPath()
            refreshPool()
        }
    }

    // Navigation

    fun selectTab(tab: Tab) = _state.update { it.copy(tab = tab) }

    fun showAbout() = _state.update { it.copy(screen = Screen.About) }

    fun backToRoot() {
        activePool = null
        reviewQueue.clear()
        _state.update {
            it.copy(screen = Screen.Root, quiz = null, lesson = null, introWords = emptyList(), outcome = null)
        }
        // Every route back to the path runs through here, including quitting a session
        // part-way. Reviews and abandoned lessons still moved the schedule, so the due
        // count and the mastery rings have to be recomputed even though nothing was graded.
        refreshPath()
    }

    // Settings

    fun setFlag(key: String, value: Boolean) = updateOptions { it.with(key, value) }

    fun setFocus(focus: String) = updateOptions { it.copy(questionFocus = focus) }

    fun setNumQuestions(text: String) = updateOptions { it.copy(numQuestions = text.filter(Char::isDigit).take(3)) }

    fun setTheme(theme: ThemeChoice) = updateOptions { it.copy(theme = theme) }

    /** Resets the practice settings only; the chosen theme is not one of them. */
    fun resetDefaults() = updateOptions { QuizOptions(theme = it.theme) }

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
            val newPool = withContext(Dispatchers.Default) { engine.buildPool(options) }
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
                bestAccuracy = progress.lessons[lesson.id]?.bestAccuracy ?: 0.0,
                strength = strengthOf(lesson, progress),
            )
        }
        _state.update { it.copy(path = path, dueCount = dueNow(progress, day)) }
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

    private fun dueNow(progress: Progress, day: Long): Int =
        progress.skills.count { (_, state) -> Scheduler.isDue(state, day) }

    fun openLesson(lesson: Lesson) {
        val data = data ?: return
        val words = lesson.newWords.mapNotNull(data.wordsByKey::get)
        if (words.isEmpty()) {
            startLesson(lesson)
            return
        }
        // A lesson that introduces vocabulary shows it first: the drill grades production,
        // and grading a word the learner has never been shown tests nothing useful.
        _state.update { it.copy(screen = Screen.LessonIntro, lesson = lesson, introWords = words) }
    }

    fun startLesson(lesson: Lesson) {
        val engine = engine ?: return
        val curriculum = data?.curriculum ?: return
        val options = curriculum.optionsFor(lesson, _state.value.options)

        viewModelScope.launch {
            val lessonPool = withContext(Dispatchers.Default) { engine.buildPool(options) }
            val question = engine.nextQuestion(lessonPool)
            if (question == null) {
                backToRoot()
                return@launch
            }
            activePool = lessonPool
            reviewQueue.clear()
            _state.update {
                it.copy(
                    screen = Screen.Quiz,
                    kind = SessionKind.Lesson,
                    lesson = lesson,
                    introWords = emptyList(),
                    quiz = QuizState(lesson.questions, question),
                    quizOptions = options,
                )
            }
        }
    }

    fun startReview() {
        val engine = engine ?: return
        val curriculum = data?.curriculum ?: return
        val progress = _state.value.progress
        val passed = progress.passed
        if (passed.isEmpty()) return

        val words = passed.flatMapTo(HashSet()) { curriculum.words(it) }
        val forms = passed.flatMapTo(HashSet()) { curriculum.forms(it) }
        val options = curriculum.optionsForReview(words, forms, _state.value.options)

        viewModelScope.launch {
            val queue = withContext(Dispatchers.Default) {
                buildReviewQueue(engine, options, progress, today)
            }
            if (queue.isEmpty()) {
                backToRoot()
                return@launch
            }
            reviewQueue = queue.toMutableList()
            activePool = null
            val question = engine.questionFor(reviewQueue.removeAt(0))
            _state.update {
                it.copy(
                    screen = Screen.Quiz,
                    kind = SessionKind.Review,
                    lesson = null,
                    quiz = QuizState(queue.size, question),
                    quizOptions = options,
                )
            }
        }
    }

    /**
     * Picks the review questions up front, cycling through the due skills so the session
     * interleaves them rather than blocking one skill at a time. Interleaving feels harder
     * and retains better, which is the whole point of a review.
     */
    private fun buildReviewQueue(
        engine: QuizEngine,
        options: QuizOptions,
        progress: Progress,
        day: Long,
    ): List<Int> {
        val index = engine.buildSkillIndex(options)
        if (index.isEmpty()) return emptyList()

        val due = index.keys.filter { key ->
            progress.skills[key]?.let { Scheduler.isDue(it, day) } ?: true
        }
        val skills = (due.ifEmpty { index.keys.toList() }).shuffled()
        val count = (skills.size * QUESTIONS_PER_SKILL).coerceIn(MIN_REVIEW, MAX_REVIEW)

        val queue = ArrayList<Int>(count)
        var i = 0
        while (queue.size < count) {
            val skill = skills[i++ % skills.size]
            val entries = index[skill] ?: continue
            queue += pickForSkill(engine, entries, progress, day, QuizEngine.typeOfSkill(skill))
        }
        return queue
    }

    /** Within a skill, a leech beats a due word beats anything else. */
    private fun pickForSkill(
        engine: QuizEngine,
        entries: IntArray,
        progress: Progress,
        day: Long,
        type: String,
    ): Int {
        val leeches = entries.filter { packed ->
            val key = Progress.leechKey(engine.wordOf(packed).key, type)
            (progress.leeches[key] ?: 0) >= Progress.LEECH_THRESHOLD
        }
        if (leeches.isNotEmpty()) return leeches.random()

        val dueWords = entries.filter { packed ->
            progress.words[engine.wordOf(packed).key]?.let { Scheduler.isDue(it, day) } ?: true
        }
        return (if (dueWords.isNotEmpty()) dueWords else entries.toList()).random()
    }

    // Quiz flow

    fun start() {
        val state = _state.value
        val engine = engine ?: return
        val pool = pool?.takeIf { it.options.sameQuestions(state.options) } ?: return
        val total = state.options.questionCount ?: return
        if (!state.canStart) return
        val question = engine.nextQuestion(pool) ?: return
        activePool = pool
        reviewQueue.clear()
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
            leeches = when {
                entry.correct && misses > 0 -> progress.leeches + (leech to misses - 1)
                entry.correct -> progress.leeches
                else -> progress.leeches + (leech to misses + 1)
            },
        )
        persist(updated)
    }

    private fun persist(progress: Progress) {
        progressStore.save(progress)
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
        val question = when (state.kind) {
            // removeAt rather than removeFirst: the latter clashes with the SequencedCollection
            // default method on newer JDKs and fails at runtime on older Android.
            SessionKind.Review -> reviewQueue.takeIf { it.isNotEmpty() }
                ?.removeAt(0)
                ?.let { engine?.questionFor(it) }
            else -> activePool?.let { engine?.nextQuestion(it) }
        }
        if (question == null) {
            finish(quiz)
            return
        }
        _state.update {
            it.copy(quiz = quiz.copy(question = question, answer = null, showExplanation = false))
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

    private companion object {
        const val QUESTIONS_PER_SKILL = 2
        const val MIN_REVIEW = 10
        const val MAX_REVIEW = 30
    }
}
