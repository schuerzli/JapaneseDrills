package com.japanesedrills.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.PracticePreset
import com.japanesedrills.quiz.Progress
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.Scheduler
import com.japanesedrills.quiz.TransformationBuilder
import com.japanesedrills.quiz.WordColumn
import com.japanesedrills.quiz.WordSets
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.components.SectionCard
import com.japanesedrills.ui.components.SettingRow
import com.japanesedrills.ui.components.verticalScrollWithScrollbar

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
    val canUsePractised = state.progress.skills.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScrollWithScrollbar()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Start from", "Sets everything below in one tap") {
            Column {
                PracticePreset.entries.forEachIndexed { i, preset ->
                    SettingRow(
                        preset.label,
                        first = i == 0,
                        // Nothing practised on the path yet would select no forms at all.
                        onClick = { if (preset != PracticePreset.Practised || canUsePractised) onPreset(preset) },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SectionCard("Quiz") {
            Column {
                ChoiceRow(
                    label = "Questions",
                    value = options.numQuestions,
                    choices = QuizOptions.QUESTION_COUNTS.map { it.toString() to it.toString() },
                    first = true,
                    onChoose = onNumQuestions,
                )
                ChoiceRow(
                    label = "Focus",
                    value = QuizOptions.FOCUS.firstOrNull { it.key == options.questionFocus }?.label
                        ?: options.questionFocus,
                    choices = QuizOptions.FOCUS.map { it.key to it.label },
                    first = false,
                    onChoose = onFocus,
                )
            }
        }

        SectionCard("Word sets", "Every set you switch on is added to the pool") {
            WordSetList(state, onWordSet, onAllWords, onNewSet, onEditSet)
        }

        SectionCard("What to practise", "A form of a word class; tap a name for its whole line") {
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

        SectionCard("Options") {
            Column {
                QuizOptions.GENERAL.forEachIndexed { i, item ->
                    SettingRow(
                        item.label,
                        supporting = item.note,
                        first = i == 0,
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
            add(
                if (pool.questions == 0) "No questions match these settings."
                else "Not enough questions; some will repeat."
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
    first: Boolean,
    onChoose: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingRow(label, first = first, onClick = { expanded = true }) {
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
 * What a session will ask, as a square per form and word class: the pairing the drill
 * itself is built on (`QuizEngine.skillOf`). Tapping a form or a class switches its whole
 * line; tapping a square switches that one pairing.
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
            "No words to practise. Switch on a word set below.",
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
                    val held = strengthOf(form.key, column, state.progress)
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = CellGap / 2)
                            .height(CellHeight)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                when {
                                    strength -> ink(held)
                                    asked -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                            .clickable(enabled = !strength) { onSquare(form.key, column, !asked) }
                            .semantics {
                                contentDescription = "${form.label}, ${Furigana.toKanji(column.label)}"
                                stateDescription = when {
                                    strength -> "${(held * 100).roundToInt()} percent"
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
 * How well a square is holding up, 0f..1f: the review strength of that form on that class,
 * averaged over the classes a column covers. Never asked counts as nothing, which is what
 * the palest square means.
 */
private fun strengthOf(form: String, column: WordColumn, progress: Progress): Float {
    val type = TransformationBuilder.typeOfForm(form)
    val states = column.groups.map { progress.skills[QuizEngine.skillKey(type, it)] }
    return states.sumOf { state -> state?.let(Scheduler::strength)?.toDouble() ?: 0.0 }.toFloat() / states.size
}

/**
 * A square's shade in the strength view: the card's own ink, from an empty square to full
 * text colour. Ink rather than a colour of its own, because every colour in the app already
 * says something — the accent says "this does something when you tap it".
 */
@Composable
private fun ink(strength: Float): Color =
    lerp(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.onSurface,
        strength.coerceIn(0f, 1f),
    )

/** The strength view's scale, for reading the squares. */
@Composable
private fun StrengthLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        val caption = MaterialTheme.typography.bodySmall
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text("never asked", style = caption, color = muted)
        for (step in 0..Scheduler.LADDER.size) {
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(width = 18.dp, height = 12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(ink(step.toFloat() / Scheduler.LADDER.size)),
            )
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
    Column {
        SetRow(
            label = "All words",
            count = state.words.size.takeIf { it > 0 },
            checked = options.allWords,
            dimmed = false,
            first = true,
        ) { onAllWords(!options.allWords) }
        for (set in WordSets.BUILT_IN) {
            SetRow(
                label = set.label,
                count = state.setSizes[set.id],
                checked = options.isSetOn(set.id),
                dimmed = options.allWords,
                first = false,
            ) { onWordSet(set.id, !options.isSetOn(set.id)) }
        }
        // The learner's own sets carry a pencil, which opens the editor; the built-in ones
        // have none, which is the whole of how a set says whether it can be changed.
        for (set in state.progress.sets.values) {
            SetRow(
                label = set.name,
                count = set.words.size,
                checked = options.isSetOn(set.id),
                dimmed = options.allWords,
                first = false,
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
    }
}

/** One set: its name, how many words it holds, and whether the pool draws on it. */
@Composable
private fun SetRow(
    label: String,
    count: Int?,
    checked: Boolean,
    dimmed: Boolean,
    first: Boolean,
    onEdit: (() -> Unit)? = null,
    onToggle: () -> Unit,
) {
    val faded = MaterialTheme.colorScheme.outline
    Column {
        if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(vertical = 10.dp),
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
                color = if (dimmed) faded else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                count?.toString().orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = if (dimmed) faded else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = if (dimmed) faded else MaterialTheme.colorScheme.primary,
                    uncheckedColor = if (dimmed) faded else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
