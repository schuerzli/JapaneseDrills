package com.japanesedrills.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.japanesedrills.data.Word
import com.japanesedrills.quiz.Explanations
import com.japanesedrills.quiz.Grammar
import com.japanesedrills.quiz.GrammarNote
import com.japanesedrills.quiz.Prompts
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.SolutionStep
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.components.SectionCard

/** Every form the drill can ask about. Tapping one opens what it means and how it is built. */
@Composable
fun GrammarScreen(onForm: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Every form the drill asks about, what it is for, and how it is built.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(Grammar.NOTES, key = { it.key }) { note ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onForm(note.key) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(note.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            note.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One form in full: what it is for, then how each word class builds it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarDetailScreen(
    note: GrammarNote,
    examples: List<Word>,
    furiganaAlways: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(note.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back to the form list")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { GrammarUsage(note) }
            item { GrammarConstruction(note, examples, furiganaAlways) }
        }
    }
}

/** The prose half: what the form means and when it is reached for. */
@Composable
fun GrammarUsage(note: GrammarNote) {
    SectionCard("What it is for") {
        Text(note.summary, style = MaterialTheme.typography.bodyLarge)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (line in note.notes) {
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * The construction half, derived per word class from the same engine that explains a wrong
 * answer, so it cannot disagree with what the drill accepts. A class with no such form —
 * an adjective has no passive — simply does not appear.
 */
@Composable
fun GrammarConstruction(note: GrammarNote, examples: List<Word>, furiganaAlways: Boolean) {
    val target = Grammar.conjugationOf(note.key)
    if (target == null) {
        SectionCard("How it is built", "Nothing to build — this is the form words are listed in") {
            for (word in examples) {
                RichText(
                    listOf(RichPart.Jp(word.dictionary), RichPart.Text("  ${word.meaning}")),
                    style = MaterialTheme.typography.bodyLarge,
                    furiganaAlways = furiganaAlways,
                )
            }
        }
        return
    }

    val shown = examples.filter { word -> word.conjugations[target]?.forms?.isNotEmpty() == true }
    SectionCard("How it is built", "Starting from the dictionary form") {
        shown.forEachIndexed { index, word ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    QuizEngine.groupLabels[word.group] ?: word.group,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                val solution = Explanations.solution(word, target)
                for (step in solution.steps) {
                    StepLine(step, furiganaAlways)
                }
            }
        }
    }
}

@Composable
private fun StepLine(step: SolutionStep, furiganaAlways: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        RichText(
            step.rule,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            furiganaAlways = furiganaAlways,
        )
        // The arrow is a character in the same run of text, the way the quiz's solution
        // renders it, so it shares the baseline. An icon beside the text cannot: furigana
        // makes the Japanese taller at the top, and centring floats the arrow above the words.
        RichText(
            listOf(RichPart.Jp(step.from), RichPart.Text("  →  ")) + Prompts.wordList(step.to),
            style = MaterialTheme.typography.bodyLarge,
            furiganaAlways = furiganaAlways,
        )
    }
}
