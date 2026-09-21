package com.japanesedrills.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.japanesedrills.ui.HistoryEntry
import com.japanesedrills.ui.LessonOutcome
import com.japanesedrills.ui.components.JapaneseLocale
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.theme.DrillTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    history: List<HistoryEntry>,
    options: QuizOptions,
    outcome: LessonOutcome?,
    onBackToStart: () -> Unit,
    /** Runs the same lesson again; only offered when it was not passed. */
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = outcome != null && !outcome.passed
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
                    // After a failed lesson the likely next move is another go, so that is the
                    // filled button; going back to the path to find the same row is the detour.
                    if (failed) {
                        OutlinedButton(onClick = onBackToStart, modifier = Modifier.weight(1f)) {
                            Text("Back to Start")
                        }
                        Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Try again")
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
            if (outcome != null) item { OutcomeCard(outcome) }
            item { ScoreCard(history) }
            itemsIndexed(history) { index, entry ->
                HistoryRow(index + 1, entry, options)
            }
        }
    }
}

/**
 * Whether the lesson was passed, and if not, exactly why. A run can clear 85% overall and
 * still fail on one form, which looks arbitrary unless the weak form is named.
 */
@Composable
private fun OutcomeCard(outcome: LessonOutcome) {
    val answers = DrillTheme.answerColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (outcome.passed) answers.correctContainer else MaterialTheme.colorScheme.errorContainer,
            contentColor = if (outcome.passed) answers.onCorrectContainer else MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (outcome.passed) "${outcome.lesson.title} passed" else "${outcome.lesson.title} not passed yet",
                style = MaterialTheme.typography.titleLarge,
            )
            if (!outcome.passed) {
                val needed = (outcome.lesson.passAccuracy * 100).toInt()
                Text(
                    if (outcome.weakForms.isEmpty()) {
                        "You need $needed% to pass. Try it again — the questions will be different."
                    } else {
                        "Still shaky on " +
                            outcome.weakForms.joinToString(", ") {
                                QuizOptions.typeLabel(it).replaceFirstChar(Char::lowercase)
                            } + ". " +
                            "Try it again — the questions will be different."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (outcome.unlocked.isNotEmpty()) {
                Text(
                    "Unlocked: " + outcome.unlocked.joinToString(", ") { it.title },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
                shape = MaterialTheme.shapes.extraLarge,
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
                    furiganaAlways = options.furiganaAlways,
                )
                Text(
                    "Your answer: ${entry.response}",
                    style = MaterialTheme.typography.bodyMedium.copy(localeList = JapaneseLocale),
                    color = if (entry.correct) answerColors.correct else scheme.error,
                )
                if (!entry.correct) {
                    RichText(
                        listOf(RichPart.Text("Correct: ")) + Prompts.wordList(question.answersDisplay(options.kana)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = answerColors.correct,
                        furiganaAlways = options.furiganaAlways,
                    )
                }
            }
        }
    }
}
