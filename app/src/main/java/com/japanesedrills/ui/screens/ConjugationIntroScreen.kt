package com.japanesedrills.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.ConjugationIntro
import com.japanesedrills.quiz.ConjugationIntroBlock
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.ui.components.AlignedChanges
import com.japanesedrills.ui.components.ChangeRow
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.RichTable
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.components.SectionCardPiece
import com.japanesedrills.ui.components.SectionHeading
import com.japanesedrills.ui.components.Subheading
import com.japanesedrills.ui.components.TableLayout
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
                val pieces = piecesOf(section.blocks)
                item(key = section.title) { SectionCardPiece(first = true, last = false) { SectionHeading(section.title) } }
                itemsIndexed(pieces, key = { i, _ -> "${section.title}/$i" }) { i, piece ->
                    SectionCardPiece(first = false, last = i == pieces.lastIndex) {
                        if (piece.size == 1 && piece[0] !is ConjugationIntroBlock.Step) {
                            ConjugationIntroBlockView(piece[0], afterTable = pieces.getOrNull(i - 1)?.last() is ConjugationIntroBlock.Table)
                        } else {
                            Changes(piece.filterIsInstance<ConjugationIntroBlock.Step>())
                        }
                    }
                }
            }
        }
    }
}

/**
 * The blocks as list items: one each, except that a run of worked changes is one item, so
 * its arrows can line up. A run is a handful of short rows, far from the size of a section.
 */
private fun piecesOf(blocks: List<ConjugationIntroBlock>): List<List<ConjugationIntroBlock>> {
    val pieces = ArrayList<MutableList<ConjugationIntroBlock>>()
    for (block in blocks) {
        val last = pieces.lastOrNull()
        if (block is ConjugationIntroBlock.Step && last?.last() is ConjugationIntroBlock.Step) last.add(block)
        else pieces += mutableListOf(block)
    }
    return pieces
}

/** A run of worked changes, drawn the way every change in the app is ([ChangeRow]). */
@Composable
private fun Changes(steps: List<ConjugationIntroBlock.Step>) {
    val rows = steps.map { RichPart.marked(it.from) to RichPart.marked(it.to) }
    AlignedChanges(rows.map { it.first }) {
        // Named steps get the room of a step on a grammar card; bare rows sit as one table.
        Column(verticalArrangement = Arrangement.spacedBy(if (steps.any { it.note.isNotEmpty() }) 10.dp else 4.dp)) {
            steps.forEachIndexed { i, step ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (step.note.isNotEmpty()) {
                        Text(
                            step.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ChangeRow(rows[i].first, rows[i].second)
                }
            }
        }
    }
}

@Composable
private fun ConjugationIntroBlockView(block: ConjugationIntroBlock, afterTable: Boolean) {
    when (block) {
        is ConjugationIntroBlock.Line -> FuriganaText(block.text, style = MaterialTheme.typography.bodyMedium)

        // On the text's baseline, or a reading over the first line lifts the bullet above it.
        is ConjugationIntroBlock.Bullet -> Row {
            Text("•  ", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
            FuriganaText(block.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
        }

        is ConjugationIntroBlock.Sub -> Subheading(block.title, Modifier.padding(top = 4.dp))

        is ConjugationIntroBlock.Step -> Changes(listOf(block))

        is ConjugationIntroBlock.Table -> RichTable(
            rows = block.rows,
            header = block.header,
            marked = block.marked,
            layout = if (block.header.isEmpty() && !block.marked) TableLayout.Grid else TableLayout.Columns,
            firstColumnEnd = block.firstColumnEnd,
            topRule = !afterTable,
        )

        is ConjugationIntroBlock.Legend -> RichText(block.parts, style = MaterialTheme.typography.bodyMedium)
    }
}
