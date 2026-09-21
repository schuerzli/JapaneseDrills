package com.japanesedrills.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.japanesedrills.data.Word
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.GrammarExamples
import com.japanesedrills.quiz.GrammarNote
import com.japanesedrills.quiz.Step
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.ui.components.JapaneseLocale
import com.japanesedrills.ui.components.RichText

/**
 * What a step introduces, shown the first time it is opened.
 *
 * The drill grades production, so asking for the て form of a word the learner has never
 * seen would test two things at once and diagnose neither. Everything shown here is
 * already in words.json — this screen is presentation, not new content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepIntroScreen(
    step: Step,
    words: List<Word>,
    forms: List<GrammarNote>,
    classes: List<GrammarNote>,
    examples: GrammarExamples,
    options: QuizOptions,
    onStart: () -> Unit,
    onPrimer: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(step.title) },
                navigationIcon = {
                    IconButton(onClick = onQuit) {
                        Icon(Icons.Default.Close, contentDescription = "Back to the path")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("Start")
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The rule text below leans on the kana grid and the verb classes, which only
            // the primer explains; this is where not knowing them would first bite.
            if (forms.isNotEmpty()) {
                item(key = "primer") {
                    TextButton(onClick = onPrimer) {
                        Text("New to verb classes and the kana grid? Read how conjugation works")
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                }
            }
            // A new word class comes first: the forms and words after it are that class's.
            for (note in classes) {
                item(key = "class-${note.key}") { GrammarUsage(note, heading = note.title) }
            }
            // Grammar next: the words are practice material for whatever the form is.
            // Titled by name, so a note reads the same here as on the Grammar tab.
            for (note in forms) {
                item(key = "usage-${note.key}") { GrammarUsage(note, heading = note.title) }
                item(key = "build-${note.key}") {
                    GrammarConstruction(note, examples, furiganaAlways = true)
                }
            }
            if (words.isNotEmpty()) {
                item {
                    Text(
                        "New words in this step",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
            }
            items(words, key = { it.key }) { word -> WordCard(word, options) }
        }
    }
}

@Composable
private fun WordCard(word: Word, options: QuizOptions) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val dictionary = if (options.kana) Furigana.toKana(word.dictionary) else word.dictionary
            RichText(
                listOf(RichPart.Jp(dictionary)),
                style = MaterialTheme.typography.headlineSmall,
                // Always on here: the whole point of the card is that the word is new.
                furiganaAlways = true,
            )
            Text(word.meaning, style = MaterialTheme.typography.bodyLarge)
            Text(
                QuizEngine.groupLabels[word.group] ?: word.group,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (word.sentenceJp.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    word.sentenceJp,
                    style = MaterialTheme.typography.bodyMedium.copy(localeList = JapaneseLocale),
                )
                Text(
                    word.sentenceEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
