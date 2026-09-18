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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.japanesedrills.ui.components.JapaneseLocale
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.theme.DrillTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    history: List<HistoryEntry>,
    options: QuizOptions,
    onBackToStart: () -> Unit,
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
                Button(
                    onClick = onBackToStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Back to Start")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScoreCard(history) }
            itemsIndexed(history) { index, entry ->
                HistoryRow(index + 1, entry, options)
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
                Text(
                    "${history.size - correct} to review below",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                )
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
