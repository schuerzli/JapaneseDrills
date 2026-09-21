package com.japanesedrills

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.japanesedrills.quiz.OptionsStore
import com.japanesedrills.quiz.ThemeChoice
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.DrillViewModel
import com.japanesedrills.ui.Screen
import com.japanesedrills.ui.components.LocalFurigana
import com.japanesedrills.ui.Tab as AppTab
import com.japanesedrills.ui.screens.AboutScreen
import com.japanesedrills.ui.screens.AppSettingsScreen
import com.japanesedrills.ui.screens.GrammarDetailScreen
import com.japanesedrills.ui.screens.GrammarScreen
import com.japanesedrills.ui.screens.LearnPathScreen
import com.japanesedrills.ui.screens.PracticeBar
import com.japanesedrills.ui.screens.PrimerScreen
import com.japanesedrills.ui.screens.PracticeScreen
import com.japanesedrills.ui.screens.QuizScreen
import com.japanesedrills.ui.screens.ResultsScreen
import com.japanesedrills.ui.screens.StepIntroScreen
import com.japanesedrills.ui.theme.JapaneseDrillsTheme

class MainActivity : ComponentActivity() {

    /**
     * The window background behind the app and the tint of the system bar icons are
     * resources, so the framework picks them by the device's night setting rather than by
     * the app's own. On Light or Dark those disagree, and it shows as the wrong scheme
     * behind the app while it starts, so the stored choice goes into this activity's
     * configuration before any resource is resolved.
     */
    override fun attachBaseContext(newBase: Context) {
        val night = nightUiMode(newBase)
        if (night == null) {
            super.attachBaseContext(newBase)
        } else {
            val config = Configuration(newBase.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            super.attachBaseContext(newBase.createConfigurationContext(config))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        matchSystemNightMode()
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
            // The icons' tint was decided in onCreate from the choice stored then, so a
            // switch made in Settings would leave dark icons on a dark bar until a restart.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            JapaneseDrillsTheme(darkTheme = dark, palette = state.options.palette) {
                // One value for every screen, so furigana can never be on in one place and off in another.
                CompositionLocalProvider(LocalFurigana provides state.options.furigana) { DrillApp(state, viewModel) }
            }
        }
    }

    /** The stored choice as a [Configuration] night bit, or null to follow the device. */
    private fun nightUiMode(context: Context): Int? = when (OptionsStore(context).load().theme) {
        ThemeChoice.System -> null
        ThemeChoice.Light -> Configuration.UI_MODE_NIGHT_NO
        ThemeChoice.Dark -> Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * The launcher draws the splash before this process exists, from the night mode the
     * system holds for the app, so the app cannot colour it directly — it can only keep
     * the system's copy of the choice in step. The system remembers, so the splash is
     * right from the next launch on. Before API 31 there is nothing to tell it, and the
     * splash follows the device.
     */
    private fun matchSystemNightMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mode = when (nightUiMode(this)) {
            Configuration.UI_MODE_NIGHT_NO -> UiModeManager.MODE_NIGHT_NO
            Configuration.UI_MODE_NIGHT_YES -> UiModeManager.MODE_NIGHT_YES
            else -> UiModeManager.MODE_NIGHT_AUTO
        }
        getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)
    }

    private companion object {
        val TRANSPARENT = Color.Transparent.toArgb()
        // enableEdgeToEdge's own defaults, which it keeps private.
        val LIGHT_SCRIM = Color(0xE6FFFFFF).toArgb()
        val DARK_SCRIM = Color(0x801B1B1B).toArgb()
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
    val step = state.step
    val note = state.grammarNote
    when {
        state.screen == Screen.Quiz && quiz != null -> {
            BackHandler(onBack = viewModel::backToRoot)
            QuizScreen(
                quiz = quiz,
                options = state.quizOptions,
                onSubmit = viewModel::submit,
                onProceed = viewModel::proceed,
                onExplain = viewModel::explain,
                onToggleFurigana = viewModel::toggleFurigana,
                onQuit = viewModel::backToRoot,
                modifier = contentModifier,
            )
        }

        state.screen == Screen.StepIntro && step != null -> {
            BackHandler(onBack = viewModel::backToRoot)
            StepIntroScreen(
                step = step,
                words = state.introWords,
                forms = state.introForms,
                classes = state.introClasses,
                examples = state.grammarExamples,
                options = state.options,
                onStart = { viewModel.startStep(step) },
                onPrimer = viewModel::showPrimer,
                onQuit = viewModel::backToRoot,
                modifier = contentModifier,
            )
        }

        state.screen == Screen.GrammarDetail && note != null -> {
            BackHandler(onBack = viewModel::backToRoot)
            GrammarDetailScreen(
                note = note,
                examples = state.grammarExamples,
                onBack = viewModel::backToRoot,
                modifier = contentModifier,
            )
        }

        state.screen == Screen.Primer -> {
            BackHandler(onBack = viewModel::closePrimer)
            PrimerScreen(
                onBack = viewModel::closePrimer,
                modifier = contentModifier,
            )
        }

        state.screen == Screen.Settings -> {
            BackHandler(onBack = viewModel::backToRoot)
            AppSettingsScreen(
                state = state,
                onTheme = viewModel::setTheme,
                onPalette = viewModel::setPalette,
                onFurigana = viewModel::setFurigana,
                onResetProgress = viewModel::resetProgress,
                onExport = viewModel::exportProgress,
                onImport = viewModel::importProgress,
                onAbout = viewModel::showAbout,
                onBack = viewModel::backToRoot,
                modifier = contentModifier,
            )
        }

        state.screen == Screen.About -> {
            // Back to Settings rather than the path: About has no other way in.
            BackHandler(onBack = viewModel::showSettings)
            AboutScreen(onBack = viewModel::showSettings, modifier = contentModifier)
        }

        state.screen == Screen.Results && quiz != null -> {
            BackHandler(onBack = viewModel::backToRoot)
            ResultsScreen(
                history = quiz.history,
                options = state.quizOptions,
                outcome = state.outcome,
                onBackToStart = viewModel::backToRoot,
                onRetry = { state.outcome?.step?.let(viewModel::startStep) },
                onNext = { state.outcome?.next?.let(viewModel::openStep) },
                modifier = contentModifier,
            )
        }

        else -> RootScreen(state, viewModel, contentModifier)
    }
}

/** The three tabs, with settings on the bar beside them rather than among them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScreen(state: DrillUiState, viewModel: DrillViewModel, modifier: Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Japanese Drills") },
                    actions = {
                        IconButton(onClick = viewModel::showSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
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
                onStep = viewModel::openStep,
                onStepIntro = viewModel::showStepIntro,
                onReview = viewModel::startReview,
                onPrimer = viewModel::showPrimer,
                onToggleChapter = viewModel::setChapterOpen,
                modifier = inner,
            )

            AppTab.Grammar -> GrammarScreen(
                onPrimer = viewModel::showPrimer,
                onForm = viewModel::showGrammar,
                modifier = inner,
            )

            AppTab.Practice -> PracticeScreen(
                state = state,
                onFlag = viewModel::setFlag,
                onFocus = viewModel::setFocus,
                onNumQuestions = viewModel::setNumQuestions,
                onPreset = viewModel::applyPreset,
                modifier = inner,
            )

        }
    }
}
