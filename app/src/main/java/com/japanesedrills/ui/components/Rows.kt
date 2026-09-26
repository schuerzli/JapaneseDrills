package com.japanesedrills.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/*
 * The rows a section is listed from: a setting and its value, a count, or a control that
 * needs the whole width. One shape to read down a section, whether the value is a palette,
 * a choice of three, a switch or a number.
 *
 * No line between them: the entries of one section are like things, and the heading's own
 * rule already says where the section starts (ui/components/Section.kt).
 */

/** One setting: what it is and what it is set to. */
@Composable
fun SettingRow(
    label: String,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    value: @Composable RowScope.() -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            value()
        }
    }
}

/** A setting whose control needs the whole width: the label, then the control under it. */
@Composable
fun StackedRow(label: String, control: @Composable () -> Unit) {
    Column {
        Column(Modifier.padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            control()
        }
    }
}

/** A count, in tabular figures so the numbers in a section stand in one column. */
@Composable
fun StatRow(label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
        }
    }
}
