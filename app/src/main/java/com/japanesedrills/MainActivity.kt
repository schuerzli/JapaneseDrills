package com.japanesedrills

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import com.japanesedrills.ui.screens.AboutScreen
import com.japanesedrills.ui.screens.QuizScreen
import com.japanesedrills.ui.screens.ResultsScreen
import com.japanesedrills.ui.screens.SettingsScreen
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
            // Each screen's Scaffold paints the background, so no Surface is needed here.
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

    val quiz = state.quiz
    when {
        state.screen == Screen.Quiz && quiz != null -> {
            BackHandler(onBack = viewModel::backToStart)
            QuizScreen(
                quiz = quiz,
                options = state.quizOptions,
                onSubmit = viewModel::submit,
                onProceed = viewModel::proceed,
                onExplain = viewModel::explain,
                onQuit = viewModel::backToStart,
                modifier = contentModifier,
            )
        }

        state.screen == Screen.About -> {
            BackHandler(onBack = viewModel::backToStart)
            AboutScreen(onBack = viewModel::backToStart, modifier = contentModifier)
        }

        state.screen == Screen.Results && quiz != null -> {
            BackHandler(onBack = viewModel::backToStart)
            ResultsScreen(
                history = quiz.history,
                options = state.quizOptions,
                onBackToStart = viewModel::backToStart,
                modifier = contentModifier,
            )
        }

        else -> SettingsScreen(
            state = state,
            onFlag = viewModel::setFlag,
            onFocus = viewModel::setFocus,
            onNumQuestions = viewModel::setNumQuestions,
            onStart = viewModel::start,
            onReset = viewModel::resetDefaults,
            onAbout = viewModel::showAbout,
            onTheme = viewModel::setTheme,
            modifier = contentModifier,
        )
    }
}
