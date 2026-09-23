package com.japanesedrills.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Palette
import com.japanesedrills.quiz.ThemeChoice
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.components.SectionCard
import com.japanesedrills.ui.components.SettingRow
import com.japanesedrills.ui.components.StackedRow
import com.japanesedrills.ui.components.StatRow
import com.japanesedrills.ui.components.verticalScrollWithScrollbar
import com.japanesedrills.ui.theme.facesOf

/**
 * Appearance, progress and attribution, reached from the cog in the top bar.
 *
 * Every setting is a row with its value on the right, the way the practice tab lists its
 * word sets: one shape to read down, whether the value is a palette, a choice of three or a
 * switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    state: DrillUiState,
    onTheme: (ThemeChoice) -> Unit,
    onPalette: (Palette) -> Unit,
    onFurigana: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onExport: () -> String,
    onImport: (String) -> Boolean,
    onAbout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val ready = state.path.count { it.ready }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScrollWithScrollbar()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("Appearance") {
                Column {
                    PaletteRow(state.options.palette, onPalette)
                    // Three choices side by side need the width, so they sit under the label
                    // rather than squeezing it.
                    StackedRow("Theme") {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeChoice.entries.forEachIndexed { i, choice ->
                                SegmentedButton(
                                    selected = state.options.theme == choice,
                                    onClick = { onTheme(choice) },
                                    shape = SegmentedButtonDefaults.itemShape(i, ThemeChoice.entries.size),
                                    // No tick: it pushes the label aside on selection. The fill says it.
                                    icon = {},
                                ) {
                                    Text(choice.label)
                                }
                            }
                        }
                    }
                    SettingRow(
                        "Show furigana",
                        supporting = "Readings above every kanji. Tapping a question card switches this too.",
                        onClick = { onFurigana(!state.options.furigana) },
                        role = Role.Switch,
                    ) {
                        Switch(checked = state.options.furigana, onCheckedChange = null)
                    }
                }
            }

            SectionCard("Progress", "Everything the learn path has earned, and how to keep a copy") {
                Column {
                    StatRow("Steps ready", "$ready of ${state.path.size}", first = true)
                    StatRow("Words tracked", "${state.progress.words.size}")
                    StatRow("Skills scheduled", "${state.progress.skills.size}")
                }
                if (state.salvagedProgress) {
                    Text(
                        "Saved progress could not be read and has been set aside rather than " +
                            "overwritten. Resetting below will discard it for good.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Progress is plain text. Copying puts the whole learn path on the clipboard: " +
                        "paste it into a note, a message to yourself, anywhere that keeps text.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val text = onExport()
                            clipboard.setText(AnnotatedString(text))
                            notice = "Copied ${text.length} characters to the clipboard."
                        },
                        enabled = state.started,
                        modifier = Modifier.weight(1f),
                    ) { Text("Copy") }
                    OutlinedButton(
                        onClick = {
                            val pasted = clipboard.getText()?.text.orEmpty()
                            when {
                                pasted.isBlank() -> notice = "The clipboard is empty."
                                // Confirm first only when there is something to lose.
                                state.started -> pendingImport = pasted
                                else -> notice =
                                    if (onImport(pasted)) "Progress restored."
                                    else "That does not look like a progress backup."
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Paste") }
                    OutlinedButton(
                        onClick = { confirming = true },
                        // Also enabled when a document was set aside: that is the only way to
                        // clear it, and the notice above tells the user resetting will do so.
                        enabled = !state.progress.isEmpty || state.salvagedProgress,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                    ) { Text("Reset") }
                }
                if (notice != null) {
                    Text(notice.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionCard("About") {
                SettingRow("Sources and attribution", first = true, onClick = onAbout) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Progress is the one thing in this app that cannot be recreated from the assets, so
        // wiping it asks first and names what goes.
        if (confirming) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text("Reset progress?") },
                text = {
                    Text(
                        "This clears every step record, the whole review schedule and the word " +
                            "sets you have made. It cannot be undone. Your practice settings are " +
                            "not affected."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onResetProgress()
                            confirming = false
                        }
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirming = false }) { Text("Cancel") }
                },
            )
        }

        // Restoring replaces everything, so it asks in the one case where that costs something.
        val importing = pendingImport
        if (importing != null) {
            AlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { Text("Replace your progress?") },
                text = {
                    Text(
                        "The pasted backup will replace the learn path you have now. " +
                            "Copy your current progress first if you might want it back."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            notice = if (onImport(importing)) "Progress restored."
                            else "That does not look like a progress backup."
                            pendingImport = null
                        }
                    ) {
                        Text("Replace")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
                },
            )
        }
    }
}

/** The palettes by name, each with the faces it is set in, since those change too. */
@Composable
private fun PaletteRow(selected: Palette, onSelected: (Palette) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingRow(
            "Palette",
            supporting = facesOf(selected),
            first = true,
            onClick = { expanded = true },
        ) {
            Swatch()
            Spacer(Modifier.width(8.dp))
            Text(selected.label, style = MaterialTheme.typography.bodyLarge)
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (palette in Palette.entries) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(palette.label)
                            Text(
                                facesOf(palette),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelected(palette)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** The palette in miniature: its accent, which is the part that changes most between them. */
@Composable
private fun Swatch() {
    Box(
        Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}
