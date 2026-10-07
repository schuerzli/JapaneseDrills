package com.japanesedrills.dev

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.LearnPath
import com.japanesedrills.quiz.ProgressCodec
import com.japanesedrills.quiz.ProgressStore
import com.japanesedrills.ui.DrillViewModel
import com.japanesedrills.ui.Screen
import com.japanesedrills.ui.components.Section
import com.japanesedrills.ui.components.SettingRow
import com.japanesedrills.ui.components.TextAction
import java.time.LocalDate

/**
 * Developer tools, in debug builds only: a clock that can be moved forward, progress states
 * to jump to ([Scenario]), and shortcuts through a session. Release builds get the no-op
 * twin of this object from `src/release`, so none of it reaches the phone.
 *
 * Two ways in: the Developer section at the foot of Settings, and launch extras for driving
 * the emulator from a shell, which apply before anything is loaded —
 *
 *     adb shell am start -S -n com.japanesedrills/.MainActivity --es dev.scenario due --ei dev.days 3
 *
 * `-S` restarts the app, so the extras are read on a fresh start rather than ignored by a
 * running one.
 */
object DevTools {

    private const val TAG = "DevTools"
    private const val PREFS = "dev"
    private const val DAYS = "days"
    private const val EXTRA_SCENARIO = "dev.scenario"
    private const val EXTRA_DAYS = "dev.days"

    /** An answer no question accepts: Japanese, so it is checked rather than shaken off. */
    private const val WRONG = "ん"

    /** Days added to the real date, so lessons come due without waiting for them. Kept across launches. */
    var dayOffset: Long = 0
        private set

    /** Today as the app sees it: the real date plus [dayOffset]. */
    private fun today(): Long = LocalDate.now().toEpochDay() + dayOffset

    /**
     * Reads the stored clock and applies launch extras. Called before the ViewModel exists,
     * so a scenario written here is what it loads. [fresh] is false for an activity being
     * recreated, which still carries the extras it was launched with: loading the scenario
     * again then would wipe everything done since.
     */
    fun onLaunch(context: Context, intent: Intent?, fresh: Boolean) {
        dayOffset = prefs(context).getLong(DAYS, 0)
        if (!fresh || intent == null) return
        if (intent.hasExtra(EXTRA_DAYS)) setDays(context, intent.getIntExtra(EXTRA_DAYS, 0).toLong())
        val key = intent.getStringExtra(EXTRA_SCENARIO) ?: return
        val scenario = Scenario.entries.firstOrNull { it.key == key }
        if (scenario == null) {
            Log.w(TAG, "No scenario '$key'; known: ${Scenario.entries.joinToString { it.key }}")
            return
        }
        ProgressStore(context).save(Scenarios.build(scenario, pathOf(context), today()))
        Log.i(TAG, "Loaded scenario '$key' at day offset $dayOffset")
    }

    /** The developer section of Settings: the clock and the scenarios. */
    @Composable
    fun SettingsSection(viewModel: DrillViewModel) {
        val context = LocalContext.current
        var days by remember { mutableLongStateOf(dayOffset) }
        fun move(to: Long) {
            setDays(context, to)
            days = to
            viewModel.refreshDay()
        }
        Section("Developer", "Debug builds only. Nothing here is in the release build") {
            Column {
                SettingRow("Today", supporting = if (days == 0L) "The real date" else "The real date + $days days") {
                    TextAction("+1 day") { move(days + 1) }
                    TextAction("+7") { move(days + 7) }
                    if (days != 0L) TextAction("Reset") { move(0) }
                }
                for (scenario in Scenario.entries) {
                    val load = {
                        val progress = Scenarios.build(scenario, pathOf(context), today())
                        viewModel.importProgress(ProgressCodec.encode(progress))
                        Unit
                    }
                    SettingRow(scenario.label, supporting = scenario.description, onClick = load) {
                        TextAction("Load", load)
                    }
                }
            }
        }
    }

    /** A menu in the quiz's top bar for getting through a session without typing it. */
    @Composable
    fun QuizActions(viewModel: DrillViewModel) {
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Developer") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((label, action) in listOf<Pair<String, () -> Unit>>(
                    "Answer right" to { answer(viewModel, right = true) },
                    "Answer wrong" to { answer(viewModel, right = false) },
                    "Finish, all right" to { finish(viewModel, right = true) },
                    "Finish, all wrong" to { finish(viewModel, right = false) },
                )) {
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            open = false
                            action()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
    }

    /** Answers the question on screen and moves on, as a learner would. */
    private fun answer(viewModel: DrillViewModel, right: Boolean) {
        val quiz = viewModel.state.value.quiz ?: return
        if (quiz.answer == null) {
            viewModel.submit(if (right) Furigana.toKanji(quiz.question.answers.first()) else WRONG)
        }
        // "Skip ahead when right" may already have moved on; only a verdict still on screen
        // is waiting for the tap.
        if (viewModel.state.value.quiz?.answer != null) viewModel.proceed()
    }

    /** Answers everything left, through to the results. */
    private fun finish(viewModel: DrillViewModel, right: Boolean) {
        // Bounded, so a session that somehow never ends cannot hang the app.
        repeat(1000) {
            if (viewModel.state.value.screen != Screen.Quiz) return
            answer(viewModel, right)
        }
    }

    private fun setDays(context: Context, days: Long) {
        dayOffset = days
        prefs(context).edit().putLong(DAYS, days).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pathOf(context: Context): LearnPath =
        LearnPath.parse(context.assets.open("lessons.json").bufferedReader().use { it.readText() })
}
