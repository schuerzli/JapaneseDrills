package com.japanesedrills.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.ThemeChoice
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.components.SectionCard

private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

/** Appearance, progress and attribution, reached from the cog in the top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    state: DrillUiState,
    onTheme: (ThemeChoice) -> Unit,
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
    val passed = state.progress.passed.size

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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Appearance") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeChoice.entries.forEachIndexed { i, choice ->
                    SegmentedButton(
                        selected = state.options.theme == choice,
                        onClick = { onTheme(choice) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeChoice.entries.size),
                    ) {
                        Text(choice.label)
                    }
                }
            }
        }

        SectionCard("Progress", "Lessons passed and everything scheduled for review") {
            Text(
                if (state.started) {
                    "$passed of ${state.path.size} lessons passed. Tracking " +
                        plural(state.progress.words.size, "word") + " and " +
                        plural(state.progress.skills.size, "skill") + "."
                } else {
                    "Nothing yet — the learn path has not been started."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.salvagedProgress) {
                Text(
                    "Saved progress could not be read and has been set aside rather than " +
                        "overwritten. Resetting below will discard it for good.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(
                onClick = { confirming = true },
                // Also enabled when a document was set aside: that is the only way to
                // clear it, and the notice above tells the user resetting will do so.
                enabled = !state.progress.isEmpty || state.salvagedProgress,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Reset progress")
            }
        }

        SectionCard("Backup", "Progress is plain text — copy it somewhere safe, paste it back later") {
            Text(
                "Copying puts the whole learn path on the clipboard. Paste it into a note, " +
                    "a message to yourself, anywhere that keeps text.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val text = onExport()
                        clipboard.setText(AnnotatedString(text))
                        notice = "Copied ${text.length} characters to the clipboard."
                    },
                    enabled = state.started,
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Copy")
                }
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
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Paste")
                }
            }
            if (notice != null) {
                Text(notice.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard("About") {
            Button(onClick = onAbout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Sources and attribution")
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
                    "This clears every passed lesson and the whole review schedule. " +
                        "It cannot be undone. Your practice settings are not affected."
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
