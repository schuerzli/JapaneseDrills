package com.japanesedrills.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.ConjugationIntro
import com.japanesedrills.quiz.ConjugationIntroBlock
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.RichTable
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.components.SectionHeading
import com.japanesedrills.ui.components.SectionPadding
import com.japanesedrills.ui.components.SectionSpacing
import com.japanesedrills.ui.components.verticalScrollbar

/**
 * The Conjugation Intro: one page, read top to bottom. It is the only screen in the app
 * meant to be read rather than used, which is why it is a page of its own rather than a
 * long entry in the form list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConjugationIntroScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(ConjugationIntro.TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back to the form list")
                    }
                },
            )
        },
    ) { padding ->
        // Each block is its own list item, drawn so a section still reads as one card. As one
        // item per section, a section with two tables and forty readings was built in a
        // single frame as it scrolled in, and a fast fling stuttered on it.
        val list = rememberLazyListState()
        LazyColumn(
            state = list,
            modifier = Modifier.padding(padding).verticalScrollbar(list),
            contentPadding = PaddingValues(16.dp),
        ) {
            for (section in ConjugationIntro.SECTIONS) {
                val last = section.blocks.lastIndex
                item(key = section.title) { Piece(first = true, last = false) { SectionHeading(section.title) } }
                itemsIndexed(section.blocks, key = { i, _ -> "${section.title}/$i" }) { i, block ->
                    Piece(first = false, last = i == last) { ConjugationIntroBlockView(block) }
                }
            }
        }
    }
}

/**
 * One slice of a section card: rounded at the top for the heading, at the bottom for the
 * last block, and square between, with the card's padding split so the slices join up.
 */
@Composable
private fun Piece(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
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

@Composable
private fun ConjugationIntroBlockView(block: ConjugationIntroBlock) {
    when (block) {
        is ConjugationIntroBlock.Line -> FuriganaText(block.text, style = MaterialTheme.typography.bodyMedium)

        // On the text's baseline, or a reading over the first line lifts the bullet above it.
        is ConjugationIntroBlock.Bullet -> Row {
            Text("•  ", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
            FuriganaText(block.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
        }

        is ConjugationIntroBlock.Sub -> FuriganaText(
            block.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )

        is ConjugationIntroBlock.Step -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (block.note.isNotEmpty()) {
                Text(
                    block.note,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RichText(
                RichPart.marked(block.from) + RichPart.Text("  →  ") + RichPart.marked(block.to),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        is ConjugationIntroBlock.Table -> RichTable(
            rows = block.rows,
            header = block.header,
            marked = block.marked,
        )

        is ConjugationIntroBlock.Legend -> RichText(block.parts, style = MaterialTheme.typography.bodyMedium)
    }
}
