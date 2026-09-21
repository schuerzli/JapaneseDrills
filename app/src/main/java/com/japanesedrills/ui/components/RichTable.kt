package com.japanesedrills.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.RichPart

/**
 * A small table of Japanese cells: the kana grid and the sound changes in the primer, and
 * the same sound changes in the grammar reference.
 *
 * Cells share the width evenly, which caps a readable table at about nine columns on a
 * phone — the reason the kana grid has no row-label column. Cells go through [RichText],
 * so a cell written in furigana notation carries its reading.
 */
@Composable
fun RichTable(
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    header: List<String> = emptyList(),
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (header.isNotEmpty()) {
                Row(Modifier.fillMaxWidth()) {
                    for (cell in header) {
                        Text(
                            cell,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            for (row in rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (cell in row) {
                        RichText(
                            listOf(RichPart.Jp(cell)),
                            style = MaterialTheme.typography.bodyMedium,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
