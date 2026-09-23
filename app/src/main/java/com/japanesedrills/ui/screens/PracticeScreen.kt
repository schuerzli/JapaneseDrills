package com.japanesedrills.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.OptionItem
import com.japanesedrills.quiz.PracticePreset
import com.japanesedrills.quiz.Progress
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.Scheduler
import com.japanesedrills.quiz.TransformationBuilder
import com.japanesedrills.quiz.WordColumn
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.LocalFurigana
import com.japanesedrills.ui.components.SectionCard
import com.japanesedrills.ui.components.SwitchRow
import com.japanesedrills.ui.components.verticalScrollWithScrollbar

/**
 * The free-practice tab: one-tap presets above the full option grid. Content only — the tab
 * scaffold in MainActivity owns the bars.
 */
@Composable
fun PracticeScreen(
    state: DrillUiState,
    onFlag: (String, Boolean) -> Unit,
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
        SectionCard("Start from", "Sets the forms and words below in one tap") {
            PresetRow(canUsePractised, onPreset)
        }

        SectionCard("Quiz") {
            OutlinedTextField(
                value = options.numQuestions,
                onValueChange = onNumQuestions,
                label = { Text("Number of questions") },
                singleLine = true,
                isError = options.questionCount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            FocusDropdown(selected = options.questionFocus, onSelected = onFocus)
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

        SectionCard("Filters", "Only ask about words in the selected lists") {
            LevelChips(options, onFlag)
        }

        SectionCard("Options") {
            Column {
                QuizOptions.GENERAL.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(item.label, options.isOn(item.key)) { onFlag(item.key, it) }
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
        if (!options.hasPoliteness) add("Select at least one of 'Plain' and 'Polite'.")
        if (count == null) {
            add("Enter a number of questions between 1 and ${QuizOptions.MAX_QUESTIONS}.")
        } else if (pool != null && pool.questions < count) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(canUsePractised: Boolean, onPreset: (PracticePreset) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (preset in PracticePreset.entries) {
            SuggestionChip(
                onClick = { onPreset(preset) },
                label = { Text(preset.label) },
                // Nothing practised on the path yet would select no forms at all.
                enabled = preset != PracticePreset.Practised || canUsePractised,
            )
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
    val present = state.pool?.columns
    val columns = QuizOptions.COLUMNS.filter { present == null || it.key in present }
    if (columns.isEmpty()) {
        Text(
            "No words to practise. Switch on a list below.",
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
                Text(
                    column.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
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
                                contentDescription = "${form.label}, ${column.label}"
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

/** The word lists, which narrow the words the grid then asks about. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelChips(options: QuizOptions, onFlag: (String, Boolean) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (item in QuizOptions.LEVEL_FILTERS) {
            val selected = options.isOn(item.key)
            FilterChip(
                selected = selected,
                onClick = { onFlag(item.key, !selected) },
                label = { Text(item.label) },
                // The fill alone carries the state. A tick widened the chip on selection
                // and reflowed every chip after it, which read as the grid jumping.
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = QuizOptions.FOCUS.firstOrNull { it.key == selected }?.label ?: selected

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Question focus") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (item in QuizOptions.FOCUS) {
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        onSelected(item.key)
                        expanded = false
                    },
                )
            }
        }
    }
}
