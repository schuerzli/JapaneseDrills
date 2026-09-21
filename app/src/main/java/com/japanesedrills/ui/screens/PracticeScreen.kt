package com.japanesedrills.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.OptionItem
import com.japanesedrills.quiz.PracticePreset
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.components.SectionCard

/**
 * The free-practice tab: one-tap presets above the full option grid. Content only — the tab
 * scaffold in MainActivity owns the bars.
 */
@Composable
fun PracticeScreen(
    state: DrillUiState,
    onFlag: (String, Boolean) -> Unit,
    onFocus: (String) -> Unit,
    onNumQuestions: (String) -> Unit,
    onPreset: (PracticePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = state.options
    val canUseLearned = state.progress.passed.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Start from", "Sets the forms and words below in one tap") {
            PresetRow(canUseLearned, onPreset)
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

        SectionCard("Forms", "Which conjugations the questions may use") {
            ChipGroup(QuizOptions.FORMS, options, onFlag)
        }

        SectionCard("Words") {
            ChipGroup(QuizOptions.REGULAR_VERBS, options, onFlag, "Regular verbs")
            ChipGroup(QuizOptions.IRREGULAR_VERBS, options, onFlag, "Irregular verbs")
            ChipGroup(QuizOptions.ADJECTIVES, options, onFlag, "Adjectives")
            ChipGroup(QuizOptions.IRREGULAR_ADJECTIVES, options, onFlag, "Irregular adjectives")
        }

        SectionCard("Filters", "Only ask about words in the selected lists") {
            ChipGroup(QuizOptions.LEVEL_FILTERS, options, onFlag)
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
private fun PresetRow(canUseLearned: Boolean, onPreset: (PracticePreset) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (preset in PracticePreset.entries) {
            SuggestionChip(
                onClick = { onPreset(preset) },
                label = { Text(preset.label) },
                // Nothing passed means nothing learned, which would select no forms at all.
                enabled = preset != PracticePreset.Learned || canUseLearned,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    items: List<OptionItem>,
    options: QuizOptions,
    onFlag: (String, Boolean) -> Unit,
    label: String? = null,
) {
    Column {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (item in items) {
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
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
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
