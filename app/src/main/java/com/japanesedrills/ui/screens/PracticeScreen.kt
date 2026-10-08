package com.japanesedrills.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.PracticePreset
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.Scheduler
import com.japanesedrills.quiz.TransformationBuilder
import com.japanesedrills.quiz.WordColumn
import com.japanesedrills.quiz.WordSets
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.components.Section
import com.japanesedrills.ui.components.SettingRow
import com.japanesedrills.ui.components.TextAction
import com.japanesedrills.ui.components.verticalScrollWithScrollbar
import com.japanesedrills.ui.theme.DrillTheme
import kotlin.math.roundToInt

/**
 * The free-practice tab: one-tap presets above the full option grid. Content only — the tab
 * scaffold in MainActivity owns the bars.
 */
@Composable
fun PracticeScreen(
    state: DrillUiState,
    onFlag: (String, Boolean) -> Unit,
    onWordSet: (String, Boolean) -> Unit,
    onAllWords: (Boolean) -> Unit,
    onNewSet: () -> Unit,
    onEditSet: (String) -> Unit,
    onForm: (String, Boolean) -> Unit,
    onColumn: (WordColumn, Boolean) -> Unit,
    onSquare: (String, WordColumn, Boolean) -> Unit,
    onFocus: (String) -> Unit,
    onNumQuestions: (String) -> Unit,
    onPreset: (PracticePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = state.options
    val canUsePractised = state.progress.lessons.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScrollWithScrollbar()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("Start from", "Sets everything below in one tap") {
            Column {
                PracticePreset.entries.forEach { preset ->
                    // Nothing practised on the path yet would select no forms at all.
                    val use = { if (preset != PracticePreset.Practised || canUsePractised) onPreset(preset) }
                    SettingRow(preset.label, onClick = use) {
                        // Not a chevron: a preset sets the options below rather than
                        // opening a page, and a chevron means "opens a page" everywhere else.
                        TextAction("Use", use)
                    }
                }
            }
        }

        Section("Quiz") {
            Column {
                ChoiceRow(
                    label = "Questions",
                    value = options.numQuestions,
                    choices = QuizOptions.QUESTION_COUNTS.map { it.toString() to it.toString() },
                    onChoose = onNumQuestions,
                )
                ChoiceRow(
                    label = "Focus",
                    value = QuizOptions.FOCUS.firstOrNull { it.key == options.questionFocus }?.label
                        ?: options.questionFocus,
                    choices = QuizOptions.FOCUS.map { it.key to it.label },
                    onChoose = onFocus,
                )
            }
        }

        Section("Word sets", "Every set you switch on is added to the pool") {
            WordSetList(state, onWordSet, onAllWords, onNewSet, onEditSet)
        }

        Section("What to practise", "A form of a word class; tap a name for its whole line") {
            // Two readings of one grid: what a session would ask, and how those pairings are
            // holding up. The second is the same shape, so the eye keeps its place.
            var strength by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (showing in listOf(false, true)) {
                    FilterChip(
                        selected = strength == showing,
                        onClick = { strength = showing },
                        label = { Text(if (showing) "Strength" else "Choose") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            PracticeGrid(state, strength, onForm, onColumn, onSquare)
        }

        Section("Options") {
            Column {
                QuizOptions.GENERAL.forEach { item ->
                    SettingRow(
                        item.label,
                        supporting = item.note,
                        onClick = { onFlag(item.key, !options.isOn(item.key)) },
                        role = Role.Switch,
                    ) {
                        Switch(checked = options.isOn(item.key), onCheckedChange = null)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The practice tab's bottom bar: pool counts, reset and start. */
@Composable
fun PracticeBar(state: DrillUiState, onStart: () -> Unit, onReset: () -> Unit) {
    val options = state.options
    val pool = state.pool
    val count = options.questionCount
    val problems = buildList {
        if (!options.hasWords) add("Switch on a word set, or all words.")
        if (!options.hasPoliteness) add("Select at least one of 'Plain' and 'Polite'.")
        if (count == null) {
            add("Enter a number of questions between 1 and ${QuizOptions.MAX_QUESTIONS}.")
        } else if (pool != null && pool.questions < count && options.hasWords) {
            // A set of the learner's own with nothing in it yet is the one empty pool the
            // grid is not to blame for, and the only one with an obvious next move.
            val empty = !options.allWords && options.sets.isNotEmpty() &&
                options.sets.all { state.progress.sets[it]?.words?.isEmpty() == true }
            add(
                when {
                    empty && options.sets.size == 1 -> "That set has no words in it yet. Open it and pick some."
                    empty -> "Those sets have no words in them yet."
                    pool.questions == 0 -> "No questions match these settings."
                    else -> "Not enough questions; some will repeat."
                }
            )
        }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (problem in problems) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(problem, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CountRow("Words", pool?.words)
                    CountRow("Questions", pool?.questions)
                }
                OutlinedButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Reset")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onStart, enabled = state.canStart) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Go")
                }
            }
        }
    }
}

/** A setting whose value is picked from a short list, shown in a menu under the row. */
@Composable
private fun ChoiceRow(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onChoose: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingRow(label, onClick = { expanded = true }) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((key, text) in choices) {
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onChoose(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * One line of the start bar's summary: a label with its count, or "…" while counting.
 *
 * Label and count share a baseline, and counts are right-aligned in tabular figures so the
 * two lines' digits stand in columns; left-aligned proportional figures of different
 * lengths looked ragged against each other.
 */
@Composable
private fun CountRow(label: String, value: Int?) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(76.dp)
                .alignByBaseline(),
        )
        Text(
            value?.toString() ?: "…",
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(min = 64.dp)
                .alignByBaseline(),
        )
    }
}

/**
 * What a session will ask, as a square per form and word class. Tapping a form or a class
 * switches its whole line; tapping a square switches that one pairing.
 *
 * Only what can exist is drawn. A class no chosen word belongs to has no column, and a form
 * a class does not have has no square — an adjective has no passive.
 */
@Composable
private fun PracticeGrid(
    state: DrillUiState,
    strength: Boolean,
    onForm: (String, Boolean) -> Unit,
    onColumn: (WordColumn, Boolean) -> Unit,
    onSquare: (String, WordColumn, Boolean) -> Unit,
) {
    val options = state.options
    val columns = QuizOptions.COLUMNS.filter { it.key in state.columns }
    if (columns.isEmpty()) {
        Text(
            "No words to practise. Switch on a word set above.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val has = { column: WordColumn, form: String -> form in state.columnForms[column.key].orEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
        if (strength) {
            Text(
                "How well each pairing is holding up in review",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.width(RowLabelWidth))
            for (column in columns) {
                val on = options.isColumnOn(column)
                // Through RichText, because the irregular column names its two verbs.
                RichText(
                    listOf(RichPart.Text(column.label)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onColumn(column, !on) }
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                )
            }
        }
        for (form in QuizOptions.FORMS) {
            if (columns.none { has(it, form.key) }) continue
            val on = options.isOn(form.key)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // The chips' labels name the ending as well, "Conditional (たら)", which
                    // is more than a row heading has room for.
                    form.label.substringBefore(" ("),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(RowLabelWidth)
                        .clickable { onForm(form.key, !on) }
                        .padding(vertical = 7.dp, horizontal = 2.dp),
                )
                for (column in columns) {
                    if (!has(column, form.key)) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val asked = options.asksSquare(form.key, column.key)
                    val held = strengthOf(form.key, column, state)
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = CellGap / 2)
                            .height(CellHeight)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                when {
                                    strength -> held?.let { DrillTheme.strengthColors.at(it) }
                                        ?: MaterialTheme.colorScheme.surfaceContainerHighest
                                    asked -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                            .clickable(enabled = !strength) { onSquare(form.key, column, !asked) }
                            .semantics {
                                contentDescription = "${form.label}, ${Furigana.toKanji(column.label)}"
                                stateDescription = when {
                                    strength -> held?.let { "${(it * 100).roundToInt()} percent" }
                                        ?: "Never asked"
                                    asked -> "Asked"
                                    else -> "Not asked"
                                }
                            },
                    )
                }
            }
        }
        if (strength) StrengthLegend()
    }
}

/**
 * How well a square is holding up, 0f..1f: the review strength of the lessons that teach
 * that form on that column's classes, averaged. Null when review has none of them yet,
 * which is not the same as holding up badly and is not drawn as if it were.
 */
private fun strengthOf(form: String, column: WordColumn, state: DrillUiState): Float? {
    val type = TransformationBuilder.typeOfForm(form)
    val states = state.path.map { it.lesson }
        .filter { lesson -> lesson.focus == type && lesson.conjugations.keys.any(column.groups::contains) }
        .mapNotNull { state.progress.lessons[it.id] }
    if (states.isEmpty()) return null
    return states.map(Scheduler::strength).average().toFloat()
}

/**
 * The strength view's scale, for reading the squares: the empty square first, then the
 * ramp the learn path and the results bar fill with. One meaning, one set of colours.
 */
@Composable
private fun StrengthLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        val caption = MaterialTheme.typography.bodySmall
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val swatch = Modifier
            .padding(horizontal = 2.dp)
            .size(width = 18.dp, height = 12.dp)
            .clip(MaterialTheme.shapes.extraSmall)
        Text("never asked", style = caption, color = muted)
        Box(swatch.background(MaterialTheme.colorScheme.surfaceContainerHighest))
        for (step in 0..Scheduler.LADDER.size) {
            Box(swatch.background(DrillTheme.strengthColors.at(step.toFloat() / Scheduler.LADDER.size)))
        }
        Text("solid", style = caption, color = muted)
    }
}

private val RowLabelWidth = 88.dp
private val CellHeight = 26.dp
private val CellGap = 3.dp

/**
 * Which words the pool draws on. The sets stack, so the line at the foot gives the pool
 * rather than the sum: a word in three sets is still one word.
 *
 * "All words" is a mode rather than a set. While it is on the others are dimmed but still
 * live, so a tap on one records a choice for when it goes off again rather than fighting it.
 */
@Composable
private fun WordSetList(
    state: DrillUiState,
    onWordSet: (String, Boolean) -> Unit,
    onAllWords: (Boolean) -> Unit,
    onNewSet: () -> Unit,
    onEditSet: (String) -> Unit,
) {
    val options = state.options
    // As in the editor: a key words.json no longer has is not a word the set can draw on.
    val known = remember(state.words) { state.words.mapTo(HashSet()) { it.key } }
    Column {
        SetRow(
            label = "All words",
            count = state.words.size.takeIf { it > 0 },
            checked = options.allWords,
            dimmed = false,
        ) { onAllWords(!options.allWords) }
        for (set in WordSets.BUILT_IN) {
            SetRow(
                label = set.label,
                count = state.setSizes[set.id],
                checked = options.isSetOn(set.id),
                dimmed = options.allWords,
            ) { onWordSet(set.id, !options.isSetOn(set.id)) }
        }
        // The learner's own sets carry a pencil, which opens the editor; the built-in ones
        // have none, which is the whole of how a set says whether it can be changed.
        for (set in state.progress.sets.values) {
            SetRow(
                label = set.name,
                count = set.words.count { it in known },
                checked = options.isSetOn(set.id),
                dimmed = options.allWords,
                onEdit = { onEditSet(set.id) },
            ) { onWordSet(set.id, !options.isSetOn(set.id)) }
        }
        OutlinedButton(onClick = onNewSet, modifier = Modifier.padding(top = 12.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("New set")
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "In the pool",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                state.pool?.let { "${it.words} words" } ?: "…",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
        }
        PoolGap(state.pool?.skipped.orEmpty())
    }
}

/**
 * Why the pool holds fewer words than the sets do. The sets say what is available and the
 * grid says what is asked about, and a word in a column with nothing switched on is in the
 * first and not the second — a gap with no explanation anywhere on the screen until here.
 */
@Composable
private fun PoolGap(skipped: Map<String, Int>) {
    if (skipped.isEmpty()) return
    val total = skipped.values.sum()
    // Largest first: the one column that explains most of the gap is the one to switch on.
    val lines = QuizOptions.COLUMNS.mapNotNull { column ->
        skipped[column.key]?.let { column to it }
    }.sortedByDescending { it.second }
    Column(
        Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            if (total == 1) "1 more word is in your sets, but nothing on the grid asks about it:"
            else "$total more words are in your sets, but nothing on the grid asks about them:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for ((column, count) in lines) {
            RichText(
                listOf(
                    RichPart.Text("\u2022  $count in "),
                    RichPart.Text(column.label),
                    RichPart.Text(" \u2014 nothing selected"),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One set: its name, how many words it holds, and whether the pool draws on it. */
@Composable
private fun SetRow(
    label: String,
    count: Int?,
    checked: Boolean,
    dimmed: Boolean,
    onEdit: (() -> Unit)? = null,
    onToggle: () -> Unit,
) {
    // Dimmed while "All words" is on: the name steps down to the secondary text colour, which
    // holds the text floor anywhere on the page, and the pencil to the outline colour.
    val faded = MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit $label",
                    tint = if (dimmed) faded else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            count?.toString().orEmpty(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        // A switch, like every other thing on this screen that is either on or off.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = !dimmed,
            modifier = Modifier.scale(0.8f),
        )
    }
}
