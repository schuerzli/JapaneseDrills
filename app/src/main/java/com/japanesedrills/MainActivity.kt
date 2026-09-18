package com.japanesedrills

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.japanesedrills.quiz.ThemeChoice
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.DrillViewModel
import com.japanesedrills.ui.Screen
import com.japanesedrills.ui.Tab as AppTab
import com.japanesedrills.ui.screens.AboutScreen
import com.japanesedrills.ui.screens.AppSettingsScreen
import com.japanesedrills.ui.screens.LearnPathScreen
import com.japanesedrills.ui.screens.LessonIntroScreen
import com.japanesedrills.ui.screens.PracticeBar
import com.japanesedrills.ui.screens.PracticeScreen
import com.japanesedrills.ui.screens.QuizScreen
import com.japanesedrills.ui.screens.ResultsScreen
import com.japanesedrills.ui.theme.JapaneseDrillsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: DrillViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            // The stored options are read before the first frame, so the chosen theme
            // applies immediately rather than flashing the system one first.
            val dark = when (state.options.theme) {
                ThemeChoice.System -> isSystemInDarkTheme()
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
            }
            JapaneseDrillsTheme(darkTheme = dark) { DrillApp(state, viewModel) }
        }
    }
}

@Composable
private fun DrillApp(state: DrillUiState, viewModel: DrillViewModel) {
    val contentModifier = Modifier.fillMaxSize()

    if (state.loading) {
        Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Bound to locals so the null checks below smart-cast inside the lambdas.
    val quiz = state.quiz
    val lesson = state.lesson
    when {
        state.screen == Screen.Quiz && quiz != null -> {
            BackHandler(onBack = viewModel::backToRoot)
            QuizScreen(
                quiz = quiz,
                options = state.quizOptions,
                onSubmit = viewModel::submit,
                onProceed = viewModel::proceed,
                onExplain = viewModel::explain,
                onQuit = viewModel::backToRoot,
                modifier = contentModifier,
            )
        }

        state.screen == Screen.LessonIntro && lesson != null -> {
            BackHandler(onBack = viewModel::backToRoot)
            LessonIntroScreen(
                lesson = lesson,
                words = state.introWords,
                options = state.options,
                onStart = { viewModel.startLesson(lesson) },
                onQuit = viewModel::backToRoot,
                modifier = contentModifier,
            )
        }

        state.screen == Screen.About -> {
            BackHandler(onBack = viewModel::backToRoot)
            AboutScreen(onBack = viewModel::backToRoot, modifier = contentModifier)
        }

        state.screen == Screen.Results && quiz != null -> {
            BackHandler(onBack = viewModel::backToRoot)
            ResultsScreen(
                history = quiz.history,
                options = state.quizOptions,
                outcome = state.outcome,
                onBackToStart = viewModel::backToRoot,
                modifier = contentModifier,
            )
        }

        else -> RootScreen(state, viewModel, contentModifier)
    }
}

/**
 * The three tabs. Learn is first and the default: it is the one screen that tells a
 * newcomer what to do, where the practice grid assumes you already know the grammar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScreen(state: DrillUiState, viewModel: DrillViewModel, modifier: Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(title = { Text("Japanese Drills") })
                PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                    AppTab.entries.forEach { tab ->
                        Tab(
                            selected = state.tab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (state.tab == AppTab.Practice) {
                PracticeBar(state, onStart = viewModel::start, onReset = viewModel::resetDefaults)
            }
        },
    ) { padding ->
        val inner = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)

        when (state.tab) {
            AppTab.Learn -> LearnPathScreen(
                state = state,
                onLesson = viewModel::openLesson,
                onReview = viewModel::startReview,
                modifier = inner,
            )

            AppTab.Practice -> PracticeScreen(
                state = state,
                onFlag = viewModel::setFlag,
                onFocus = viewModel::setFocus,
                onNumQuestions = viewModel::setNumQuestions,
                modifier = inner,
            )

            AppTab.Settings -> AppSettingsScreen(
                state = state,
                onTheme = viewModel::setTheme,
                onResetProgress = viewModel::resetProgress,
                onExport = viewModel::exportProgress,
                onImport = viewModel::importProgress,
                onAbout = viewModel::showAbout,
                modifier = inner,
            )
        }
    }
}
