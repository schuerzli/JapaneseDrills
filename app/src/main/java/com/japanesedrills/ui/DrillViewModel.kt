package com.japanesedrills.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.japanesedrills.data.DrillData
import com.japanesedrills.quiz.OptionsStore
import com.japanesedrills.quiz.Question
import com.japanesedrills.quiz.QuestionPool
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.RomajiConverter
import com.japanesedrills.quiz.ThemeChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Settings, Quiz, Results, About }

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

data class DrillUiState(
    val loading: Boolean = true,
    val options: QuizOptions = QuizOptions(),
    /** Null while the pool for the current options is being counted. */
    val pool: PoolCounts? = null,
    val screen: Screen = Screen.Settings,
    val quiz: QuizState? = null,
    /** Options the running quiz was started with. */
    val quizOptions: QuizOptions = QuizOptions(),
) {
    val canStart: Boolean
        get() = !loading && options.hasPoliteness && options.questionCount != null && (pool?.questions ?: 0) > 0
}

class DrillViewModel(application: Application) : AndroidViewModel(application) {

    private val store = OptionsStore(application)
    private val _state = MutableStateFlow(DrillUiState(options = store.load()))
    val state: StateFlow<DrillUiState> = _state.asStateFlow()

    private var engine: QuizEngine? = null
    private var pool: QuestionPool? = null
    private var activePool: QuestionPool? = null
    private var poolJob: Job? = null

    init {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { DrillData.load(getApplication()) }
            engine = QuizEngine(data)
            _state.update { it.copy(loading = false) }
            refreshPool()
        }
    }

    // Settings

    fun setFlag(key: String, value: Boolean) = updateOptions { it.with(key, value) }

    fun setFocus(focus: String) = updateOptions { it.copy(questionFocus = focus) }

    fun setNumQuestions(text: String) = updateOptions { it.copy(numQuestions = text.filter(Char::isDigit).take(3)) }

    fun setTheme(theme: ThemeChoice) = updateOptions { it.copy(theme = theme) }

    /** Resets the quiz settings only; the chosen theme is not one of them. */
    fun resetDefaults() = updateOptions { QuizOptions(theme = it.theme) }

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

    // Quiz flow

    fun start() {
        val state = _state.value
        val engine = engine ?: return
        val pool = pool?.takeIf { it.options.sameQuestions(state.options) } ?: return
        val total = state.options.questionCount ?: return
        if (!state.canStart) return
        val question = engine.nextQuestion(pool) ?: return
        activePool = pool
        _state.update {
            it.copy(screen = Screen.Quiz, quiz = QuizState(total, question), quizOptions = state.options)
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

    fun explain() {
        _state.update { s -> s.copy(quiz = s.quiz?.copy(showExplanation = true)) }
    }

    fun proceed() {
        val quiz = _state.value.quiz ?: return
        if (quiz.answer == null) return
        if (quiz.history.size >= quiz.total) {
            _state.update { it.copy(screen = Screen.Results) }
            return
        }
        val question = activePool?.let { engine?.nextQuestion(it) }
        if (question == null) {
            backToStart()
            return
        }
        _state.update {
            it.copy(quiz = quiz.copy(question = question, answer = null, showExplanation = false))
        }
    }

    fun backToStart() {
        activePool = null
        _state.update { it.copy(screen = Screen.Settings, quiz = null) }
    }

    fun showAbout() = _state.update { it.copy(screen = Screen.About) }
}
