package com.japanesedrills.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Prompts
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.LessonRecord
import com.japanesedrills.ui.HistoryEntry
import com.japanesedrills.ui.LessonOutcome
import com.japanesedrills.ui.components.AlignedChanges
import com.japanesedrills.ui.components.ChangeRow
import com.japanesedrills.ui.components.FuriganaAction
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.HeroBar
import com.japanesedrills.ui.components.SectionPiece
import com.japanesedrills.ui.components.SectionHeading
import com.japanesedrills.ui.components.PanelPadding
import com.japanesedrills.ui.components.Spotlight
import com.japanesedrills.ui.components.StrengthBar
import com.japanesedrills.ui.components.StepBlock
import com.japanesedrills.ui.components.heroBarColors
import com.japanesedrills.ui.components.verticalScrollbar
import com.japanesedrills.ui.theme.DrillTheme
import com.japanesedrills.ui.theme.heading
import com.japanesedrills.ui.theme.lead
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    history: List<HistoryEntry>,
    options: QuizOptions,
    /** Set when the session was a lesson, which can be gone through again from here. */
    outcome: LessonOutcome?,
    onBackToStart: () -> Unit,
    onRetry: () -> Unit,
    /** Opens [LessonOutcome.next]. */
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        topBar = {
            HeroBar {
                TopAppBar(
                    colors = heroBarColors(),
                    title = { Text("Results") },
                    navigationIcon = {
                        IconButton(onClick = onBackToStart) {
                            Icon(Icons.Default.Close, contentDescription = "Done")
                        }
                    },
                    actions = { FuriganaAction() },
                )
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // A lesson that has just become ready points on to the next one; otherwise
                    // another go is the likely move, since a lesson is never finished.
                    val next = outcome?.next?.takeIf { outcome.becameReady }
                    if (next != null) {
                        OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                            Text("Go again")
                        }
                        Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                            Text("Next lesson")
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                        }
                    } else if (outcome != null) {
                        OutlinedButton(onClick = onBackToStart, modifier = Modifier.weight(1f)) {
                            Text("Done")
                        }
                        Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Go again")
                        }
                    } else {
                        Button(onClick = onBackToStart, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Done")
                        }
                    }
                }
            }
        },
    ) { padding ->
        val list = rememberLazyListState()
        LazyColumn(
            state = list,
            modifier = Modifier.padding(padding).verticalScrollbar(list),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (outcome != null) item { Box(Modifier.padding(bottom = 12.dp)) { ReadinessCard(outcome) } }
            item { Box(Modifier.padding(bottom = 12.dp)) { ScoreCard(history) } }
            // One card of answers, a slice per answer: a long session is hundreds of them,
            // too many to compose as one item.
            if (history.isNotEmpty()) {
                item { SectionPiece(first = true, last = false) { SectionHeading("Your answers") } }
            }
            itemsIndexed(history) { index, entry ->
                SectionPiece(first = false, last = index == history.lastIndex) {
                    HistoryRow(index + 1, entry, options)
                }
            }
        }
    }
}

/**
 * Where the lesson stands after this session. Readiness is judged on the recent answers
 * across sessions, not on this one alone, so the card says which.
 */
@Composable
private fun ReadinessCard(outcome: LessonOutcome) {
    val answers = DrillTheme.answerColors
    val record = outcome.record
    val recent = minOf(record.answered, LessonRecord.WINDOW)
    val percent = (record.recentAccuracy * 100).roundToInt()
    val bar = (LessonRecord.READY_ACCURACY * 100).roundToInt()
    val surfaces = DrillTheme.surfaces
    Spotlight {
        Row(Modifier.padding(PanelPadding), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FuriganaText(
                when {
                    outcome.becameReady -> "Ready for the next lesson"
                    record.ready -> "${outcome.lesson.title} is ready"
                    else -> "${outcome.lesson.title}: not ready yet"
                },
                style = MaterialTheme.typography.lead,
                color = surfaces.onHero,
            )
            FuriganaText(
                when {
                    outcome.becameReady && outcome.next != null ->
                        "$percent% of your last $recent answers here were right. Next up: ${outcome.next.title}."
                    outcome.becameReady -> "$percent% of your last $recent answers here were right."
                    record.ready -> "Come back to it whenever you like; review keeps it fresh."
                    record.answered < LessonRecord.minAnswers(outcome.lesson.questions) ->
                        "Ready once $bar% of your recent answers are right, over at least " +
                            "${LessonRecord.minAnswers(outcome.lesson.questions)}. So far: $percent% of $recent."
                    else -> "$percent% of your last $recent answers were right; ready at $bar%."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = surfaces.onHeroVariant,
            )
        }
            // The tick the learn path marks a ready lesson with, so the two screens agree —
            // here cut from the panel's own cream, because everything on it is.
            if (record.ready) {
                Spacer(Modifier.width(14.dp))
                Surface(shape = CircleShape, color = surfaces.onHero, contentColor = surfaces.heroEnd) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Ready",
                        modifier = Modifier.padding(7.dp).size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreCard(history: List<HistoryEntry>) {
    val correct = history.count { it.correct }
    val missed = history.size - correct
    val share = if (history.isEmpty()) 0f else correct / history.size.toFloat()

    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (correct) {
                history.size -> "All ${history.size} correct"
                0 -> "None of ${history.size} correct"
                else -> "$correct of ${history.size} correct"
            },
            style = MaterialTheme.typography.heading,
            color = if (DrillTheme.accents.titles) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        // The same bar the learn path draws a lesson's strength with, filled by this
        // session's score, so a share of something reads the same way twice.
        StrengthBar(share, Modifier.fillMaxWidth())
        if (missed > 0) {
            Text(
                if (missed == 1) "1 to review below" else "$missed to review below",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(number: Int, entry: HistoryEntry, options: QuizOptions) {
    val question = entry.question
    val answerColors = DrillTheme.answerColors
    val given = listOf(RichPart.Jp(question.givenDisplay(options.kana)))
    Column {
        // Between answers rather than above the first, which sits under the card's heading.
        if (number > 1) {
            HorizontalDivider(Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        StepBlock(number = number, rule = Prompts.instruction(question.transformation.phrase)) {
            AlignedChanges(listOf(given)) {
                Column(
                    Modifier.semantics(mergeDescendants = true) {
                        stateDescription = if (entry.correct) "Correct" else "Incorrect"
                    },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ChangeRow(
                        given,
                        listOf(RichPart.Jp(entry.responseDisplay)),
                        toColor = if (entry.correct) answerColors.correct else MaterialTheme.colorScheme.error,
                    )
                    if (!entry.correct) {
                        for (answer in question.answersDisplay(options.kana)) {
                            ChangeRow(null, listOf(RichPart.Jp(answer)), toColor = answerColors.correct)
                        }
                    }
                }
            }
        }
    }
}
