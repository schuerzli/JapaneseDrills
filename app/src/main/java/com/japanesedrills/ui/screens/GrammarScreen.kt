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
import com.japanesedrills.quiz.GrammarExamples
import com.japanesedrills.quiz.GrammarNote
import com.japanesedrills.quiz.Primer
import com.japanesedrills.quiz.Prompts
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.SolutionStep
import com.japanesedrills.ui.components.RichTable
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.components.SectionCard

/** Every form the drill can ask about. Tapping one opens what it means and how it is built. */
@Composable
fun GrammarScreen(onPrimer: () -> Unit, onForm: (String) -> Unit, modifier: Modifier = Modifier) {
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
        // First, and marked out from the list: the rest of this screen assumes you know
        // what a godan verb is, and this is where that is explained.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPrimer),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(Primer.TITLE, style = MaterialTheme.typography.titleMedium)
                        Text(Primer.SUMMARY, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
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
    examples: GrammarExamples,
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
fun GrammarConstruction(note: GrammarNote, examples: GrammarExamples, furiganaAlways: Boolean) {
    val words = Grammar.examplesFor(note.key).mapNotNull(examples::get)
    val target = Grammar.conjugationOf(note.key)
    if (target == null) {
        SectionCard("How it is built", "Nothing to build — this is the form words are listed in") {
            for (word in words) {
                RichText(
                    listOf(RichPart.Jp(word.dictionary), RichPart.Text("  ${word.meaning}")),
                    style = MaterialTheme.typography.bodyLarge,
                    furiganaAlways = furiganaAlways,
                )
            }
        }
        return
    }

    val derived = words
        .filter { word -> word.conjugations[target]?.forms?.isNotEmpty() == true }
        .map { word -> word to Explanations.solution(word, target).steps }

    // An irregular verb that inherits this form from its class is dropped: its entry would
    // repeat the rule above it and only change the word being named. 行く is irregular in
    // the forms built on its て-form and ordinary everywhere else, and the section is for
    // verbs that are irregular *here*.
    val shown = derived.filter { (word, _) ->
        word.group !in Grammar.IRREGULAR_GROUPS || examples.declaresOwnRule(word, target)
    }

    // A sound change is a closed list, not a rule, so godan gets the table itself instead
    // of one sentence per example word. The rule text would only repeat a row of it, so
    // the example shows the change happening and nothing else.
    val fusions = Explanations.godanFusions(target)

    SectionCard("How it is built", "Starting from the dictionary form") {
        // Grouped by heading rather than one heading per word: する, 来る and 行く are worth
        // meeting as "the irregulars" rather than as three unrelated classes.
        shown.groupBy { (word, _) -> headingFor(word) }.entries.forEachIndexed { index, (heading, group) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val table = fusions?.takeIf { group.any { (word, _) -> word.group == "godan" } }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                if (table != null) {
                    RichTable(
                        rows = table.map { fusion ->
                            listOf(fusion.endings.joinToString(" · "), fusion.te, fusion.past)
                        },
                        header = listOf("plain", "て-form", "past"),
                        furiganaAlways = furiganaAlways,
                    )
                }
                for ((_, steps) in group) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (step in steps) {
                            StepLine(step, furiganaAlways, showRule = table == null)
                        }
                    }
                }
            }
        }
    }
}

private fun headingFor(word: Word): String =
    if (word.group in Grammar.IRREGULAR_GROUPS) "irregular verbs"
    else QuizEngine.groupLabels[word.group] ?: word.group

@Composable
private fun StepLine(step: SolutionStep, furiganaAlways: Boolean, showRule: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showRule) {
            RichText(
                step.rule,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                furiganaAlways = furiganaAlways,
            )
        }
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
