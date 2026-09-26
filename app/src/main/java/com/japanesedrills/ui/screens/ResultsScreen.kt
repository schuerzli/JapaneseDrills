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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Prompts
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.StepRecord
import com.japanesedrills.ui.HistoryEntry
import com.japanesedrills.ui.StepOutcome
import com.japanesedrills.ui.components.AlignedChanges
import com.japanesedrills.ui.components.ChangeRow
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.SectionCardPiece
import com.japanesedrills.ui.components.SectionHeading
import com.japanesedrills.ui.components.SectionPadding
import com.japanesedrills.ui.components.StepBlock
import com.japanesedrills.ui.components.verticalScrollbar
import com.japanesedrills.ui.theme.DrillTheme
import com.japanesedrills.ui.theme.heading
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    history: List<HistoryEntry>,
    options: QuizOptions,
    /** Set when the session was a step, which can be gone through again from here. */
    outcome: StepOutcome?,
    onBackToStart: () -> Unit,
    onRetry: () -> Unit,
    /** Opens [StepOutcome.next]. */
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Results") },
                navigationIcon = {
                    IconButton(onClick = onBackToStart) {
                        Icon(Icons.Default.Close, contentDescription = "Done")
                    }
                },
            )
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
                    // A step that has just become ready points on to the next one; otherwise
                    // another go is the likely move, since a step is never finished.
                    val next = outcome?.next?.takeIf { outcome.becameReady }
                    if (next != null) {
                        OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                            Text("Go again")
                        }
                        Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                            Text("Next step")
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
                item { SectionCardPiece(first = true, last = false) { SectionHeading("Your answers") } }
            }
            itemsIndexed(history) { index, entry ->
                SectionCardPiece(first = false, last = index == history.lastIndex) {
                    HistoryRow(index + 1, entry, options)
                }
            }
        }
    }
}

/**
 * Where the step stands after this session. Readiness is judged on the recent answers
 * across sessions, not on this one alone, so the card says which.
 */
@Composable
private fun ReadinessCard(outcome: StepOutcome) {
    val answers = DrillTheme.answerColors
    val record = outcome.record
    val recent = minOf(record.answered, StepRecord.WINDOW)
    val percent = (record.recentAccuracy * 100).roundToInt()
    val bar = (StepRecord.READY_ACCURACY * 100).roundToInt()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(SectionPadding), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FuriganaText(
                when {
                    outcome.becameReady -> "Ready for the next step"
                    record.ready -> "${outcome.step.title} is ready"
                    else -> "${outcome.step.title}: not ready yet"
                },
                style = MaterialTheme.typography.heading,
            )
            FuriganaText(
                when {
                    outcome.becameReady && outcome.next != null ->
                        "$percent% of your last $recent answers here were right. Next up: ${outcome.next.title}."
                    outcome.becameReady -> "$percent% of your last $recent answers here were right."
                    record.ready -> "Come back to it whenever you like; review keeps it fresh."
                    record.answered < StepRecord.minAnswers(outcome.step.questions) ->
                        "Ready once $bar% of your recent answers are right, over at least " +
                            "${StepRecord.minAnswers(outcome.step.questions)}. So far: $percent% of $recent."
                    else -> "$percent% of your last $recent answers were right; ready at $bar%."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
            // The tick the learn path marks a ready step with, so the two screens agree.
            if (record.ready) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Ready",
                    tint = answers.correct,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ScoreCard(history: List<HistoryEntry>) {
    val correct = history.count { it.correct }
    val missed = history.size - correct
    val share = if (history.isEmpty()) 0f else correct / history.size.toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(SectionPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            // The same bar the learn path draws a step's strength with, filled by this
            // session's score, so a share of something reads the same way twice.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (share > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(share)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(DrillTheme.strengthColors.at(share)),
                    )
                }
            }
            if (missed > 0) {
                Text(
                    if (missed == 1) "1 to review below" else "$missed to review below",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
