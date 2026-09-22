package com.japanesedrills.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.RichPart

/** How a [RichTable] shares out its width. */
enum class TableLayout {
    /** Every column the same width across the whole table: the kana grid. */
    Grid,

    /**
     * The first column as wide as its widest cell, then every other column one shared width
     * with the same gap between each, so a table of endings reads in even steps however
     * short one column's cells are next to another's.
     */
    Columns,
}

/**
 * A small table of Japanese cells: the kana grid, the row shifts and the fusions in the
 * Conjugation Intro, and a form's fusions in the grammar reference.
 *
 * Drawn between hairlines rather than on a panel, so a table stays lighter than the
 * examples around it, the way every worked change in the app is drawn ([StepBlock]). Cells
 * go through [RichText], so a cell written in furigana notation carries its reading. A
 * [TableLayout.Grid] caps a readable table at about nine columns on a phone, the reason the
 * kana grid has no row-label column.
 */
@Composable
fun RichTable(
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    header: List<String> = emptyList(),
    /** Cells are worked examples written with marks; see [RichPart.marked]. */
    marked: Boolean = false,
    layout: TableLayout = TableLayout.Grid,
    /** Right-aligns the first column of a [TableLayout.Columns] table against the next. */
    firstColumnEnd: Boolean = false,
    /** False under a table directly above, so the two read as one rather than doubling the rule between them. */
    topRule: Boolean = true,
) {
    val style = MaterialTheme.typography.bodyMedium
    val divider = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.padding(vertical = 2.dp)) {
        if (topRule) HorizontalDivider(color = divider)
        when (layout) {
            TableLayout.Grid -> Column(
                Modifier.padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (header.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth()) {
                        for (cell in header) HeaderCell(cell, TextAlign.Center, Modifier.weight(1f))
                    }
                }
                for (row in rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (cell in row) Cell(cell, marked, style, TextAlign.Center, Modifier.weight(1f))
                    }
                }
            }

            TableLayout.Columns -> ColumnsLayout(
                header = header,
                rows = rows,
                firstColumnEnd = firstColumnEnd,
                cell = { text, align -> Cell(text, marked, style, align) },
                headerCell = { text, align -> HeaderCell(text, align) },
            )
        }
        HorizontalDivider(color = divider)
    }
}

@Composable
private fun HeaderCell(text: String, align: TextAlign, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier,
    )
}

@Composable
private fun Cell(text: String, marked: Boolean, style: TextStyle, align: TextAlign, modifier: Modifier = Modifier) {
    // Plain kana needs none of the reading machinery, and the kana grid is forty-five cells of it.
    if (marked || Furigana.hasReading(text)) {
        RichText(
            if (marked) RichPart.marked(text) else listOf(RichPart.Jp(text)),
            style = style,
            horizontalArrangement = when (align) {
                TextAlign.Center -> Arrangement.Center
                TextAlign.End -> Arrangement.End
                else -> Arrangement.Start
            },
            modifier = modifier,
        )
    } else {
        Text(text, style = style.copy(localeList = JapaneseLocale), textAlign = align, modifier = modifier)
    }
}

/** The [TableLayout.Columns] table: measured whole, so every column after the first shares one width. */
@Composable
private fun ColumnsLayout(
    header: List<String>,
    rows: List<List<String>>,
    firstColumnEnd: Boolean,
    cell: @Composable (String, TextAlign) -> Unit,
    headerCell: @Composable (String, TextAlign) -> Unit,
) {
    val columns = (rows.map { it.size } + header.size).max()
    val firstAlign = if (firstColumnEnd) TextAlign.End else TextAlign.Start
    val lines = (if (header.isEmpty()) emptyList() else listOf(header)) + rows
    Layout(
        modifier = Modifier.padding(vertical = 6.dp),
        content = {
            if (header.isNotEmpty()) {
                header.forEachIndexed { i, text -> headerCell(text, if (i == 0) firstAlign else TextAlign.Start) }
            }
            for (row in rows) row.forEachIndexed { i, text -> cell(text, if (i == 0) firstAlign else TextAlign.Start) }
        },
    ) { measurables, constraints ->
        val rowGap = RowGap.roundToPx()
        // Cells in reading order, grouped back into lines; a short line leaves its end empty.
        var next = 0
        val grid = lines.map { line -> line.indices.map { measurables[next++] } }

        // The later columns first, as wide as their widest cell: they are short endings. A
        // header wider than its cells widens the one gap every column shares instead of its
        // own column, or "て-form" over って pushed the past column further out than the gap
        // between the others. The first column takes what is left, so a long label wraps
        // rather than pushing them off.
        val body = if (header.isEmpty()) grid else grid.drop(1)
        val rest = body.flatMap { it.drop(1) }.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val headers = if (header.isEmpty()) 0 else grid.first().drop(1).maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val gap = maxOf(ColumnGap.roundToPx(), headers - rest + HeaderClearance.roundToPx())
        val restWidth = (columns - 1) * (rest + gap)
        val firstMax = grid.maxOf { it.first().maxIntrinsicWidth(Constraints.Infinity) }
        val first = minOf(firstMax, (constraints.maxWidth - restWidth).coerceAtLeast(0))

        val placed = grid.mapIndexed { r, line ->
            line.mapIndexed { i, m ->
                val isHeader = r == 0 && header.isNotEmpty()
                when {
                    i == 0 -> m.measure(Constraints.fixedWidth(first))
                    isHeader -> m.measure(Constraints(minWidth = rest, maxWidth = rest + gap))
                    else -> m.measure(Constraints.fixedWidth(rest))
                }
            }
        }
        val heights = placed.map { line -> line.maxOf { it.height } }
        val width = minOf(first + restWidth, constraints.maxWidth)
        layout(width, heights.sum() + rowGap * (heights.size - 1).coerceAtLeast(0)) {
            var y = 0
            placed.forEachIndexed { r, line ->
                var x = 0
                line.forEachIndexed { i, p ->
                    p.place(x, y)
                    x += (if (i == 0) first else rest) + gap
                }
                y += heights[r] + rowGap
            }
        }
    }
}

private val ColumnGap = 20.dp
private val RowGap = 2.dp

/** The least room between a header reaching past its column and the next header. */
private val HeaderClearance = 8.dp
