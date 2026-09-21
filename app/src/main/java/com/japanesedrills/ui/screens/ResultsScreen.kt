package com.japanesedrills.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Prompts
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.StepRecord
import com.japanesedrills.ui.HistoryEntry
import com.japanesedrills.ui.StepOutcome
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.theme.DrillTheme
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
                        Icon(Icons.Default.Close, contentDescription = "Back to start")
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
                            Text("Back to Start")
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
                            Text("Back to Start")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (outcome != null) item { ReadinessCard(outcome) }
            item { ScoreCard(history) }
            itemsIndexed(history) { index, entry ->
                HistoryRow(index + 1, entry, options)
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
        colors = CardDefaults.cardColors(
            containerColor = if (outcome.becameReady) answers.correctContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (outcome.becameReady) answers.onCorrectContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FuriganaText(
                when {
                    outcome.becameReady -> "Ready for the next step"
                    record.ready -> "${outcome.step.title} is ready"
                    else -> "${outcome.step.title}: not ready yet"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            FuriganaText(
                when {
                    outcome.becameReady && outcome.next != null ->
                        "$percent% of your last $recent answers here were right. Next up: ${outcome.next.title}."
                    outcome.becameReady -> "$percent% of your last $recent answers here were right."
                    record.ready -> "Come back to it whenever you like; review keeps it fresh."
                    record.answered < StepRecord.READY_MIN_ANSWERS ->
                        "Ready once $bar% of your recent answers are right, over at least " +
                            "${StepRecord.READY_MIN_ANSWERS}. So far: $percent% of $recent."
                    else -> "$percent% of your last $recent answers were right; ready at $bar%."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ScoreCard(history: List<HistoryEntry>) {
    val correct = history.count { it.correct }
    val title = when (correct) {
        history.size -> "All correct!"
        0 -> "All incorrect!"
        else -> "$correct of ${history.size} correct"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (history.isEmpty()) 0f else correct / history.size.toFloat() },
                    modifier = Modifier.size(88.dp),
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    "${if (history.isEmpty()) 0 else correct * 100 / history.size}%",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.width(24.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                val missed = history.size - correct
                if (missed > 0) {
                    Text(
                        "$missed to review below",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(number: Int, entry: HistoryEntry, options: QuizOptions) {
    val question = entry.question
    val answerColors = DrillTheme.answerColors
    val scheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp)) {
            Surface(
                shape = CircleShape,
                color = if (entry.correct) answerColors.correctContainer else scheme.errorContainer,
                contentColor = if (entry.correct) answerColors.onCorrectContainer else scheme.onErrorContainer,
            ) {
                Icon(
                    if (entry.correct) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (entry.correct) "Correct" else "Incorrect",
                    modifier = Modifier
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RichText(
                    listOf(RichPart.Text("$number. ")) +
                        Prompts.question(question.transformation.phrase, question.givenDisplay(options.kana)),
                    style = MaterialTheme.typography.bodyLarge,
                    emphasisColor = scheme.primary,
                )
                RichText(
                    listOf(RichPart.Text("Your answer: "), RichPart.Jp(entry.responseDisplay)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (entry.correct) answerColors.correct else scheme.error,
                )
                if (!entry.correct) {
                    RichText(
                        listOf(RichPart.Text("Correct: ")) + Prompts.wordList(question.answersDisplay(options.kana)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = answerColors.correct,
                    )
                }
            }
        }
    }
}
