package com.japanesedrills.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.japanesedrills.quiz.Explanations
import com.japanesedrills.quiz.FusionColumn
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.ui.theme.DrillTheme
import com.japanesedrills.ui.theme.subheading

/*
 * How the app draws a worked change, wherever it shows one: the Grammar tab, the quiz
 * explanation, the results and the Conjugation Intro. One set of pieces, so a change reads
 * the same on every screen: plain step numbers, the rule as quiet caption text, and
 * "from → to" in columns whose arrows line up.
 */

/** How far a step sits in from its heading: the width of the number column. */
val StepInset = 30.dp

/** A heading inside a card, such as a word class on a grammar card or "Solution". */
@Composable
fun Subheading(text: String, modifier: Modifier = Modifier) {
    FuriganaText(
        text,
        style = MaterialTheme.typography.subheading,
        color = if (DrillTheme.accents.headings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * One step: its [number], if the caller numbers it, then an optional [title], its [rule],
 * whatever [extra] adds, and its [changes]. The number column is there even when empty, so
 * every step sits inset under its heading.
 */
@Composable
fun StepBlock(
    number: Int?,
    rule: List<RichPart>,
    changes: List<Pair<List<RichPart>, List<RichPart>>> = emptyList(),
    title: String? = null,
    extra: @Composable () -> Unit = {},
) {
    Row {
        Text(
            number?.toString().orEmpty(),
            // The outline colour keeps the numbers quieter than the text they number; the
            // contrast check holds it to the text floor on the card.
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(StepInset).alignByBaseline(),
        )
        Column(Modifier.alignByBaseline(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            }
            if (rule.isNotEmpty()) {
                RichText(
                    // A label such as "Irregular" is the whole rule here, so it reads as one.
                    rule.map { if (it is RichPart.Tag) RichPart.Text(it.text) else it },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    emphasisColor = MaterialTheme.colorScheme.primary,
                )
            }
            extra()
            for ((from, to) in changes) ChangeRow(from, to)
        }
    }
}

/**
 * The godan fusions, with only the column [column] uses: the dictionary endings right-aligned
 * against what they fuse into. Shown wherever a step applies one, on a grammar card and
 * behind a tap in an explanation.
 */
@Composable
fun FusionTable(column: FusionColumn) {
    RichTable(
        rows = Explanations.GODAN_FUSIONS.map { fusion ->
            listOf(fusion.endings.joinToString(" · "), if (column == FusionColumn.TE_FORM) fusion.te else fusion.past)
        },
        layout = TableLayout.Columns,
        firstColumnEnd = true,
    )
}

/** How wide the "from" column of [ChangeRow]s is, so their arrows line up. */
private val LocalFromWidth = compositionLocalOf { Dp.Unspecified }

/**
 * Lines up the arrows of every [ChangeRow] in [content]: the "from" column is as wide as the
 * widest of [froms], but never more than half the width, so a long word wraps rather than
 * pushing its result off the card.
 */
@Composable
fun AlignedChanges(froms: List<List<RichPart>>, content: @Composable () -> Unit) {
    val style = MaterialTheme.typography.bodyLarge
    SubcomposeLayout { constraints ->
        val widest = subcompose("froms") { froms.forEach { RichText(it, style = style) } }
            .maxOfOrNull { it.measure(Constraints()).width } ?: 0
        val width = minOf(widest, constraints.maxWidth / 2).toDp()
        val body = subcompose("body") {
            CompositionLocalProvider(LocalFromWidth provides width, content = content)
        }.map { it.measure(constraints) }
        layout(constraints.maxWidth, body.sumOf { it.height }) {
            var y = 0
            for (placeable in body) {
                placeable.place(0, y)
                y += placeable.height
            }
        }
    }
}

/**
 * One change, "from → to", in columns. The arrow shares the words' baseline: furigana makes
 * the Japanese taller at the top, so centring would float it above them.
 *
 * A null [from] continues the row above: an answer listed under the one before it, in the
 * same column, with the arrow's room kept but nothing drawn in it.
 */
@Composable
fun ChangeRow(from: List<RichPart>?, to: List<RichPart>, toColor: Color = LocalContentColor.current) {
    val style = MaterialTheme.typography.bodyLarge
    // Emphasis marks what a change is about where there is no kana to mark, as in the quiz's goal.
    val emphasis = MaterialTheme.colorScheme.primary
    Row {
        val width = LocalFromWidth.current
        val fromColumn = if (width.isSpecified) Modifier.width(width) else Modifier
        if (from != null) {
            RichText(from, style = style, emphasisColor = emphasis, modifier = fromColumn.alignByBaseline())
        } else {
            Spacer(fromColumn)
        }
        Text(
            "→",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .alignByBaseline()
                .then(if (from == null) Modifier.drawWithContent {} else Modifier),
        )
        RichText(
            to,
            style = style,
            color = toColor,
            emphasisColor = emphasis,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
    }
}
