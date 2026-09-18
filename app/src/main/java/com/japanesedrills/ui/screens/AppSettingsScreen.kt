package com.japanesedrills.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.ThemeChoice
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.components.SectionCard

private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

/** Appearance, progress and attribution. Content only; the tab scaffold owns the bars. */
@Composable
fun AppSettingsScreen(
    state: DrillUiState,
    onTheme: (ThemeChoice) -> Unit,
    onResetProgress: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }
    val passed = state.progress.passed.size

    Column(
        modifier = modifier
            .fillMaxSize()
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
}
