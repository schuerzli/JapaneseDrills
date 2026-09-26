package com.japanesedrills.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.japanesedrills.ui.theme.DrillTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.japanesedrills.ui.theme.heading

/** The titled card the settings, practice and about screens are built from. */
@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(SectionPadding), verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
            SectionHeading(title, subtitle)
            content()
        }
    }
}

/** The inside padding of a [SectionCard], and the space between the things in it. */
val SectionPadding = 20.dp
val SectionSpacing = 12.dp

/**
 * One slice of a [SectionCard] built from list items, for a card too long to compose in one
 * frame: rounded at the top for the heading, at the bottom for the last slice, and square
 * between, with the card's padding split so the slices join up.
 */
@Composable
fun SectionCardPiece(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    // The card's own shape, square where one slice meets the next.
    val card = MaterialTheme.shapes.extraLarge
    val shape = card.copy(
        topStart = if (first) card.topStart else ZeroCornerSize,
        topEnd = if (first) card.topEnd else ZeroCornerSize,
        bottomStart = if (last) card.bottomStart else ZeroCornerSize,
        bottomEnd = if (last) card.bottomEnd else ZeroCornerSize,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) SectionSpacing else 0.dp),
    ) {
        Box(
            Modifier.padding(
                start = SectionPadding,
                end = SectionPadding,
                top = if (first) SectionPadding else SectionSpacing / 2,
                bottom = if (last) SectionPadding else SectionSpacing / 2,
            ),
        ) { content() }
    }
}

/** A [SectionCard]'s title and subtitle, for a card assembled from list items instead. */
@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Column {
        FuriganaText(
            title,
            style = MaterialTheme.typography.heading,
            color = if (DrillTheme.accents.titles) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
