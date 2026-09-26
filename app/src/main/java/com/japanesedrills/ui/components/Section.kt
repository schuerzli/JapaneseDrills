package com.japanesedrills.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.japanesedrills.ui.theme.DrillTheme
import com.japanesedrills.ui.theme.heading

/*
 * A run of rows under a heading, flat on the page.
 *
 * It is the learn path's own device: a label with a rule running out from it, and
 * everything under it indented, the way a lesson sits under its chapter. There is no card,
 * no fill and no line between the rows — they are like things in one group, and the
 * heading's rule already says where the group starts. A filled box on any screen means the
 * header, the spotlight, the well or a verdict, and a run of settings is none of those.
 */

/** A titled run of rows. */
@Composable
fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeading(title, subtitle)
        Column(Modifier.padding(start = SectionIndent)) { content() }
    }
}

/** A [Section]'s heading, for a section assembled from list items instead. */
@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FuriganaText(
                title,
                style = MaterialTheme.typography.heading,
                color = if (DrillTheme.accents.titles) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.width(12.dp))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One item of a [Section] that is a list rather than a column: the heading is the [first]
 * piece and is not indented, everything after it is.
 */
@Composable
fun SectionPiece(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.padding(
            start = if (first) 0.dp else SectionIndent,
            bottom = if (last) SectionSpacing else 0.dp,
        ),
    ) { content() }
}

/** How far a section's contents sit in from its heading. */
val SectionIndent = 14.dp

/** Between one section and the next. */
val SectionSpacing = 18.dp

/** Inside the spotlight and the panels: they are the only things with an inside. */
val PanelPadding = 20.dp

/** The gap either side of a screen's content. */
val ScreenGutter = 16.dp
