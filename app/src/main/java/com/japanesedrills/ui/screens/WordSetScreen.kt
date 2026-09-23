package com.japanesedrills.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.japanesedrills.data.Word
import com.japanesedrills.quiz.CustomSet
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.RomajiConverter
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.components.verticalScrollbar

/**
 * One word set being put together: its name, and every word in the list with a tick for the
 * ones it holds.
 *
 * The search takes what the learner can type: the kanji spelling, the reading, romaji for
 * that reading, or the English meaning. Romaji goes through the same converter the quiz
 * answers use, so "kaeru" finds 帰[かえ]る the way typing an answer would.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSetScreen(
    set: CustomSet,
    words: List<Word>,
    onName: (String) -> Unit,
    onWord: (String, Boolean) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("${set.words.size} words") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Check, contentDescription = "Done with this set")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete this set")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = set.name,
                    onValueChange = onName,
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search words") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val matches = remember(query, words) { search(words, query) }
            val list = rememberLazyListState()
            LazyColumn(
                state = list,
                modifier = Modifier.verticalScrollbar(list),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            ) {
                items(matches, key = { it.key }) { word ->
                    WordRow(word, word.key in set.words) { onWord(word.key, it) }
                }
                if (matches.isEmpty()) {
                    item {
                        Text(
                            "No words match that.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${set.name}?") },
            text = { Text("The set goes; the words and your progress stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun WordRow(word: Word, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheck)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RichText(listOf(RichPart.Jp(word.dictionary)), style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(word.meaning.takeIf { it.isNotEmpty() }, QuizEngine.groupLabels[word.group])
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}

/** The words [query] finds: its kanji, its reading, romaji for that reading, or its meaning. */
private fun search(words: List<Word>, query: String): List<Word> {
    val text = query.trim()
    if (text.isEmpty()) return words
    val kana = RomajiConverter.finish(text.lowercase())
    return words.filter { word ->
        Furigana.toKanji(word.dictionary).contains(text) ||
            Furigana.toKana(word.dictionary).contains(kana) ||
            word.meaning.contains(text, ignoreCase = true)
    }
}
