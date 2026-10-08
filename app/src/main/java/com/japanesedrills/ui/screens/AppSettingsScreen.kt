package com.japanesedrills.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Palette
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.ThemeChoice
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.components.HeroBar
import com.japanesedrills.ui.components.Section
import com.japanesedrills.ui.components.SettingRow
import com.japanesedrills.ui.components.StackedRow
import com.japanesedrills.ui.components.StatRow
import com.japanesedrills.ui.components.heroBarColors
import com.japanesedrills.ui.components.verticalScrollWithScrollbar
import com.japanesedrills.ui.theme.facesOf

/**
 * Appearance, review, progress and attribution, reached from the cog in the top bar. Every
 * setting is one of the rows every section is listed from (ui/components/Rows.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    state: DrillUiState,
    onTheme: (ThemeChoice) -> Unit,
    onPalette: (Palette) -> Unit,
    onReviewCap: (Int) -> Unit,
    onResetProgress: () -> Unit,
    onExport: () -> String,
    onImport: (String) -> Boolean,
    onAbout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The debug build's developer section; nothing in a release build. */
    developer: @Composable () -> Unit = {},
) {
    var confirming by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val ready = state.path.count { it.ready }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        topBar = {
            HeroBar {
                TopAppBar(
                    colors = heroBarColors(),
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
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
            Section("Appearance") {
                Column {
                    PaletteRow(state.options.app.palette, onPalette)
                    // Three choices side by side need the width, so they sit under the label
                    // rather than squeezing it.
                    StackedRow("Theme") {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeChoice.entries.forEachIndexed { i, choice ->
                                SegmentedButton(
                                    selected = state.options.app.theme == choice,
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
                }
            }

            Section("Review", "Each lesson due adds to a review, up to this many") {
                ReviewCapRow(state.options.app.reviewCap, onReviewCap)
            }

            Section("Progress", "Everything the learn path has earned, and how to keep a copy") {
                Column {
                    StatRow("Lessons ready", "$ready of ${state.path.size}")
                    StatRow("Words tracked", "${state.progress.words.size}")
                    StatRow("Lessons in review", "${state.progress.lessons.size}")
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
                        enabled = !state.progress.isEmpty,
                        modifier = Modifier.weight(1f),
                    ) { Text("Copy") }
                    OutlinedButton(
                        onClick = {
                            val pasted = clipboard.getText()?.text.orEmpty()
                            when {
                                pasted.isBlank() -> notice = "The clipboard is empty."
                                // Confirm first only when there is something to lose.
                                !state.progress.isEmpty -> pendingImport = pasted
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

            Section("About") {
                SettingRow("Sources and attribution", onClick = onAbout) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            developer()
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
                        "This clears every lesson record, the whole review schedule and the word " +
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

/**
 * The most questions a review may ask, typed in. A number in range is kept as it is typed, so
 * the Review row on the path follows at once; anything else — an empty field, a 0 — is
 * marked and not kept, and the field shows the kept number again when the screen comes back.
 */
@Composable
private fun ReviewCapRow(cap: Int, onReviewCap: (Int) -> Unit) {
    var text by remember(cap) { mutableStateOf(cap.toString()) }
    val valid = text.toIntOrNull()?.takeIf { it in 1..QuizOptions.MAX_QUESTIONS } != null
    val keyboard = LocalSoftwareKeyboardController.current
    SettingRow(
        "Maximum review questions",
        supporting = if (valid) null else "A number from 1 to ${QuizOptions.MAX_QUESTIONS}",
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                text = typed.filter(Char::isDigit).take(3)
                text.toIntOrNull()?.takeIf { it in 1..QuizOptions.MAX_QUESTIONS }?.let(onReviewCap)
            },
            singleLine = true,
            isError = !valid,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            // Only the keyboard goes: clearing focus handed it to the Back button, and a
            // hardware Enter then left Settings.
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier.width(96.dp),
        )
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
